package ms.maomer.neobackup.backup;

import ms.maomer.neobackup.NeoBackupConfig;
import ms.maomer.neobackup.ServerBridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BackupManager {
    private static final Logger LOGGER = LogManager.getLogger("NeoBackup");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final ExecutorService BACKUP_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NeoBackup Compressor");
        thread.setDaemon(true);
        return thread;
    });

    private BackupManager() {
    }

    public static CompletableFuture<Void> performBackup(ServerBridge bridge, NeoBackupConfig config, boolean force) {
        LOGGER.info("[NeoBackup] Starting backup...");
        CompletableFuture<Void> result = new CompletableFuture<>();

        saveWorldAsync(bridge).thenRunAsync(() -> {
            try {
                Path worldPath = bridge.worldsRootPath();
                if (!Files.exists(worldPath)) {
                    LOGGER.error("[NeoBackup] World path does not exist: " + worldPath);
                    result.complete(null);
                    return;
                }

                Path backupDir = getBackupDirectory(config);
                Files.createDirectories(backupDir);

                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String backupFileName = "backup_" + timestamp;
                String compressionMethod = config.getCompressionMethod();

                createBackup(worldPath, backupDir, backupFileName, compressionMethod, config);
                LOGGER.info("[NeoBackup] Backup completed successfully!");

                cleanupOldBackups(backupDir, config);
                result.complete(null);

            } catch (IOException e) {
                LOGGER.error("[NeoBackup] Failed to create backup: " + e.getMessage());
                result.completeExceptionally(e);
            } catch (SecurityException e) {
                LOGGER.error("[NeoBackup] Security error during backup: " + e.getMessage());
                result.completeExceptionally(e);
            } catch (Exception e) {
                LOGGER.error("[NeoBackup] Unexpected error during backup: " + e.getMessage());
                result.completeExceptionally(e);
            } catch (Throwable t) {
                LOGGER.error("[NeoBackup] Fatal error during backup: " + t.getMessage(), t);
                result.completeExceptionally(t);
            }
        }, BACKUP_EXECUTOR);

        return result;
    }

    private static CompletableFuture<Void> saveWorldAsync(ServerBridge bridge) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        bridge.executeOnMainThread(() -> {
            try {
                LOGGER.info("[NeoBackup] Saving world...");
                bridge.saveAll();
                LOGGER.info("[NeoBackup] World saved.");
                future.complete(null);
            } catch (Exception e) {
                LOGGER.error("[NeoBackup] Failed to save world: " + e.getMessage());
                future.completeExceptionally(e);
            } catch (Throwable t) {
                LOGGER.error("[NeoBackup] Fatal error while saving world: " + t.getMessage(), t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private static void createBackup(Path worldPath, Path backupDir, String fileName, String method, NeoBackupConfig config) throws IOException {
        switch (method) {
            case "zstd" -> {
                Path targetFile = backupDir.resolve(fileName + ".tar.zst");
                CompressionUtil.compressWithZstd(worldPath, targetFile, config);
                LOGGER.info("[NeoBackup] Backup saved: " + targetFile);
            }
            case "zip" -> {
                Path targetFile = backupDir.resolve(fileName + ".zip");
                CompressionUtil.compressWithZip(worldPath, targetFile, config);
                LOGGER.info("[NeoBackup] Backup saved: " + targetFile);
            }
            default -> throw new IllegalArgumentException("Unknown compression method: " + method);
        }
    }

    private static Path getBackupDirectory(NeoBackupConfig config) {
        String backupPathStr = config.getBackupPath();
        Path backupPath = Paths.get(backupPathStr);
        if (!backupPath.isAbsolute()) {
            backupPath = Paths.get(".").resolve(backupPathStr);
        }
        return backupPath.normalize();
    }

    public static void cleanupOldBackups(NeoBackupConfig config) {
        try {
            Path backupDir = getBackupDirectory(config);
            if (Files.exists(backupDir)) {
                cleanupOldBackups(backupDir, config);
            }
        } catch (Exception e) {
            LOGGER.error("[NeoBackup] Failed to cleanup old backups: " + e.getMessage());
        }
    }

    private static void cleanupOldBackups(Path backupDir, NeoBackupConfig config) {
        int maxCount = config.getMaxBackupCount();
        int maxSizeGB = config.getMaxBackupSizeGB();

        if (maxCount <= 0 && maxSizeGB <= 0) {
            return;
        }

        try {
            List<Path> backupFiles = getBackupFiles(backupDir);
            backupFiles.sort(Comparator.comparingLong(path -> {
                try {
                    return Files.getLastModifiedTime(path).toMillis();
                } catch (IOException e) {
                    return 0L;
                }
            }));

            int deleteIndex = 0;

            if (maxSizeGB > 0) {
                long maxSizeBytes = (long) maxSizeGB * 1024 * 1024 * 1024;
                long totalSize = 0;
                for (Path file : backupFiles) {
                    totalSize += Files.size(file);
                }

                while (totalSize > maxSizeBytes && deleteIndex < backupFiles.size()) {
                    Path file = backupFiles.get(deleteIndex);
                    long fileSize = Files.size(file);
                    deleteBackup(file);
                    totalSize -= fileSize;
                    deleteIndex++;
                }
            }

            if (maxCount > 0) {
                int remaining = backupFiles.size() - deleteIndex;
                int excess = remaining - maxCount;
                for (int i = 0; i < excess && deleteIndex < backupFiles.size(); i++) {
                    deleteBackup(backupFiles.get(deleteIndex));
                    deleteIndex++;
                }
            }
        } catch (IOException e) {
            LOGGER.error("[NeoBackup] Failed to cleanup old backups: " + e.getMessage());
        }
    }

    private static List<Path> getBackupFiles(Path backupDir) throws IOException {
        List<Path> backupFiles = new ArrayList<>();
        if (!Files.exists(backupDir)) {
            return backupFiles;
        }

        try (Stream<Path> stream = Files.list(backupDir)) {
            stream.filter(path -> {
                String name = path.getFileName().toString();
                return name.endsWith(".tar.zst") || name.endsWith(".zip");
            }).forEach(backupFiles::add);
        }
        return backupFiles;
    }

    private static void deleteBackup(Path file) {
        try {
            Files.deleteIfExists(file);
            LOGGER.info("[NeoBackup] Deleted old backup: " + file.getFileName());
        } catch (IOException e) {
            LOGGER.error("[NeoBackup] Failed to delete backup " + file.getFileName() + ": " + e.getMessage());
        }
    }

    public static void shutdownExecutor() {
        BACKUP_EXECUTOR.shutdown();
        try {
            if (!BACKUP_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                BACKUP_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            BACKUP_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

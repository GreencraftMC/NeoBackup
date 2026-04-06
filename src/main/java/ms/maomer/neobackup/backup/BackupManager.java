package ms.maomer.neobackup.backup;

import ms.maomer.neobackup.NeoBackupConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import java.util.stream.Stream;

public class BackupManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final ExecutorService BACKUP_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NeoBackup Compressor");
        thread.setDaemon(true);
        return thread;
    });

    private BackupManager() {
    }

    public static void performBackup(MinecraftServer server, boolean force) {
        LOGGER.info("[NeoBackup] Starting backup...");

        saveWorldAsync(server).thenRunAsync(() -> {
            try {
                Path worldPath = getWorldPath(server);
                if (!Files.exists(worldPath)) {
                    LOGGER.error("[NeoBackup] World path does not exist: {}", worldPath);
                    return;
                }

                Path backupDir = getBackupDirectory();
                Files.createDirectories(backupDir);

                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String backupFileName = "backup_" + timestamp;
                String compressionMethod = NeoBackupConfig.getCompressionMethod();

                createBackup(worldPath, backupDir, backupFileName, compressionMethod);
                LOGGER.info("[NeoBackup] Backup completed successfully!");

                cleanupOldBackups(backupDir);

            } catch (IOException e) {
                LOGGER.error("[NeoBackup] Failed to create backup: {}", e.getMessage(), e);
            } catch (SecurityException e) {
                LOGGER.error("[NeoBackup] Security error during backup: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.error("[NeoBackup] Unexpected error during backup: {}", e.getMessage(), e);
            }
        }, BACKUP_EXECUTOR);
    }

    private static CompletableFuture<Void> saveWorldAsync(MinecraftServer server) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                LOGGER.info("[NeoBackup] Saving world...");
                server.saveEverything(true, true, true);
                LOGGER.info("[NeoBackup] World saved.");
                future.complete(null);
            } catch (Exception e) {
                LOGGER.error("[NeoBackup] Failed to save world: {}", e.getMessage(), e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static void createBackup(Path worldPath, Path backupDir, String fileName, String method) throws IOException {
        switch (method) {
            case "zstd" -> {
                Path targetFile = backupDir.resolve(fileName + ".tar.zst");
                CompressionUtil.compressWithZstd(worldPath, targetFile);
                LOGGER.info("[NeoBackup] Backup saved: {}", targetFile);
            }
            case "zip" -> {
                Path targetFile = backupDir.resolve(fileName + ".zip");
                CompressionUtil.compressWithZip(worldPath, targetFile);
                LOGGER.info("[NeoBackup] Backup saved: {}", targetFile);
            }
            default -> throw new IllegalArgumentException("Unknown compression method: " + method);
        }
    }

    private static Path getWorldPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    private static Path getBackupDirectory() {
        String backupPathStr = NeoBackupConfig.COMMON.backupPath.get();
        Path backupPath = Paths.get(backupPathStr);
        if (!backupPath.isAbsolute()) {
            backupPath = Paths.get(".").resolve(backupPathStr);
        }
        return backupPath.normalize();
    }

    public static void cleanupOldBackups() {
        try {
            Path backupDir = getBackupDirectory();
            if (Files.exists(backupDir)) {
                cleanupOldBackups(backupDir);
            }
        } catch (Exception e) {
            LOGGER.error("[NeoBackup] Failed to cleanup old backups: {}", e.getMessage());
        }
    }

    private static void cleanupOldBackups(Path backupDir) {
        int maxCount = NeoBackupConfig.COMMON.maxBackupCount.get();
        int maxSizeGB = NeoBackupConfig.COMMON.maxBackupSizeGB.get();

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
            LOGGER.error("[NeoBackup] Failed to cleanup old backups: {}", e.getMessage());
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
            LOGGER.info("[NeoBackup] Deleted old backup: {}", file.getFileName());
        } catch (IOException e) {
            LOGGER.error("[NeoBackup] Failed to delete backup {}: {}", file.getFileName(), e.getMessage());
        }
    }
}

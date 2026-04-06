package ms.maomer.neobackup.backup;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import ms.maomer.neobackup.NeoBackupConfig;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackupScheduler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String THREAD_NAME = "NeoBackup Scheduler";
    private static final String STATE_FILE_NAME = ".neobackup_state";

    private static volatile ScheduledExecutorService scheduler;
    private static volatile Cron cron;
    private static volatile ScheduledFuture<?> scheduledBackup;
    private static final AtomicBoolean hasPlayerJoinedSinceLastBackup = new AtomicBoolean(true);
    private static volatile MinecraftServer server;

    private BackupScheduler() {
    }

    public static void init(MinecraftServer minecraftServer) {
        server = minecraftServer;
        loadState();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        parseCronExpression();
        scheduleNextBackup();
        BackupManager.cleanupOldBackups();
        LOGGER.info("[NeoBackup] Backup scheduler initialized");
    }

    private static void parseCronExpression() {
        String cronExpr = NeoBackupConfig.COMMON.cronExpression.get();
        try {
            CronDefinition definition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
            CronParser parser = new CronParser(definition);
            Cron parsedCron = parser.parse(cronExpr);
            parsedCron.validate();
            cron = parsedCron;
            LOGGER.info("[NeoBackup] Cron expression: {}", cronExpr);
        } catch (IllegalArgumentException e) {
            LOGGER.error("[NeoBackup] Invalid cron expression: {}", cronExpr);
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpr, e);
        }
    }

    private static void scheduleNextBackup() {
        if (scheduledBackup != null && !scheduledBackup.isDone()) {
            scheduledBackup.cancel(false);
        }

        if (cron == null || scheduler == null || scheduler.isShutdown()) {
            LOGGER.warn("[NeoBackup] Scheduler not initialized");
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        executionTime.timeToNextExecution(now).ifPresentOrElse(
            delay -> {
                long delayMillis = delay.toMillis();
                LOGGER.info("[NeoBackup] Next backup in: {}h {}m", 
                    delayMillis / 3600000, 
                    (delayMillis % 3600000) / 60000);

                scheduledBackup = scheduler.schedule(() -> {
                    performScheduledBackup();
                    scheduleNextBackup();
                }, delayMillis, TimeUnit.MILLISECONDS);
            },
            () -> LOGGER.warn("[NeoBackup] Could not determine next backup time")
        );
    }

    private static void performScheduledBackup() {
        if (shouldSkipBackup()) {
            LOGGER.info("[NeoBackup] Skipping: no players since last backup");
            return;
        }

        MinecraftServer currentServer = server;
        if (currentServer != null) {
            BackupManager.performBackup(currentServer, false);
            hasPlayerJoinedSinceLastBackup.set(false);
            saveState();
        } else {
            LOGGER.error("[NeoBackup] Server is null, cannot backup");
        }
    }

    public static void playerJoined() {
        hasPlayerJoinedSinceLastBackup.set(true);
        saveState();
        LOGGER.info("[NeoBackup] Player joined, backup enabled");
    }

    private static boolean shouldSkipBackup() {
        if (!NeoBackupConfig.COMMON.skipIfNoPlayers.get()) {
            return false;
        }
        return !hasPlayerJoinedSinceLastBackup.get();
    }

    public static void forceBackup() {
        MinecraftServer currentServer = server;
        if (currentServer != null) {
            LOGGER.info("[NeoBackup] Forced backup requested");
            BackupManager.performBackup(currentServer, true);
            hasPlayerJoinedSinceLastBackup.set(false);
            saveState();
        } else {
            LOGGER.error("[NeoBackup] Server is null, cannot backup");
        }
    }

    public static void reload() {
        parseCronExpression();
        scheduleNextBackup();
        BackupManager.cleanupOldBackups();
        LOGGER.info("[NeoBackup] Config reloaded");
    }

    private static Path getStateFilePath() {
        String backupPathStr = NeoBackupConfig.COMMON.backupPath.get();
        Path backupPath = Paths.get(backupPathStr);
        if (!backupPath.isAbsolute()) {
            backupPath = Paths.get(".").resolve(backupPathStr);
        }
        return backupPath.resolve(STATE_FILE_NAME);
    }

    private static void loadState() {
        Path stateFile = getStateFilePath();
        if (Files.exists(stateFile)) {
            try {
                String content = Files.readString(stateFile).trim();
                boolean hasJoined = Boolean.parseBoolean(content);
                hasPlayerJoinedSinceLastBackup.set(hasJoined);
                LOGGER.info("[NeoBackup] Loaded player state: {}", hasJoined);
            } catch (IOException e) {
                LOGGER.warn("[NeoBackup] Failed to load state: {}, assuming no player activity", e.getMessage());
                hasPlayerJoinedSinceLastBackup.set(false);
                saveState();
            }
        } else {
            LOGGER.info("[NeoBackup] No state file found, will backup on first run");
            hasPlayerJoinedSinceLastBackup.set(true);
        }
    }

    private static void saveState() {
        Path stateFile = getStateFilePath();
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, String.valueOf(hasPlayerJoinedSinceLastBackup.get()),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        } catch (IOException e) {
            LOGGER.error("[NeoBackup] Failed to save state: {}", e.getMessage());
        }
    }

    public static void shutdown() {
        ScheduledExecutorService currentScheduler = scheduler;
        if (currentScheduler != null) {
            currentScheduler.shutdown();
            try {
                if (!currentScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    currentScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                currentScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }
}

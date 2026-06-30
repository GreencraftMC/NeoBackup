package ms.maomer.neobackup.backup;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import ms.maomer.neobackup.NeoBackupConfig;
import ms.maomer.neobackup.ServerBridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BackupScheduler {
    private static final Logger LOGGER = LogManager.getLogger("NeoBackup");
    private static final String THREAD_NAME = "NeoBackup Scheduler";
    private static final String STATE_FILE_NAME = ".neobackup_state";
    private static final long STATE_WRITE_INTERVAL_MS = 30_000;

    private final ServerBridge bridge;
    private final Supplier<NeoBackupConfig> configSupplier;
    private volatile NeoBackupConfig config;
    private ScheduledExecutorService scheduler;
    private Cron cron;
    private ScheduledFuture<?> scheduledBackup;
    private final AtomicBoolean hasPlayerJoinedSinceLastBackup = new AtomicBoolean(true);
    private final Object lock = new Object();
    private volatile long lastStateWrite;
    private volatile boolean stateDirty;

    public BackupScheduler(ServerBridge bridge, Supplier<NeoBackupConfig> configSupplier) {
        this.bridge = bridge;
        this.configSupplier = configSupplier;
    }

    public void init() {
        config = configSupplier.get();
        loadState();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        parseCronExpression();
        scheduleNextBackup();
        BackupManager.cleanupOldBackups(config);
        LOGGER.info("[NeoBackup] Backup scheduler initialized");
    }

    private void parseCronExpression() {
        String cronExpr = config.getCronExpression();
        try {
            CronDefinition definition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
            CronParser parser = new CronParser(definition);
            Cron parsedCron = parser.parse(cronExpr);
            parsedCron.validate();
            cron = parsedCron;
            LOGGER.info("[NeoBackup] Cron expression: " + cronExpr);
        } catch (IllegalArgumentException e) {
            LOGGER.error("[NeoBackup] Invalid cron expression: " + cronExpr);
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpr, e);
        }
    }

    private void scheduleNextBackup() {
        synchronized (lock) {
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
                    LOGGER.info("[NeoBackup] Next backup in: " + (delayMillis / 3600000) + "h "
                        + ((delayMillis % 3600000) / 60000) + "m");

                    scheduledBackup = scheduler.schedule(() -> {
                        performScheduledBackup();
                        scheduleNextBackup();
                    }, delayMillis, TimeUnit.MILLISECONDS);
                },
                () -> LOGGER.warn("[NeoBackup] Could not determine next backup time")
            );
        }
    }

    private void performScheduledBackup() {
        if (shouldSkipBackup()) {
            LOGGER.info("[NeoBackup] Skipping: no players since last backup");
            return;
        }

        BackupManager.performBackup(bridge, config, false);
        hasPlayerJoinedSinceLastBackup.set(false);
        saveState();
    }

    public void playerJoined() {
        hasPlayerJoinedSinceLastBackup.set(true);
        saveState();
        LOGGER.info("[NeoBackup] Player joined, backup enabled");
    }

    private boolean shouldSkipBackup() {
        if (!config.isSkipIfNoPlayers()) {
            return false;
        }
        return !hasPlayerJoinedSinceLastBackup.get();
    }

    public CompletableFuture<Void> forceBackup() {
        LOGGER.info("[NeoBackup] Forced backup requested");
        var future = BackupManager.performBackup(bridge, config, true);
        hasPlayerJoinedSinceLastBackup.set(false);
        saveState();
        return future;
    }

    public void reload() {
        synchronized (lock) {
            config = configSupplier.get();
            parseCronExpression();
            scheduleNextBackup();
            BackupManager.cleanupOldBackups(config);
        }
        LOGGER.info("[NeoBackup] Config reloaded");
    }

    private Path getStateFilePath() {
        String backupPathStr = config.getBackupPath();
        Path backupPath = Paths.get(backupPathStr);
        if (!backupPath.isAbsolute()) {
            backupPath = Paths.get(".").resolve(backupPathStr);
        }
        return backupPath.resolve(STATE_FILE_NAME);
    }

    private void loadState() {
        Path stateFile = getStateFilePath();
        if (Files.exists(stateFile)) {
            try {
                String content = Files.readString(stateFile).trim();
                boolean hasJoined = Boolean.parseBoolean(content);
                hasPlayerJoinedSinceLastBackup.set(hasJoined);
                LOGGER.info("[NeoBackup] Loaded player state: " + hasJoined);
            } catch (IOException e) {
                LOGGER.warn("[NeoBackup] Failed to load state: " + e.getMessage() + ", assuming no player activity");
                hasPlayerJoinedSinceLastBackup.set(false);
                saveState();
            }
        } else {
            LOGGER.info("[NeoBackup] No state file found, will backup on first run");
            hasPlayerJoinedSinceLastBackup.set(true);
        }
    }

    private void saveState() {
        stateDirty = true;
        long now = System.currentTimeMillis();
        if (now - lastStateWrite < STATE_WRITE_INTERVAL_MS) {
            return;
        }
        flushState(now, false);
    }

    private void flushState(long now, boolean sync) {
        try {
            Files.createDirectories(getStateFilePath().getParent());
            OpenOption[] opts = sync
                ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC}
                : new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
            Files.writeString(getStateFilePath(), String.valueOf(hasPlayerJoinedSinceLastBackup.get()), opts);
            lastStateWrite = now;
            stateDirty = false;
        } catch (IOException e) {
            LOGGER.error("[NeoBackup] Failed to save state: " + e.getMessage());
        }
    }

    public void shutdown() {
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
        if (stateDirty) {
            flushState(System.currentTimeMillis(), true);
        }
        BackupManager.shutdownExecutor();
    }
}

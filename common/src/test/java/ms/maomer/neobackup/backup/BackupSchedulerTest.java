package ms.maomer.neobackup.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ms.maomer.neobackup.NeoBackupConfig;
import ms.maomer.neobackup.ServerBridge;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

class BackupSchedulerTest {

    private ServerBridge bridge;
    private Supplier<NeoBackupConfig> configSupplier;
    private BackupScheduler scheduler;

    @BeforeEach
    void setUp() {
        bridge = mock(ServerBridge.class);
        when(bridge.worldsRootPath()).thenReturn(Path.of("."));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(bridge).executeOnMainThread(any());
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void initWithValidCron() {
        configSupplier = () -> new NeoBackupConfig("0 */5 * * *", "zip",
            "./backups", false, List.of(), 0, 0);
        scheduler = new BackupScheduler(bridge, configSupplier);
        assertDoesNotThrow(() -> scheduler.init());
    }

    @Test
    void initWithInvalidCronThrows() {
        configSupplier = () -> new NeoBackupConfig("not-a-cron", "zip",
            "./backups", false, List.of(), 0, 0);
        scheduler = new BackupScheduler(bridge, configSupplier);
        assertThrows(IllegalArgumentException.class,
            () -> scheduler.init());
    }

    @Test
    void forceBackupAfterInit(@TempDir Path tempDir) {
        configSupplier = () -> new NeoBackupConfig("0 */5 * * *", "zip",
            tempDir.toAbsolutePath().toString(), false, List.of(), 0, 0);
        scheduler = new BackupScheduler(bridge, configSupplier);
        scheduler.init();
        assertDoesNotThrow(() -> scheduler.forceBackup());
        verify(bridge, atLeastOnce()).saveAll();
    }

    @Test
    void reloadUpdatesCron() {
        configSupplier = () -> new NeoBackupConfig("0 */5 * * *", "zip",
            "./backups", false, List.of(), 0, 0);
        scheduler = new BackupScheduler(bridge, configSupplier);
        scheduler.init();
        assertDoesNotThrow(() -> scheduler.reload());
    }

    @Test
    void playerJoinedDoesNotThrow() {
        configSupplier = () -> new NeoBackupConfig("0 */5 * * *", "zip",
            "./backups", false, List.of(), 0, 0);
        scheduler = new BackupScheduler(bridge, configSupplier);
        scheduler.init();
        assertDoesNotThrow(() -> scheduler.playerJoined());
    }
}

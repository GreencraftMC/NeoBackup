package ms.maomer.neobackup;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

class NeoBackupConfigTest {

    @Test
    void defaultsWhenNull() {
        var config = new NeoBackupConfig(null, null, null, false, null, 0, 0);
        assertNull(config.getCronExpression());
        assertEquals("zip", config.getCompressionMethod());
        assertEquals("./backups", config.getBackupPath());
        assertTrue(config.getExcludedFiles().contains("session.lock"));
    }

    @Test
    void invalidCompressionFallsBackToZip() {
        var config = new NeoBackupConfig("* * * * *", "rar", "./backups", false, List.of(), 5, 10);
        assertEquals("zip", config.getCompressionMethod());
    }

    @Test
    void zstdCompressionAccepted() {
        var config = new NeoBackupConfig("* * * * *", "zstd", "./backups", false, List.of(), 5, 10);
        assertEquals("zstd", config.getCompressionMethod());
    }

    @Test
    void maxCountClampsToZero() {
        var config = new NeoBackupConfig("* * * * *", "zip", "./backups", false, List.of(), -5, -1);
        assertEquals(0, config.getMaxBackupCount());
        assertEquals(0, config.getMaxBackupSizeGB());
    }

    @Test
    void gettersMatchConstructor() {
        var excluded = List.of("session.lock", "playerdata");
        var config = new NeoBackupConfig("0 */5 * * *", "zstd", "/backups", true, excluded, 10, 50);
        assertEquals("0 */5 * * *", config.getCronExpression());
        assertEquals("zstd", config.getCompressionMethod());
        assertEquals("/backups", config.getBackupPath());
        assertTrue(config.isSkipIfNoPlayers());
        assertEquals(excluded, config.getExcludedFiles());
        assertEquals(10, config.getMaxBackupCount());
        assertEquals(50, config.getMaxBackupSizeGB());
    }
}

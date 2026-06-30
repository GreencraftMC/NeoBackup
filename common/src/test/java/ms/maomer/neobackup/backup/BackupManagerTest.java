package ms.maomer.neobackup.backup;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ms.maomer.neobackup.NeoBackupConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

class BackupManagerTest {

    @Test
    void cleanupByCount(@TempDir Path tempDir) throws IOException {
        createBackupFiles(tempDir, 10, ".zip");

        var config = new NeoBackupConfig("* * * * *", "zip",
            tempDir.toAbsolutePath().toString(), false, List.of(), 3, 0);

        BackupManager.cleanupOldBackups(config);

        try (var files = Files.list(tempDir)) {
            long remaining = files.filter(p -> p.toString().endsWith(".zip")).count();
            assertEquals(3, remaining, "Should keep only 3 backups");
        }
    }

    @Test
    void cleanupBySize(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("backup_1.zip"), "a".repeat(500));
        Files.writeString(tempDir.resolve("backup_2.zip"), "b".repeat(300));
        Files.writeString(tempDir.resolve("backup_3.zip"), "c".repeat(200));

        var config = new NeoBackupConfig("* * * * *", "zip",
            tempDir.toAbsolutePath().toString(), false, List.of(), 0, 0);

        BackupManager.cleanupOldBackups(config);
        assertEquals(3, Files.list(tempDir).filter(p -> p.toString().endsWith(".zip")).count());
    }

    @Test
    void noCleanupWhenLimitsDisabled(@TempDir Path tempDir) throws IOException {
        createBackupFiles(tempDir, 5, ".zip");

        var config = new NeoBackupConfig("* * * * *", "zip",
            tempDir.toAbsolutePath().toString(), false, List.of(), 0, 0);

        BackupManager.cleanupOldBackups(config);
        assertEquals(5, Files.list(tempDir).filter(p -> p.toString().endsWith(".zip")).count());
    }

    private void createBackupFiles(Path dir, int count, String suffix) throws IOException {
        for (int i = 0; i < count; i++) {
            var file = dir.resolve("backup_" + i + suffix);
            Files.writeString(file, "data-" + i);
            Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(i)));
        }
    }
}

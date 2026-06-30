package ms.maomer.neobackup.backup;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ms.maomer.neobackup.NeoBackupConfig;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import com.github.luben.zstd.ZstdInputStream;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.ArrayList;

class CompressionUtilTest {

    private static NeoBackupConfig config(String... excluded) {
        return new NeoBackupConfig("* * * * *", "zip", "./backups", false,
            excluded.length > 0 ? List.of(excluded) : List.of(), 0, 0);
    }

    @Test
    void zstdRoundTrip(@TempDir Path tempDir) throws IOException {
        var sourceDir = tempDir.resolve("world");
        createTestFiles(sourceDir);
        var targetFile = tempDir.resolve("backup.tar.zst");

        CompressionUtil.compressWithZstd(sourceDir, targetFile, config());
        assertTrue(Files.exists(targetFile));
        assertTrue(Files.size(targetFile) > 0);

        verifyTarContent(targetFile, sourceDir);
    }

    @Test
    void zipRoundTrip(@TempDir Path tempDir) throws IOException {
        var sourceDir = tempDir.resolve("world");
        createTestFiles(sourceDir);
        var targetFile = tempDir.resolve("backup.zip");

        CompressionUtil.compressWithZip(sourceDir, targetFile, config());
        assertTrue(Files.exists(targetFile));
        assertTrue(Files.size(targetFile) > 0);
    }

    @Test
    void excludedFilesSkippedInZstd(@TempDir Path tempDir) throws IOException {
        var sourceDir = tempDir.resolve("world");
        createTestFiles(sourceDir);
        Files.writeString(sourceDir.resolve("session.lock"), "locked");
        var targetFile = tempDir.resolve("backup.tar.zst");

        CompressionUtil.compressWithZstd(sourceDir, targetFile, config("session.lock"));
        assertTrue(Files.exists(targetFile));

        verifyTarExcludes(targetFile, "session.lock");
    }

    private void createTestFiles(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("region"));
        Files.writeString(dir.resolve("level.dat"), "level data");
        Files.writeString(dir.resolve("region/r.0.0.mca"), "region data");
    }

    private void verifyTarContent(Path tarFile, Path expectedSource) throws IOException {
        try (var fis = new FileInputStream(tarFile.toFile());
             var zis = new ZstdInputStream(fis);
             var tis = new TarArchiveInputStream(zis)) {
            boolean foundLevelDat = false;
            boolean foundRegion = false;
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.getName().equals("level.dat")) foundLevelDat = true;
                if (entry.getName().equals("region/r.0.0.mca")) foundRegion = true;
            }
            assertTrue(foundLevelDat, "level.dat should be in archive");
            assertTrue(foundRegion, "region/r.0.0.mca should be in archive");
        }
    }

    private void verifyTarExcludes(Path tarFile, String excludedName) throws IOException {
        try (var fis = new FileInputStream(tarFile.toFile());
             var zis = new ZstdInputStream(fis);
             var tis = new TarArchiveInputStream(zis)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                assertFalse(entry.getName().contains(excludedName),
                    "Archive should not contain: " + excludedName);
            }
        }
    }
}

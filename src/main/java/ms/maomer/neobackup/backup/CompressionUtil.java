package ms.maomer.neobackup.backup;

import com.github.luben.zstd.ZstdOutputStream;
import ms.maomer.neobackup.NeoBackupConfig;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CompressionUtil {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int BUFFER_SIZE = 8192;
    private static final int ZSTD_COMPRESSION_LEVEL = 3;

    private static boolean shouldSkip(Path file) {
        List<? extends String> excluded = NeoBackupConfig.COMMON.excludedFiles.get();
        String fileName = file.getFileName().toString();
        String pathStr = file.toString().replace('\\', '/');

        for (String pattern : excluded) {
            if (fileName.equals(pattern) || pathStr.endsWith("/" + pattern) || pathStr.endsWith(pattern)) {
                LOGGER.info("[NeoBackup] Skipping: {}", fileName);
                return true;
            }
        }
        return false;
    }

    public static void compressWithZstd(Path sourceDir, Path targetFile) throws IOException {
        LOGGER.info("Compressing with zstd: {} -> {}", sourceDir, targetFile);

        try (OutputStream out = Files.newOutputStream(targetFile);
             ZstdOutputStream zstdOut = new ZstdOutputStream(out, ZSTD_COMPRESSION_LEVEL);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(zstdOut)) {

            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (shouldSkip(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String entryName = sourceDir.relativize(dir).toString();
                    if (!entryName.isEmpty()) {
                        TarArchiveEntry entry = new TarArchiveEntry(dir.toFile(), entryName + "/");
                        tarOut.putArchiveEntry(entry);
                        tarOut.closeArchiveEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (shouldSkip(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    String entryName = sourceDir.relativize(file).toString();
                    TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), entryName);
                    entry.setSize(Files.size(file));
                    tarOut.putArchiveEntry(entry);

                    try (InputStream in = Files.newInputStream(file)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            tarOut.write(buffer, 0, bytesRead);
                        }
                    }

                    tarOut.closeArchiveEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        LOGGER.info("Zstd compression completed: {}", targetFile);
    }

    public static void compressWithZip(Path sourceDir, Path targetFile) throws IOException {
        LOGGER.info("Compressing with zip: {} -> {}", sourceDir, targetFile);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetFile))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (shouldSkip(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String entryName = sourceDir.relativize(dir).toString() + "/";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (shouldSkip(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    String entryName = sourceDir.relativize(file).toString();
                    zos.putNextEntry(new ZipEntry(entryName));

                    try (InputStream in = Files.newInputStream(file)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            zos.write(buffer, 0, bytesRead);
                        }
                    }

                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        LOGGER.info("Zip compression completed: {}", targetFile);
    }
}

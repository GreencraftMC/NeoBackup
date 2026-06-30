package ms.maomer.neobackup;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class NeoBackupConfig {

    private static final Set<String> VALID_COMPRESSION_METHODS = Set.of("zstd", "zip");

    private final String cronExpression;
    private final String compressionMethod;
    private final String backupPath;
    private final boolean skipIfNoPlayers;
    private final List<String> excludedFiles;
    private final int maxBackupCount;
    private final int maxBackupSizeGB;

    public NeoBackupConfig(String cronExpression, String compressionMethod, String backupPath,
                           boolean skipIfNoPlayers, List<String> excludedFiles,
                           int maxBackupCount, int maxBackupSizeGB) {
        this.cronExpression = cronExpression;
        this.compressionMethod = isValidCompressionMethod(compressionMethod) ? compressionMethod.toLowerCase() : "zip";
        String normalizedPath = backupPath != null ? backupPath.trim() : "./backups";
        if (normalizedPath.isEmpty()) normalizedPath = "./backups";
        this.backupPath = normalizedPath;
        this.skipIfNoPlayers = skipIfNoPlayers;
        this.excludedFiles = excludedFiles != null ? Collections.unmodifiableList(excludedFiles) : List.of("session.lock");
        this.maxBackupCount = Math.max(0, maxBackupCount);
        this.maxBackupSizeGB = Math.max(0, maxBackupSizeGB);
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getCompressionMethod() {
        return compressionMethod;
    }

    public String getBackupPath() {
        return backupPath;
    }

    public boolean isSkipIfNoPlayers() {
        return skipIfNoPlayers;
    }

    public List<String> getExcludedFiles() {
        return excludedFiles;
    }

    public int getMaxBackupCount() {
        return maxBackupCount;
    }

    public int getMaxBackupSizeGB() {
        return maxBackupSizeGB;
    }

    private static boolean isValidCompressionMethod(String method) {
        return method != null && VALID_COMPRESSION_METHODS.contains(method.toLowerCase());
    }
}

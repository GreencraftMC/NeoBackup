package ms.maomer.neobackup;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.nio.file.Paths;

public class NeoForgeConfigLoader implements Supplier<NeoBackupConfig> {

    private static final Set<String> VALID_COMPRESSION_METHODS = Set.of("zstd", "zip");

    public final ModConfigSpec spec;
    public final Common configValues;

    public NeoForgeConfigLoader() {
        Pair<Common, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Common::new);
        this.configValues = pair.getLeft();
        this.spec = pair.getRight();
    }

    @Override
    public NeoBackupConfig get() {
        return new NeoBackupConfig(
            configValues.cronExpression.get(),
            configValues.compressionMethod.get(),
            configValues.backupPath.get(),
            configValues.skipIfNoPlayers.get(),
            List.copyOf(configValues.excludedFiles.get()),
            configValues.maxBackupCount.get(),
            configValues.maxBackupSizeGB.get()
        );
    }

    public static class Common {
        public final ModConfigSpec.ConfigValue<String> cronExpression;
        public final ModConfigSpec.ConfigValue<String> compressionMethod;
        public final ModConfigSpec.ConfigValue<String> backupPath;
        public final ModConfigSpec.BooleanValue skipIfNoPlayers;
        public final ModConfigSpec.ConfigValue<List<? extends String>> excludedFiles;
        public final ModConfigSpec.IntValue maxBackupCount;
        public final ModConfigSpec.IntValue maxBackupSizeGB;

        public Common(ModConfigSpec.Builder builder) {
            builder.push("neobackup");
            cronExpression = builder
                .comment(
                    "Crontab expression for backup schedule.",
                    "Format: minute hour dayOfMonth month dayOfWeek",
                    "Examples:",
                    "  '0 2 * * *'     - Daily at 2:00 AM",
                    "  '0 */6 * * *'   - Every 6 hours",
                    "  '0 0 * * 0'     - Every Sunday at midnight",
                    "  '30 4 * * 1-5'  - Weekdays at 4:30 AM",
                    "  '0 0 1 * *'     - First day of each month",
                    "  '0 */2 * * *'   - Every 2 hours",
                    "  '0 0 * * 1,4'   - Monday and Thursday at midnight"
                )
                .define("cronExpression", "0 2 * * *");
            compressionMethod = builder
                .comment(
                    "Compression method: 'zstd' or 'zip'",
                    "Zstd is faster(REALLY REALLY FAST, recommended) and more efficient, but may not be supported on all platforms."
                )
                .define("compressionMethod", "zip", NeoForgeConfigLoader::isValidCompressionMethod);
            backupPath = builder
                .comment(
                    "Backup storage path.",
                    "Supported formats:",
                    "  ./backups              - Relative to server directory",
                    "  ../backups             - Parent directory",
                    "  C:\\\\backups            - Absolute Windows path",
                    "  C:\\\\backups\\\\minecraft  - Absolute Windows path with subfolder"
                )
                .define("backupPath", "./backups", NeoForgeConfigLoader::isValidPath);
            skipIfNoPlayers = builder
                .comment("Skip backup if no players have joined since last backup")
                .define("skipIfNoPlayers", true);
            excludedFiles = builder
                .comment(
                    "List of file/folder names to exclude from backup.",
                    "These are matched against the file name or path ending."
                )
                .defineListAllowEmpty("excludedFiles",
                    () -> List.of("session.lock"),
                    obj -> obj instanceof String s && !s.isEmpty()
                );
            maxBackupCount = builder
                .comment(
                    "Maximum number of backups to keep.",
                    "Applied after size limit. Set to 0 to disable."
                )
                .defineInRange("maxBackupCount", 0, 0, Integer.MAX_VALUE);
            maxBackupSizeGB = builder
                .comment(
                    "Maximum total backup directory size in GB.",
                    "This limit is applied first. Older backups are deleted when exceeded.",
                    "Set to 0 to disable."
                )
                .defineInRange("maxBackupSizeGB", 0, 0, Integer.MAX_VALUE);
            builder.pop();
        }
    }

    private static boolean isValidCompressionMethod(Object obj) {
        return obj instanceof String s && VALID_COMPRESSION_METHODS.contains(s.toLowerCase());
    }

    private static boolean isValidPath(Object obj) {
        if (!(obj instanceof String s) || s.trim().isEmpty()) {
            return false;
        }
        try {
            Paths.get(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

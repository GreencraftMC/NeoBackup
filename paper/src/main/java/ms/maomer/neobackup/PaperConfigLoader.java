package ms.maomer.neobackup;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Supplier;

public class PaperConfigLoader implements Supplier<NeoBackupConfig> {

    private final JavaPlugin plugin;

    public PaperConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public NeoBackupConfig get() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        return new NeoBackupConfig(
            cfg.getString("cronExpression", "0 2 * * *"),
            cfg.getString("compressionMethod", "zip"),
            cfg.getString("backupPath", "./backups"),
            cfg.getBoolean("skipIfNoPlayers", true),
            cfg.getStringList("excludedFiles"),
            cfg.getInt("maxBackupCount", 0),
            cfg.getInt("maxBackupSizeGB", 0)
        );
    }
}

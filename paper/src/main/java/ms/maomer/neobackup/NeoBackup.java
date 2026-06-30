package ms.maomer.neobackup;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import ms.maomer.neobackup.backup.BackupScheduler;
import ms.maomer.neobackup.command.NeoBackupCommand;
import ms.maomer.neobackup.event.PlayerActivityListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NeoBackup extends JavaPlugin {

    private static final Logger LOGGER = LogManager.getLogger("NeoBackup");

    private PaperConfigLoader configLoader;
    private BackupScheduler scheduler;

    @Override
    public void onEnable() {
        LOGGER.info("NeoBackup plugin initializing...");

        configLoader = new PaperConfigLoader(this);
        PaperServerBridge bridge = new PaperServerBridge(getServer(), this);

        scheduler = new BackupScheduler(bridge, configLoader);
        scheduler.init();

        getServer().getPluginManager().registerEvents(new PlayerActivityListener(this), this);

        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            NeoBackupCommand.register(event.registrar());
        });

        LOGGER.info("NeoBackup enabled");
    }

    @Override
    public void onDisable() {
        LOGGER.info("Shutting down backup scheduler");
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public BackupScheduler getScheduler() {
        return scheduler;
    }
}

package ms.maomer.neobackup;

import ms.maomer.neobackup.backup.BackupScheduler;
import ms.maomer.neobackup.command.NeoBackupCommand;
import ms.maomer.neobackup.event.PlayerActivityListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("neobackup")
public class NeoBackup {
    private static final Logger LOGGER = LogManager.getLogger();
    private static NeoBackup instance;

    private final NeoForgeConfigLoader configLoader;
    private BackupScheduler scheduler;

    public NeoBackup(IEventBus modBus, ModContainer modContainer) {
        instance = this;
        LOGGER.info("NeoBackup mod initializing...");

        configLoader = new NeoForgeConfigLoader();
        modContainer.registerConfig(ModConfig.Type.COMMON, configLoader.spec);

        NeoForge.EVENT_BUS.addListener(this::onCommandsRegister);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.register(new PlayerActivityListener());
    }

    public static NeoBackup getInstance() {
        return instance;
    }

    public BackupScheduler getScheduler() {
        return scheduler;
    }

    private void onCommandsRegister(RegisterCommandsEvent event) {
        LOGGER.info("Registering NeoBackup commands");
        NeoBackupCommand.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting, initializing backup scheduler");
        NeoForgeServerBridge bridge = new NeoForgeServerBridge(event.getServer());
        scheduler = new BackupScheduler(bridge, configLoader);
        scheduler.init();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping, shutting down backup scheduler");
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}

package ms.maomer.neobackup;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import ms.maomer.neobackup.backup.BackupScheduler;
import ms.maomer.neobackup.command.NeoBackupCommand;
import ms.maomer.neobackup.event.PlayerActivityListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("neobackup")
public class NeoBackup {
    private static final Logger LOGGER = LogManager.getLogger();

    public NeoBackup(IEventBus modBus, ModContainer modContainer) {
        LOGGER.info("NeoBackup mod initializing...");

        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, NeoBackupConfig.COMMON_SPEC);

        // 注册事件监听器
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onServerSetup);
        NeoForge.EVENT_BUS.addListener(this::onCommandsRegister);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.register(new PlayerActivityListener());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("NeoBackup common setup");
    }

    private void onServerSetup(FMLDedicatedServerSetupEvent event) {
        LOGGER.info("NeoBackup server setup");
    }

    private void onCommandsRegister(RegisterCommandsEvent event) {
        LOGGER.info("Registering NeoBackup commands");
        NeoBackupCommand.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting, initializing backup scheduler");
        BackupScheduler.init(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping, shutting down backup scheduler");
        BackupScheduler.shutdown();
    }
}
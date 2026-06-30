package ms.maomer.neobackup;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public class NeoForgeServerBridge implements ServerBridge {

    private final MinecraftServer server;

    public NeoForgeServerBridge(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Path worldsRootPath() {
        return server.getWorldPath(LevelResource.ROOT);
    }

    @Override
    public void saveAll() {
        server.saveEverything(true, true, true);
    }

    @Override
    public void executeOnMainThread(Runnable task) {
        server.execute(task);
    }
}

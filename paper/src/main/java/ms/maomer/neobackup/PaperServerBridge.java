package ms.maomer.neobackup;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public class PaperServerBridge implements ServerBridge {

    private final org.bukkit.Server server;
    private final JavaPlugin plugin;

    public PaperServerBridge(org.bukkit.Server server, JavaPlugin plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    @Override
    public Path worldsRootPath() {
        var mainWorld = server.getWorlds().getFirst();
        return server.getWorldContainer().toPath().resolve(mainWorld.getName());
    }

    @Override
    public void saveAll() {
        for (World world : server.getWorlds()) {
            world.save();
        }
        server.savePlayers();
    }

    @Override
    public void executeOnMainThread(Runnable task) {
        server.getScheduler().runTask(plugin, task);
    }
}

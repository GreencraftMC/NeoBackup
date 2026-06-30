package ms.maomer.neobackup.event;

import ms.maomer.neobackup.NeoBackup;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerActivityListener implements Listener {

    private final NeoBackup plugin;

    public PlayerActivityListener(NeoBackup plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var scheduler = plugin.getScheduler();
        if (scheduler != null) {
            scheduler.playerJoined();
        }
    }
}

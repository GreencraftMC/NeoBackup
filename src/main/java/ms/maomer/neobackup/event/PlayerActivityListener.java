package ms.maomer.neobackup.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import ms.maomer.neobackup.backup.BackupScheduler;

public class PlayerActivityListener {

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            BackupScheduler.playerJoined();
        }
    }
}

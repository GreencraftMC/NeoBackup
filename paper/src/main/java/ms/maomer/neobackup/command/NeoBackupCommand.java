package ms.maomer.neobackup.command;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.TextColor.color;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import ms.maomer.neobackup.NeoBackup;
import ms.maomer.neobackup.backup.BackupScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NeoBackupCommand {

    private NeoBackupCommand() {
    }

    private static final int COLOR_GOLD = 0xFFAA00;
    private static final int COLOR_YELLOW = 0xFFFF55;
    private static final int COLOR_GREEN = 0x55FF55;
    private static final int COLOR_RED = 0xFF5555;

    public static void register(Commands commands) {
        var plugin = JavaPlugin.getPlugin(NeoBackup.class);
        commands.register(
            Commands.literal("neobackup")
                .requires(Commands.restricted(
                    source -> source.getSender().hasPermission("neobackup.admin")
                ))
                .then(Commands.literal("backup")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        source.getSender().sendMessage(
                            text("[NeoBackup] ", color(COLOR_GOLD))
                                .append(text("Starting backup...", color(COLOR_YELLOW)))
                        );
                        BackupScheduler scheduler = plugin.getScheduler();
                        if (scheduler == null) {
                            source.getSender().sendMessage(
                                text("[NeoBackup] ", color(COLOR_GOLD))
                                    .append(text("Scheduler not initialized", color(COLOR_RED)))
                            );
                            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                        }
                        CompletableFuture<Void> future = scheduler.forceBackup();
                        future.whenComplete((v, t) -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (t == null) {
                                    source.getSender().sendMessage(
                                        text("[NeoBackup] ", color(COLOR_GOLD))
                                            .append(text("Backup completed!", color(COLOR_GREEN)))
                                    );
                                } else {
                                    source.getSender().sendMessage(
                                        text("[NeoBackup] ", color(COLOR_GOLD))
                                            .append(text("Backup failed: " + t.getMessage(), color(COLOR_RED)))
                                    );
                                }
                            });
                        });
                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        source.getSender().sendMessage(
                            text("[NeoBackup] ", color(COLOR_GOLD))
                                .append(text("Reloading config...", color(COLOR_YELLOW)))
                        );
                        BackupScheduler scheduler = plugin.getScheduler();
                        if (scheduler != null) {
                            scheduler.reload();
                        }
                        source.getSender().sendMessage(
                            text("[NeoBackup] ", color(COLOR_GOLD))
                                .append(text("Config reloaded!", color(COLOR_GREEN)))
                        );
                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                    })
                )
                .build(),
            "NeoBackup backup and reload commands",
            List.of("nb")
        );
    }
}

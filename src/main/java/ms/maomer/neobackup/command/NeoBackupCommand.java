package ms.maomer.neobackup.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import ms.maomer.neobackup.backup.BackupScheduler;

public class NeoBackupCommand {
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    private NeoBackupCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("neobackup")
            .requires(source -> source.hasPermission(REQUIRED_PERMISSION_LEVEL))
            .then(Commands.literal("backup")
                .executes(NeoBackupCommand::forceBackup)
            )
            .then(Commands.literal("reload")
                .executes(NeoBackupCommand::reloadConfig)
            )
        );
    }

    private static int forceBackup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("[NeoBackup] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Starting backup...").withStyle(ChatFormatting.YELLOW)), true);

        source.getServer().execute(() -> {
            BackupScheduler.forceBackup();
            source.sendSuccess(() -> Component.literal("[NeoBackup] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Backup completed!").withStyle(ChatFormatting.GREEN)), true);
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("[NeoBackup] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Reloading config...").withStyle(ChatFormatting.YELLOW)), true);

        source.getServer().execute(() -> {
            BackupScheduler.reload();
            source.sendSuccess(() -> Component.literal("[NeoBackup] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Config reloaded!").withStyle(ChatFormatting.GREEN)), true);
        });

        return Command.SINGLE_SUCCESS;
    }
}

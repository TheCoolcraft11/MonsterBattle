package de.thecoolcraft11.monsterBattle.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /worldname [player]
 * Shows the world name the command sender (or specified player) is currently in.
 */
public class WorldNameCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("monsterbattle.worldname")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
                return true;
            }
            target = p;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }
        }
        World w = target.getWorld();
        sender.sendMessage(ChatColor.GREEN + target.getName() + ChatColor.GRAY + " is in world " + ChatColor.AQUA + w.getName() + ChatColor.GRAY + " (" + w.getEnvironment().name() + ")");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("monsterbattle.worldname")) return Collections.emptyList();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .sorted().collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}


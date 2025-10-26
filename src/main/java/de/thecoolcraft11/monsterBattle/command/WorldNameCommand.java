package de.thecoolcraft11.monsterBattle.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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


public class WorldNameCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("monsterbattle.worldname")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Usage: /" + label + " <player>", NamedTextColor.YELLOW));
                return true;
            }
            target = p;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
                return true;
            }
        }
        World w = target.getWorld();
        sender.sendMessage(Component.text()
                .append(Component.text(target.getName(), NamedTextColor.GREEN))
                .append(Component.text(" is in world ", NamedTextColor.GRAY))
                .append(Component.text(w.getName(), NamedTextColor.AQUA))
                .append(Component.text(" (" + w.getEnvironment().name() + ")", NamedTextColor.GRAY))
                .build());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
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


package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class ArmyCommand implements CommandExecutor, TabCompleter {

    private final MonsterBattle plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList("save", "load");

    public ArmyCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length < 1) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /army <save|load> [filename]", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "save" -> {
                return handleSave(sender, args);
            }
            case "load" -> {
                return handleLoad(sender, args);
            }
            default -> {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Unknown subcommand. Use: /army <save|load> [filename]", NamedTextColor.RED))
                                .build()
                );
                return true;
            }
        }
    }

    private boolean handleSave(CommandSender sender, String[] args) {
        String filename = args.length > 1 ? args[1] : "armies.yml";


        if (!filename.endsWith(".yml")) {
            filename += ".yml";
        }

        File saveFile = new File(plugin.getDataFolder(), filename);

        try {

            if (!plugin.getDataFolder().exists()) {
                boolean created = plugin.getDataFolder().mkdirs();
                if (!created && !plugin.getDataFolder().exists()) {
                    plugin.getLogger().warning("Could not create data folder: " + plugin.getDataFolder().getAbsolutePath());
                    sender.sendMessage(
                            Component.text()
                                    .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                    .append(Component.text("Failed to create data folder.", NamedTextColor.RED))
                                    .build()
                    );
                    return true;
                }
            }

            plugin.getDataController().saveArmies(saveFile);

            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Successfully saved armies to ", NamedTextColor.GREEN))
                            .append(Component.text(filename, NamedTextColor.YELLOW))
                            .build()
            );
            plugin.getLogger().info("Armies saved to " + filename + " by " + sender.getName());
            return true;
        } catch (IOException e) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Failed to save armies: " + e.getMessage(), NamedTextColor.RED))
                            .build()
            );
            plugin.getLogger().severe("Failed to save armies: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Exception while saving armies", e);
            return true;
        }
    }

    private boolean handleLoad(CommandSender sender, String[] args) {
        String filename = args.length > 1 ? args[1] : "armies.yml";


        if (!filename.endsWith(".yml")) {
            filename += ".yml";
        }

        File loadFile = new File(plugin.getDataFolder(), filename);

        if (!loadFile.exists()) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("File ", NamedTextColor.RED))
                            .append(Component.text(filename, NamedTextColor.YELLOW))
                            .append(Component.text(" does not exist!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        try {
            plugin.getDataController().loadArmies(loadFile);

            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Successfully loaded armies from ", NamedTextColor.GREEN))
                            .append(Component.text(filename, NamedTextColor.YELLOW))
                            .build()
            );
            plugin.getLogger().info("Armies loaded from " + filename + " by " + sender.getName());
            return true;
        } catch (IOException e) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Failed to load armies: " + e.getMessage(), NamedTextColor.RED))
                            .build()
            );
            plugin.getLogger().severe("Failed to load armies: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Exception while loading armies", e);
            return true;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {

            String partial = args[0].toLowerCase();
            for (String subCmd : SUBCOMMANDS) {
                if (subCmd.startsWith(partial)) {
                    completions.add(subCmd);
                }
            }
        } else if (args.length == 2) {

            String partial = args[1].toLowerCase();
            File dataFolder = plugin.getDataFolder();
            if (dataFolder.exists() && dataFolder.isDirectory()) {
                File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName();
                        if (name.toLowerCase().startsWith(partial)) {
                            completions.add(name);
                        }
                    }
                }
            }

            if ("armies.yml".startsWith(partial)) {
                completions.add("armies.yml");
            }
        }

        return completions;
    }
}

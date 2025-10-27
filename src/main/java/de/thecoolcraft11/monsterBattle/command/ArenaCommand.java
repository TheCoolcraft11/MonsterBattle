package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ArenaCommand implements CommandExecutor, TabCompleter {

    private final MonsterBattle plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "tp", "delete", "rename", "load", "teams", "change-template");

    public ArenaCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("monsterbattle.arena")) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("You don't have permission.", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /" + label + " <create|tp|delete|rename|load|teams|change-template> [args...]", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "create" -> {
                return handleCreate(sender, args);
            }
            case "tp" -> {
                return handleTeleport(sender, args);
            }
            case "delete" -> {
                return handleDelete(sender, args);
            }
            case "rename" -> {
                return handleRename(sender, args);
            }
            case "load" -> {
                return handleLoad(sender, args);
            }
            case "teams" -> {
                return handleTeams(sender, args);
            }
            case "change-template" -> {
                return handleChangeTemplate(sender, args);
            }
            default -> {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Invalid subcommand. Use: create, tp, delete, rename, load, teams, or change-template", NamedTextColor.RED))
                                .build()
                );
                return true;
            }
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena create <worldName>", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        String worldName = args[1];


        if (Bukkit.getWorld(worldName) != null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("World '" + worldName + "' already exists!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        Path serverRoot = Bukkit.getWorldContainer().toPath();
        Path worldPath = serverRoot.resolve(worldName);
        if (Files.exists(worldPath)) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("World folder '" + worldName + "' already exists!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Creating world '" + worldName + "'...", NamedTextColor.YELLOW))
                        .build()
        );

        try {
            World world = new WorldCreator(worldName)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generator(new VoidWorldGenerator())
                    .createWorld();

            if (world != null) {

                Location spawnLocation = new Location(world, 0.5, 76, 0.5);
                world.setSpawnLocation(spawnLocation);


                world.getBlockAt(0, 75, 0).setType(Material.STONE);

                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("World '" + worldName + "' created successfully!", NamedTextColor.GREEN))
                                .build()
                );
                plugin.getLogger().info("Arena world '" + worldName + "' created by " + sender.getName());
            } else {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Failed to create world '" + worldName + "'", NamedTextColor.RED))
                                .build()
                );
            }
        } catch (Exception e) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Error creating world: " + e.getMessage(), NamedTextColor.RED))
                            .build()
            );
            plugin.getLogger().severe("Failed to create arena world '" + worldName + "': " + e.getMessage());
        }

        return true;
    }

    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Only players can teleport.", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        String worldName;

        if (args.length < 2) {

            String templateWorld = plugin.getConfig().getString("arena-template-world", "Arena");
            String currentWorldName = player.getWorld().getName();

            if (currentWorldName.equals(templateWorld)) {

                worldName = "world";
            } else {

                worldName = templateWorld;
            }
        } else {
            worldName = args[1];
        }

        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("World '" + worldName + "' not found!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        player.teleport(world.getSpawnLocation());
        player.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Teleported to world '" + worldName + "'", NamedTextColor.GREEN))
                        .build()
        );

        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena delete <worldName> [--force]", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        String worldName = args[1];
        boolean force = args.length > 2 && args[2].equalsIgnoreCase("--force");


        if (worldName.equals("world") || worldName.equals("world_nether") || worldName.equals("world_the_end")) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Cannot delete default server worlds!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        String templateWorld = plugin.getConfig().getString("arena-template-world", "Arena");
        if (worldName.equals(templateWorld) && !force) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Cannot delete the arena template world! Use --force to override, or change 'arena-template-world' in config first.", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        World world = Bukkit.getWorld(worldName);


        if (world != null) {
            List<Player> playersInWorld = new ArrayList<>(world.getPlayers());
            if (!playersInWorld.isEmpty()) {
                World defaultWorld = Bukkit.getWorlds().getFirst();
                for (Player p : playersInWorld) {
                    p.teleport(defaultWorld.getSpawnLocation());
                    p.sendMessage(
                            Component.text()
                                    .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                    .append(Component.text("You were teleported out because world '" + worldName + "' is being deleted.", NamedTextColor.YELLOW))
                                    .build()
                    );
                }
            }


            boolean unloaded = Bukkit.unloadWorld(world, false);
            if (!unloaded) {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Failed to unload world '" + worldName + "'", NamedTextColor.RED))
                                .build()
                );
                return true;
            }
        }


        Path serverRoot = Bukkit.getWorldContainer().toPath();
        Path worldPath = serverRoot.resolve(worldName);

        if (!Files.exists(worldPath)) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("World folder '" + worldName + "' does not exist!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Deleting world '" + worldName + "'...", NamedTextColor.YELLOW))
                        .build()
        );


        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deleteDirectory(worldPath);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(
                            Component.text()
                                    .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                    .append(Component.text("World '" + worldName + "' deleted successfully!", NamedTextColor.GREEN))
                                    .build()
                    );
                    plugin.getLogger().info("Arena world '" + worldName + "' deleted by " + sender.getName());
                });
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(
                            Component.text()
                                    .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                    .append(Component.text("Error deleting world: " + e.getMessage(), NamedTextColor.RED))
                                    .build()
                    );
                    plugin.getLogger().severe("Failed to delete arena world '" + worldName + "': " + e.getMessage());
                });
            }
        });

        return true;
    }

    private boolean handleRename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena rename <oldWorldName> <newWorldName> [--update-config]", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        String oldWorldName = args[1];
        String newWorldName = args[2];
        boolean updateConfig = args.length > 3 && args[3].equalsIgnoreCase("--update-config");


        if (oldWorldName.equals("world") || oldWorldName.equals("world_nether") || oldWorldName.equals("world_the_end")) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Cannot rename default server worlds!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        if (newWorldName.equals("world") || newWorldName.equals("world_nether") || newWorldName.equals("world_the_end")) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Cannot rename to a default server world name!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        Path serverRoot = Bukkit.getWorldContainer().toPath();
        Path oldWorldPath = serverRoot.resolve(oldWorldName);
        Path newWorldPath = serverRoot.resolve(newWorldName);


        if (!Files.exists(oldWorldPath)) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("World '" + oldWorldName + "' does not exist!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        if (Files.exists(newWorldPath)) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("A world with name '" + newWorldName + "' already exists!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        World oldWorld = Bukkit.getWorld(oldWorldName);
        if (oldWorld != null) {

            List<Player> playersInWorld = new ArrayList<>(oldWorld.getPlayers());
            if (!playersInWorld.isEmpty()) {
                World defaultWorld = Bukkit.getWorlds().getFirst();
                for (Player p : playersInWorld) {
                    p.teleport(defaultWorld.getSpawnLocation());
                    p.sendMessage(
                            Component.text()
                                    .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                    .append(Component.text("You were teleported out because world '" + oldWorldName + "' is being renamed.", NamedTextColor.YELLOW))
                                    .build()
                    );
                }
            }


            boolean unloaded = Bukkit.unloadWorld(oldWorld, true);
            if (!unloaded) {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Failed to unload world '" + oldWorldName + "'. Try again later.", NamedTextColor.RED))
                                .build()
                );
                return true;
            }
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Renaming world '" + oldWorldName + "' to '" + newWorldName + "'...", NamedTextColor.YELLOW))
                        .build()
        );


        try {
            Files.move(oldWorldPath, newWorldPath);


            try {
                Files.deleteIfExists(newWorldPath.resolve("uid.dat"));
            } catch (IOException ignored) {
            }


            World renamedWorld = new WorldCreator(newWorldName)
                    .environment(World.Environment.NORMAL)
                    .createWorld();

            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("World renamed successfully! '" + oldWorldName + "' → '" + newWorldName + "'", NamedTextColor.GREEN))
                            .build()
            );

            if (renamedWorld != null) {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("World loaded and ready for use.", NamedTextColor.GRAY))
                                .build()
                );
            }

            plugin.getLogger().info("World '" + oldWorldName + "' renamed to '" + newWorldName + "' by " + sender.getName());


            String templateWorld = plugin.getConfig().getString("arena-template-world", "Arena");
            if (updateConfig && oldWorldName.equals(templateWorld)) {
                plugin.getConfig().set("arena-template-world", newWorldName);
                plugin.saveConfig();
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Updated arena-template-world in config to '" + newWorldName + "'", NamedTextColor.GRAY))
                                .build()
                );
            } else if (!updateConfig && oldWorldName.equals(templateWorld)) {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Note: This was the template world. Use --update-config to update the config automatically.", NamedTextColor.YELLOW))
                                .build()
                );
            }
        } catch (IOException e) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Error renaming world: " + e.getMessage(), NamedTextColor.RED))
                            .build()
            );
            plugin.getLogger().severe("Failed to rename world '" + oldWorldName + "' to '" + newWorldName + "': " + e.getMessage());
        }

        return true;
    }

    private boolean handleLoad(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena load <worldName>", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        String worldName = args[1];


        Path serverRoot = Bukkit.getWorldContainer().toPath();
        Path worldPath = serverRoot.resolve(worldName);

        if (!Files.exists(worldPath)) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("World folder '" + worldName + "' does not exist!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("World '" + worldName + "' is already loaded.", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Loading world '" + worldName + "'...", NamedTextColor.YELLOW))
                        .build()
        );

        try {
            World world = new WorldCreator(worldName)
                    .environment(World.Environment.NORMAL)
                    .createWorld();

            if (world != null) {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("World '" + worldName + "' loaded successfully!", NamedTextColor.GREEN))
                                .build()
                );
                plugin.getLogger().info("World '" + worldName + "' loaded by " + sender.getName());
            } else {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Failed to load world '" + worldName + "'", NamedTextColor.RED))
                                .build()
                );
            }
        } catch (Exception e) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Error loading world: " + e.getMessage(), NamedTextColor.RED))
                            .build()
            );
            plugin.getLogger().severe("Failed to load world '" + worldName + "': " + e.getMessage());
        }

        return true;
    }

    private boolean handleTeams(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena teams <create|delete|reset|list|setup|join|leave> [args...]", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        String teamsSubCommand = args[1].toLowerCase(Locale.ROOT);
        switch (teamsSubCommand) {
            case "create" -> {
                return handleTeamsCreate(sender, args);
            }
            case "delete" -> {
                return handleTeamsDelete(sender, args);
            }
            case "reset" -> {
                return handleTeamsReset(sender);
            }
            case "list" -> {
                return handleTeamsList(sender);
            }
            case "setup" -> {
                return handleTeamsSetup(sender, args);
            }
            case "join" -> {
                return handleTeamsJoin(sender, args);
            }
            case "leave" -> {
                return handleTeamsLeave(sender, args);
            }
            default -> {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Invalid teams subcommand. Use: create, delete, reset, list, setup, join, or leave", NamedTextColor.RED))
                                .build()
                );
                return true;
            }
        }
    }

    private boolean handleTeamsCreate(CommandSender sender, String[] args) {

        if (args.length < 4) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena teams create <name> <color> [--friendlyFire] [--prefix <text>] [--suffix <text>] [player1 player2 ...]", NamedTextColor.YELLOW))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("  Use quotes for spaces: ", NamedTextColor.GRAY))
                            .append(Component.text("--prefix \"[Team Name]\"", NamedTextColor.WHITE))
                            .build()
            );
            return true;
        }


        List<String> parsedArgs = parseQuotedArgs(args);

        String teamName = parsedArgs.get(2);
        String colorName = parsedArgs.get(3).toUpperCase();


        NamedTextColor teamColor;
        try {

            teamColor = NamedTextColor.NAMES.value(colorName.toLowerCase());
            if (teamColor == null) {
                throw new IllegalArgumentException("Unknown color");
            }
        } catch (Exception e) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Invalid color: " + colorName, NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        boolean friendlyFire = false;
        String prefix = null;
        String suffix = null;
        List<String> players = new ArrayList<>();

        for (int i = 4; i < parsedArgs.size(); i++) {
            String arg = parsedArgs.get(i);
            if (arg.equalsIgnoreCase("--friendlyFire")) {
                friendlyFire = true;
            } else if (arg.equalsIgnoreCase("--prefix") && i + 1 < parsedArgs.size()) {
                prefix = parsedArgs.get(++i);
            } else if (arg.equalsIgnoreCase("--suffix") && i + 1 < parsedArgs.size()) {
                suffix = parsedArgs.get(++i);
            } else if (!arg.startsWith("--")) {
                players.add(arg);
            }
        }


        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        org.bukkit.scoreboard.Scoreboard scoreboard = manager.getMainScoreboard();


        if (scoreboard.getTeam(teamName) != null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Team '" + teamName + "' already exists!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        org.bukkit.scoreboard.Team team = scoreboard.registerNewTeam(teamName);
        team.color(teamColor);
        team.setAllowFriendlyFire(friendlyFire);

        if (prefix != null) {

            String finalPrefix = prefix.replace("{}", teamName);
            team.prefix(Component.text(finalPrefix));
        }
        if (suffix != null) {

            String finalSuffix = suffix.replace("{}", teamName);
            team.suffix(Component.text(finalSuffix));
        }


        for (String playerName : players) {
            team.addEntry(playerName);
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Team '" + teamName + "' created with color " + colorName, NamedTextColor.GREEN))
                        .build()
        );

        if (!players.isEmpty()) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Added " + players.size() + " player(s) to the team.", NamedTextColor.GRAY))
                            .build()
            );
        }

        plugin.getLogger().info("Team '" + teamName + "' created by " + sender.getName());
        return true;
    }

    private boolean handleTeamsDelete(CommandSender sender, String[] args) {

        if (args.length < 3) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena teams delete <teamName>", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        String teamName = args[2];

        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        org.bukkit.scoreboard.Scoreboard scoreboard = manager.getMainScoreboard();
        org.bukkit.scoreboard.Team team = scoreboard.getTeam(teamName);

        if (team == null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Team '" + teamName + "' does not exist!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }

        team.unregister();
        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Team '" + teamName + "' deleted successfully!", NamedTextColor.GREEN))
                        .build()
        );

        plugin.getLogger().info("Team '" + teamName + "' deleted by " + sender.getName());
        return true;
    }

    private boolean handleTeamsReset(CommandSender sender) {

        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        org.bukkit.scoreboard.Scoreboard scoreboard = manager.getMainScoreboard();

        Set<org.bukkit.scoreboard.Team> teams = new HashSet<>(scoreboard.getTeams());
        int count = teams.size();

        if (count == 0) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("No teams to delete.", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        for (org.bukkit.scoreboard.Team team : teams) {
            team.unregister();
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("All " + count + " team(s) deleted successfully!", NamedTextColor.GREEN))
                        .build()
        );

        plugin.getLogger().info("All teams reset by " + sender.getName());
        return true;
    }

    private boolean handleTeamsList(CommandSender sender) {

        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        org.bukkit.scoreboard.Scoreboard scoreboard = manager.getMainScoreboard();

        Set<org.bukkit.scoreboard.Team> teams = scoreboard.getTeams();

        if (teams.isEmpty()) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("No teams configured.", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Teams (" + teams.size() + "):", NamedTextColor.GREEN))
                        .build()
        );

        for (org.bukkit.scoreboard.Team team : teams) {

            net.kyori.adventure.text.format.TextColor teamColor = NamedTextColor.WHITE;
            try {
                net.kyori.adventure.text.format.TextColor color = team.color();
                if (color != null) {
                    teamColor = color;
                }
            } catch (IllegalStateException e) {

                teamColor = NamedTextColor.WHITE;
            }

            Component teamInfo = Component.text()
                    .append(Component.text("  • ", NamedTextColor.GRAY))
                    .append(Component.text(team.getName(), teamColor))
                    .append(Component.text(" - " + team.getEntries().size() + " member(s)", NamedTextColor.GRAY))
                    .build();

            if (team.allowFriendlyFire()) {
                teamInfo = teamInfo.append(Component.text(" [FF]", NamedTextColor.RED));
            }

            sender.sendMessage(teamInfo);
        }

        return true;
    }

    private boolean handleTeamsSetup(CommandSender sender, String[] args) {

        if (args.length < 3) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena teams setup <baseName> [options]", NamedTextColor.YELLOW))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("  Base name: ", NamedTextColor.GRAY))
                            .append(Component.text("Use {} as placeholder → Team_{} becomes Team_1, Team_2", NamedTextColor.WHITE))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("  Options: ", NamedTextColor.GRAY))
                            .append(Component.text("--color-team1 <color>, --color-team2 <color>, --friendlyFire", NamedTextColor.WHITE))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("           ", NamedTextColor.GRAY))
                            .append(Component.text("--prefix <text>, --suffix <text>", NamedTextColor.WHITE))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("  Teams: ", NamedTextColor.GRAY))
                            .append(Component.text("--team1 <players...>, --team2 <players...>", NamedTextColor.WHITE))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("  Use quotes for spaces: ", NamedTextColor.GRAY))
                            .append(Component.text("--prefix \"[Team {}]\"", NamedTextColor.WHITE))
                            .build()
            );
            return true;
        }


        List<String> parsedArgs = parseQuotedArgs(args);

        String baseName = parsedArgs.get(2);


        boolean friendlyFire = false;
        String prefix = null;
        String suffix = null;
        Map<Integer, NamedTextColor> teamColors = new HashMap<>();
        Map<Integer, List<String>> teamPlayers = new HashMap<>();

        int currentTeam = -1;

        for (int i = 3; i < parsedArgs.size(); i++) {
            String arg = parsedArgs.get(i);
            if (arg.equalsIgnoreCase("--friendlyFire")) {
                friendlyFire = true;
            } else if (arg.equalsIgnoreCase("--prefix") && i + 1 < parsedArgs.size()) {
                prefix = parsedArgs.get(++i);
            } else if (arg.equalsIgnoreCase("--suffix") && i + 1 < parsedArgs.size()) {
                suffix = parsedArgs.get(++i);
            } else if (arg.equalsIgnoreCase("--color-team1") && i + 1 < parsedArgs.size()) {
                String colorName = parsedArgs.get(++i).toLowerCase();
                NamedTextColor color = NamedTextColor.NAMES.value(colorName);
                if (color != null) {
                    teamColors.put(1, color);
                } else {
                    sender.sendMessage(
                            Component.text()
                                    .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                    .append(Component.text("Invalid color for team1: " + colorName, NamedTextColor.RED))
                                    .build()
                    );
                }
            } else if (arg.equalsIgnoreCase("--color-team2") && i + 1 < parsedArgs.size()) {
                String colorName = parsedArgs.get(++i).toLowerCase();
                NamedTextColor color = NamedTextColor.NAMES.value(colorName);
                if (color != null) {
                    teamColors.put(2, color);
                } else {
                    sender.sendMessage(
                            Component.text()
                                    .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                    .append(Component.text("Invalid color for team2: " + colorName, NamedTextColor.RED))
                                    .build()
                    );
                }
            } else if (arg.matches("--team\\d+")) {

                currentTeam = Integer.parseInt(arg.substring(6));
                teamPlayers.putIfAbsent(currentTeam, new ArrayList<>());
            } else if (currentTeam != -1 && !arg.startsWith("--")) {
                teamPlayers.get(currentTeam).add(arg);
            }
        }


        if (teamPlayers.isEmpty()) {
            teamPlayers.put(1, new ArrayList<>());
            teamPlayers.put(2, new ArrayList<>());
        } else if (teamPlayers.size() == 1) {

            int existingTeam = teamPlayers.keySet().iterator().next();
            int newTeamNum = existingTeam == 1 ? 2 : 1;
            teamPlayers.put(newTeamNum, new ArrayList<>());
        }


        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        org.bukkit.scoreboard.Scoreboard scoreboard = manager.getMainScoreboard();


        NamedTextColor[] defaultColors = {NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.YELLOW, NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE};

        int created = 0;
        for (Map.Entry<Integer, List<String>> entry : teamPlayers.entrySet()) {
            int teamNum = entry.getKey();
            List<String> players = entry.getValue();


            String teamName;
            if (baseName.contains("{}")) {
                teamName = baseName.replace("{}", String.valueOf(teamNum));
            } else {
                teamName = baseName + teamNum;
            }


            if (scoreboard.getTeam(teamName) != null) {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Team '" + teamName + "' already exists, skipping...", NamedTextColor.YELLOW))
                                .build()
                );
                continue;
            }

            org.bukkit.scoreboard.Team team = scoreboard.registerNewTeam(teamName);


            NamedTextColor color = teamColors.getOrDefault(teamNum, defaultColors[(teamNum - 1) % defaultColors.length]);
            team.color(color);
            team.setAllowFriendlyFire(friendlyFire);

            if (prefix != null) {

                String finalPrefix = prefix.replace("{}", teamName);
                team.prefix(Component.text(finalPrefix));
            }
            if (suffix != null) {

                String finalSuffix = suffix.replace("{}", teamName);
                team.suffix(Component.text(finalSuffix));
            }


            for (String playerName : players) {
                team.addEntry(playerName);
            }

            created++;
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Team '" + teamName + "' created with " + players.size() + " player(s)", NamedTextColor.GREEN))
                            .build()
            );
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Setup complete! Created " + created + " team(s).", NamedTextColor.GREEN))
                        .build()
        );

        plugin.getLogger().info("Team setup completed by " + sender.getName() + " - created " + created + " teams");
        return true;
    }

    private boolean handleTeamsJoin(CommandSender sender, String[] args) {

        if (args.length < 4) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena teams join <teamName> <player1> [player2] ...", NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }


        List<String> parsedArgs = parseQuotedArgs(args);

        String teamName = parsedArgs.get(2);


        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        org.bukkit.scoreboard.Scoreboard scoreboard = manager.getMainScoreboard();
        org.bukkit.scoreboard.Team team = scoreboard.getTeam(teamName);

        if (team == null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Team '" + teamName + "' does not exist!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        List<String> players = new ArrayList<>();
        for (int i = 3; i < parsedArgs.size(); i++) {
            players.add(parsedArgs.get(i));
        }

        if (players.isEmpty()) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("No players specified!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        int added = 0;
        int alreadyInTeam = 0;
        for (String playerName : players) {
            if (team.hasEntry(playerName)) {
                alreadyInTeam++;
            } else {
                team.addEntry(playerName);
                added++;
            }
        }


        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Added " + added + " player(s) to team '" + teamName + "'", NamedTextColor.GREEN))
                        .build()
        );

        if (alreadyInTeam > 0) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text(alreadyInTeam + " player(s) were already in the team", NamedTextColor.GRAY))
                            .build()
            );
        }

        plugin.getLogger().info("Added " + added + " player(s) to team '" + teamName + "' by " + sender.getName());
        return true;
    }

    private boolean handleTeamsLeave(CommandSender sender, String[] args) {


        if (args.length < 3) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage:", NamedTextColor.YELLOW))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("  /arena teams leave <player1> [player2] ...", NamedTextColor.WHITE))
                            .append(Component.text(" - Remove players from teams", NamedTextColor.GRAY))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("  /arena teams leave --team <teamName>", NamedTextColor.WHITE))
                            .append(Component.text(" - Kick all players from team", NamedTextColor.GRAY))
                            .build()
            );
            return true;
        }


        List<String> parsedArgs = parseQuotedArgs(args);

        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        org.bukkit.scoreboard.Scoreboard scoreboard = manager.getMainScoreboard();


        if (parsedArgs.get(2).equalsIgnoreCase("--team")) {
            if (parsedArgs.size() < 4) {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Usage: /arena teams leave --team <teamName>", NamedTextColor.YELLOW))
                                .build()
                );
                return true;
            }

            String teamName = parsedArgs.get(3);
            org.bukkit.scoreboard.Team team = scoreboard.getTeam(teamName);

            if (team == null) {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Team '" + teamName + "' does not exist!", NamedTextColor.RED))
                                .build()
                );
                return true;
            }


            Set<String> entries = new HashSet<>(team.getEntries());
            int count = entries.size();

            if (count == 0) {
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                                .append(Component.text("Team '" + teamName + "' has no members.", NamedTextColor.YELLOW))
                                .build()
                );
                return true;
            }


            for (String entry : entries) {
                team.removeEntry(entry);
            }

            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Kicked all " + count + " player(s) from team '" + teamName + "'", NamedTextColor.GREEN))
                            .build()
            );

            plugin.getLogger().info("Kicked all " + count + " player(s) from team '" + teamName + "' by " + sender.getName());
            return true;
        }


        List<String> players = new ArrayList<>();
        for (int i = 2; i < parsedArgs.size(); i++) {
            players.add(parsedArgs.get(i));
        }

        if (players.isEmpty()) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("No players specified!", NamedTextColor.RED))
                            .build()
            );
            return true;
        }


        int removed = 0;
        int notInAnyTeam = 0;

        for (String playerName : players) {
            boolean wasInTeam = false;


            for (org.bukkit.scoreboard.Team team : scoreboard.getTeams()) {
                if (team.hasEntry(playerName)) {
                    team.removeEntry(playerName);
                    removed++;
                    wasInTeam = true;
                    break;
                }
            }

            if (!wasInTeam) {
                notInAnyTeam++;
            }
        }


        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Removed " + removed + " player(s) from their teams", NamedTextColor.GREEN))
                        .build()
        );

        if (notInAnyTeam > 0) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text(notInAnyTeam + " player(s) were not in any team", NamedTextColor.GRAY))
                            .build()
            );
        }

        plugin.getLogger().info("Removed " + removed + " player(s) from their teams by " + sender.getName());
        return true;
    }

    private boolean handleChangeTemplate(CommandSender sender, String[] args) {
        if (args.length < 2) {

            String currentTemplate = plugin.getConfig().getString("arena-template-world", "Arena");
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Current template world: ", NamedTextColor.YELLOW))
                            .append(Component.text(currentTemplate, NamedTextColor.GREEN))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Usage: /arena change-template <worldName>", NamedTextColor.GRAY))
                            .build()
            );
            return true;
        }

        String newTemplateName = args[1];
        String oldTemplateName = plugin.getConfig().getString("arena-template-world", "Arena");


        Path serverRoot = Bukkit.getWorldContainer().toPath();
        Path worldPath = serverRoot.resolve(newTemplateName);

        if (!Files.exists(worldPath)) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("World folder '" + newTemplateName + "' does not exist!", NamedTextColor.RED))
                            .build()
            );
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                            .append(Component.text("Create it first with /arena create " + newTemplateName, NamedTextColor.YELLOW))
                            .build()
            );
            return true;
        }


        plugin.getConfig().set("arena-template-world", newTemplateName);
        plugin.saveConfig();

        sender.sendMessage(
                Component.text()
                        .append(Component.text("[MonsterBattle] ", NamedTextColor.GOLD))
                        .append(Component.text("Arena template world changed!", NamedTextColor.GREEN))
                        .build()
        );
        sender.sendMessage(
                Component.text()
                        .append(Component.text("  Old: ", NamedTextColor.GRAY))
                        .append(Component.text(oldTemplateName, NamedTextColor.RED))
                        .build()
        );
        sender.sendMessage(
                Component.text()
                        .append(Component.text("  New: ", NamedTextColor.GRAY))
                        .append(Component.text(newTemplateName, NamedTextColor.GREEN))
                        .build()
        );

        plugin.getLogger().info("Arena template world changed from '" + oldTemplateName + "' to '" + newTemplateName + "' by " + sender.getName());

        return true;
    }

    /**
     * Parse command arguments with support for quoted strings.
     * Handles both single and double quotes.
     * Example: ["--prefix", "\"My", "Team\""] -> ["--prefix", "My Team"]
     */
    private List<String> parseQuotedArgs(String[] args) {
        List<String> result = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '"';

        for (String arg : args) {
            if (arg.isEmpty()) continue;


            if (!inQuotes && (arg.startsWith("\"") || arg.startsWith("'"))) {
                inQuotes = true;
                quoteChar = arg.charAt(0);
                currentArg = new StringBuilder(arg.substring(1));


                if (!currentArg.isEmpty() && currentArg.charAt(currentArg.length() - 1) == quoteChar) {
                    currentArg.setLength(currentArg.length() - 1);
                    result.add(currentArg.toString());
                    currentArg = new StringBuilder();
                    inQuotes = false;
                }
            } else if (inQuotes) {

                if (arg.endsWith(String.valueOf(quoteChar))) {

                    currentArg.append(" ").append(arg, 0, arg.length() - 1);
                    result.add(currentArg.toString());
                    currentArg = new StringBuilder();
                    inQuotes = false;
                } else {

                    currentArg.append(" ").append(arg);
                }
            } else {

                result.add(arg);
            }
        }


        if (inQuotes && !currentArg.isEmpty()) {
            result.add(currentArg.toString());
        }

        return result;
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("monsterbattle.arena")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(cmd -> cmd.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);

            switch (subCommand) {
                case "tp" -> {

                    return Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .sorted()
                            .collect(Collectors.toList());
                }
                case "delete", "rename", "change-template" -> {

                    return Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .filter(name -> !name.equals("world")
                                    && !name.equals("world_nether")
                                    && !name.equals("world_the_end"))
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .sorted()
                            .collect(Collectors.toList());
                }
                case "load" -> {

                    Path serverRoot = Bukkit.getWorldContainer().toPath();
                    List<String> unloadedWorlds = new ArrayList<>();

                    try {
                        Files.list(serverRoot)
                                .filter(Files::isDirectory)
                                .map(path -> path.getFileName().toString())
                                .filter(name -> {

                                    Path levelDat = serverRoot.resolve(name).resolve("level.dat");
                                    return Files.exists(levelDat);
                                })
                                .filter(name -> Bukkit.getWorld(name) == null)
                                .filter(name -> name.toLowerCase().startsWith(prefix))
                                .forEach(unloadedWorlds::add);
                    } catch (IOException ignored) {

                    }

                    return unloadedWorlds.stream().sorted().collect(Collectors.toList());
                }
                case "create" -> {

                    List<String> suggestions = new ArrayList<>();
                    String templateWorld = plugin.getConfig().getString("arena-template-world", "Arena");
                    suggestions.add(templateWorld);
                    return suggestions.stream()
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .collect(Collectors.toList());
                }
                case "teams" -> {

                    List<String> teamsSubcommands = Arrays.asList("create", "delete", "reset", "list", "setup", "join", "leave");
                    return teamsSubcommands.stream()
                            .filter(cmd -> cmd.startsWith(prefix))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[2].toLowerCase(Locale.ROOT);

            switch (subCommand) {
                case "delete" -> {

                    if ("--force".startsWith(prefix)) {
                        return Collections.singletonList("--force");
                    }
                }
                case "rename" -> {

                    String oldWorldName = args[1];
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add(oldWorldName + "_new");
                    suggestions.add(oldWorldName + "_copy");
                    suggestions.add(oldWorldName + "_2");
                    return suggestions.stream()
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .collect(Collectors.toList());
                }
                case "teams" -> {
                    String teamsSubcommand = args[1].toLowerCase(Locale.ROOT);
                    switch (teamsSubcommand) {
                        case "delete", "join" -> {

                            org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
                            return manager.getMainScoreboard().getTeams().stream()
                                    .map(org.bukkit.scoreboard.Team::getName)
                                    .filter(name -> name.toLowerCase().startsWith(prefix))
                                    .sorted()
                                    .collect(Collectors.toList());
                        }
                        case "create" -> {

                            List<String> suggestions = new ArrayList<>();
                            suggestions.add("[Name]");
                            return suggestions.stream()
                                    .filter(name -> name.toLowerCase().startsWith(prefix))
                                    .collect(Collectors.toList());
                        }
                        case "setup" -> {

                            List<String> suggestions = new ArrayList<>();
                            suggestions.add("[BaseName{}]");
                            suggestions.add("Team{}");
                            return suggestions.stream()
                                    .filter(name -> name.toLowerCase().startsWith(prefix))
                                    .collect(Collectors.toList());
                        }
                        case "leave" -> {

                            List<String> suggestions = new ArrayList<>();

                            if ("--team".startsWith(prefix)) {
                                suggestions.add("--team");
                            }


                            Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName)
                                    .filter(name -> name.toLowerCase().startsWith(prefix))
                                    .forEach(suggestions::add);

                            return suggestions;
                        }
                    }
                }
            }
        }

        if (args.length == 4) {
            String subCommand = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[3].toLowerCase(Locale.ROOT);

            if (subCommand.equals("rename")) {

                if ("--update-config".startsWith(prefix)) {
                    return Collections.singletonList("--update-config");
                }
            } else if (subCommand.equals("teams")) {
                String teamsSubcommand = args[1].toLowerCase(Locale.ROOT);
                if (teamsSubcommand.equals("create")) {

                    return Stream.of("red", "blue", "green", "yellow", "aqua", "light_purple",
                                    "white", "black", "gray", "dark_red", "dark_blue", "dark_green",
                                    "dark_aqua", "dark_purple", "gold")
                            .filter(name -> name.startsWith(prefix))
                            .sorted()
                            .collect(Collectors.toList());
                }
            }
        }


        if (args.length >= 4 && args[0].equalsIgnoreCase("teams")) {
            String teamsSubcommand = args[1].toLowerCase(Locale.ROOT);
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            String previousArg = args[args.length - 2];

            switch (teamsSubcommand) {
                case "create" -> {

                    if (previousArg.equalsIgnoreCase("--prefix")) {
                        List<String> suggestions = new ArrayList<>();
                        suggestions.add("[{}] ");
                        suggestions.add("[{}_Team] ");
                        suggestions.add("[Team_{}] ");
                        return suggestions.stream()
                                .filter(s -> s.toLowerCase().startsWith(prefix))
                                .collect(Collectors.toList());
                    }
                    if (previousArg.equalsIgnoreCase("--suffix")) {
                        List<String> suggestions = new ArrayList<>();
                        suggestions.add("[{}]");
                        suggestions.add("_{}");
                        return suggestions.stream()
                                .filter(s -> s.toLowerCase().startsWith(prefix))
                                .collect(Collectors.toList());
                    }


                    List<String> suggestions = new ArrayList<>();


                    boolean hasFriendlyFire = Arrays.asList(args).contains("--friendlyFire");
                    boolean hasPrefix = Arrays.asList(args).contains("--prefix");
                    boolean hasSuffix = Arrays.asList(args).contains("--suffix");

                    if (!hasFriendlyFire && "--friendlyFire".startsWith(prefix)) {
                        suggestions.add("--friendlyFire");
                    }
                    if (!hasPrefix && "--prefix".startsWith(prefix)) {
                        suggestions.add("--prefix");
                    }
                    if (!hasSuffix && "--suffix".startsWith(prefix)) {
                        suggestions.add("--suffix");
                    }


                    if (!prefix.startsWith("--") && !prefix.startsWith("[")) {
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(prefix))
                                .forEach(suggestions::add);


                        if (suggestions.isEmpty() || !prefix.isEmpty()) {
                            suggestions.add("<player>");
                        }
                    }

                    return suggestions;
                }
                case "setup" -> {

                    if (previousArg.equalsIgnoreCase("--prefix")) {
                        List<String> suggestions = new ArrayList<>();
                        suggestions.add("[{}]");
                        suggestions.add("[{}_Team]");
                        suggestions.add("[T_{}]");
                        return suggestions.stream()
                                .filter(s -> s.toLowerCase().startsWith(prefix))
                                .collect(Collectors.toList());
                    }
                    if (previousArg.equalsIgnoreCase("--suffix")) {
                        List<String> suggestions = new ArrayList<>();
                        suggestions.add("[_{}_suffix]");
                        suggestions.add("_{}");
                        suggestions.add("⭐");
                        return suggestions.stream()
                                .filter(s -> s.toLowerCase().startsWith(prefix))
                                .collect(Collectors.toList());
                    }


                    if (previousArg.equalsIgnoreCase("--color-team1") || previousArg.equalsIgnoreCase("--color-team2")) {
                        return Stream.of("red", "blue", "green", "yellow", "aqua", "light_purple",
                                        "white", "black", "gray", "dark_red", "dark_blue", "dark_green",
                                        "dark_aqua", "dark_purple", "gold")
                                .filter(name -> name.startsWith(prefix))
                                .sorted()
                                .collect(Collectors.toList());
                    }


                    List<String> suggestions = new ArrayList<>();


                    boolean hasFriendlyFire = Arrays.asList(args).contains("--friendlyFire");
                    boolean hasPrefix = Arrays.asList(args).contains("--prefix");
                    boolean hasSuffix = Arrays.asList(args).contains("--suffix");
                    boolean hasColorTeam1 = Arrays.asList(args).contains("--color-team1");
                    boolean hasColorTeam2 = Arrays.asList(args).contains("--color-team2");
                    boolean hasTeam1 = Arrays.asList(args).contains("--team1");
                    boolean hasTeam2 = Arrays.asList(args).contains("--team2");


                    if (!hasFriendlyFire && "--friendlyFire".startsWith(prefix)) {
                        suggestions.add("--friendlyFire");
                    }
                    if (!hasPrefix && "--prefix".startsWith(prefix)) {
                        suggestions.add("--prefix");
                    }
                    if (!hasSuffix && "--suffix".startsWith(prefix)) {
                        suggestions.add("--suffix");
                    }
                    if (!hasColorTeam1 && "--color-team1".startsWith(prefix)) {
                        suggestions.add("--color-team1");
                    }
                    if (!hasColorTeam2 && "--color-team2".startsWith(prefix)) {
                        suggestions.add("--color-team2");
                    }


                    if (!hasTeam1 && "--team1".startsWith(prefix)) {
                        suggestions.add("--team1");
                    }
                    if (!hasTeam2 && "--team2".startsWith(prefix)) {
                        suggestions.add("--team2");
                    }


                    if (hasTeam1 && hasTeam2) {
                        for (int i = 3; i <= 6; i++) {
                            String teamFlag = "--team" + i;
                            if (!Arrays.asList(args).contains(teamFlag) && teamFlag.startsWith(prefix)) {
                                suggestions.add(teamFlag);
                            }
                        }
                    }


                    if (!prefix.startsWith("--") && !prefix.startsWith("[")) {
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(prefix))
                                .forEach(suggestions::add);


                        if (suggestions.isEmpty() || !prefix.isEmpty()) {
                            suggestions.add("<player>");
                        }
                    }

                    return suggestions;
                }
                case "join" -> {

                    List<String> suggestions = new ArrayList<>();

                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .forEach(suggestions::add);


                    if (suggestions.isEmpty() || !prefix.isEmpty()) {
                        suggestions.add("<player>");
                    }

                    return suggestions;
                }
                case "leave" -> {

                    if (args[2].equalsIgnoreCase("--team")) {

                        if (args.length == 4) {
                            org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
                            return manager.getMainScoreboard().getTeams().stream()
                                    .map(org.bukkit.scoreboard.Team::getName)
                                    .filter(name -> name.toLowerCase().startsWith(prefix))
                                    .sorted()
                                    .collect(Collectors.toList());
                        }

                        return Collections.emptyList();
                    } else {

                        List<String> suggestions = new ArrayList<>();

                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(prefix))
                                .forEach(suggestions::add);


                        if (suggestions.isEmpty() || !prefix.isEmpty()) {
                            suggestions.add("<player>");
                        }

                        return suggestions;
                    }
                }
            }
        }

        return Collections.emptyList();
    }


    private static class VoidWorldGenerator extends org.bukkit.generator.ChunkGenerator {

    }
}


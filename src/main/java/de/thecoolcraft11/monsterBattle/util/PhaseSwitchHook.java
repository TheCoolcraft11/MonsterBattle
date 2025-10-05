package de.thecoolcraft11.monsterBattle.util;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PhaseSwitchHook {

    private volatile boolean arenaCloneInProgress = false;

    public void newPhase(MonsterBattle plugin, GameState newPhase) {
        DataController dataController = plugin.getDataController();
        if (newPhase == GameState.FARMING) {
            boolean setFarmRespawn = plugin.getConfig().getBoolean("set-farm-respawn", true); 
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager == null) return;
            Set<Team> teams = manager.getMainScoreboard().getTeams();
            if (teams.isEmpty()) return;

            long configuredSeed = plugin.getConfig().getLong("world-seed", -1L);
            if (configuredSeed == -1L && !Bukkit.getWorlds().isEmpty()) {
                configuredSeed = Bukkit.getWorlds().get(0).getSeed();
            }

            dataController.resetTeamKillsForTeams(teams.stream().map(Team::getName).collect(Collectors.toSet()));

            for (Team team : teams) {
                String baseName = "Farm_" + sanitizeWorldName(team.getName());

                World world = Bukkit.getWorld(baseName);
                if (world == null) {
                    world = new WorldCreator(baseName).seed(configuredSeed).environment(World.Environment.NORMAL).createWorld();
                }
                if (world == null) continue;

                createAuxDimensions(plugin, baseName, configuredSeed, null);

                for (String entry : team.getEntries()) {
                    Player player = Bukkit.getPlayerExact(entry);
                    if (player != null && player.isOnline()) {
                        Location spawn = world.getSpawnLocation();
                        player.teleport(spawn);
                        if (setFarmRespawn) {
                            try {
                                player.setBedSpawnLocation(spawn, true);
                            } catch (NoSuchMethodError ignored) {
                            }
                        }
                        player.sendMessage("You have been teleported to your farming world: " + baseName + " (seed: " + configuredSeed + ")" + (setFarmRespawn ? ChatColor.GRAY + " (respawn set)" : ""));
                    }
                }
            }
            return;
        }

        if (newPhase == GameState.BATTLE) {
            handleBattlePhase(plugin);
        }
    }

    private void handleBattlePhase(MonsterBattle plugin) {
        if (arenaCloneInProgress) {
            plugin.getLogger().warning("Battle phase cloning already in progress. Ignoring additional request.");
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Set<Team> teams = manager.getMainScoreboard().getTeams();
        if (teams.isEmpty()) return;

        String templateName = plugin.getConfig().getString("arena-template-world", "Arena");
        boolean separateDims = plugin.getConfig().getBoolean("separate-dimensions", true);
        World template = Bukkit.getWorld(templateName);
        if (template == null) {
            plugin.getLogger().warning("Battle phase aborted: template world '" + templateName + "' is not loaded.");
            return;
        }

        if (!template.getPlayers().isEmpty()) {
            World fallback = Bukkit.getWorlds().get(0);
            for (Player p : new ArrayList<>(template.getPlayers())) {
                p.teleport(fallback.getSpawnLocation());
                p.sendMessage(ChatColor.YELLOW + "Arena template resetting, you were moved.");
            }
        }

        boolean unloaded = Bukkit.unloadWorld(template, true);
        if (!unloaded) {
            plugin.getLogger().warning("Template world could not be unloaded cleanly; copying while loaded may cause corruption.");
        }

        Path serverRoot = Bukkit.getWorldContainer().toPath();
        Path templatePath = serverRoot.resolve(templateName);
        if (!Files.exists(templatePath)) {
            plugin.getLogger().warning("Battle phase aborted: template folder missing.");
            if (unloaded) new WorldCreator(templateName).createWorld();
            return;
        }

        List<Team> creationNeeded = new ArrayList<>();
        for (Team t : teams) {
            String baseName = "Arena_" + sanitizeWorldName(t.getName());
            if (!Files.exists(serverRoot.resolve(baseName))) creationNeeded.add(t);
        }

        for (Team t : creationNeeded) {
            for (String entry : t.getEntries()) {
                Player pl = Bukkit.getPlayerExact(entry);
                if (pl != null) pl.sendMessage(ChatColor.GRAY + "Preparing your arena world...");
            }
        }

        if (creationNeeded.isEmpty()) {
            loadAndTeleportArenas(plugin, teams, separateDims);
            for (Team t : teams) {
                new MonsterSpawner().start(plugin, t.getName());
            }
            if (unloaded) new WorldCreator(templateName).createWorld();
            return;
        }

        arenaCloneInProgress = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AtomicInteger success = new AtomicInteger();
            for (Team t : creationNeeded) {
                String baseName = "Arena_" + sanitizeWorldName(t.getName());
                Path targetPath = serverRoot.resolve(baseName);
                try {
                    copyWorld(templatePath, targetPath);
                    try {
                        Files.deleteIfExists(targetPath.resolve("uid.dat"));
                    } catch (IOException ignored) {
                    }
                    success.incrementAndGet();
                } catch (IOException ex) {
                    plugin.getLogger().severe("Failed copying arena for team '" + t.getName() + "': " + ex.getMessage());
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Arena cloning complete. Created " + success.get() + " new arena worlds.");
                loadAndTeleportArenas(plugin, teams, separateDims);
                
                for (Team t : teams) {
                    new MonsterSpawner().start(plugin, t.getName());
                }
                if (unloaded) new WorldCreator(templateName).createWorld();
                arenaCloneInProgress = false;
            });
        });
    }

    private void loadAndTeleportArenas(MonsterBattle plugin, Set<Team> teams, boolean separateDims) {
        boolean setRespawn = plugin.getConfig().getBoolean("set-arena-respawn", true);
        for (Team team : teams) {
            String baseName = "Arena_" + sanitizeWorldName(team.getName());
            World targetWorld = Bukkit.getWorld(baseName);
            if (targetWorld == null) {
                targetWorld = new WorldCreator(baseName).environment(World.Environment.NORMAL).type(WorldType.NORMAL).createWorld();
            }
            if (targetWorld == null) continue;
/*
            if(separateDims) {
                createAuxDimensions(plugin, baseName, targetWorld.getSeed(), null);
            }
 */
            for (String entry : team.getEntries()) {
                Player player = Bukkit.getPlayerExact(entry);
                if (player != null && player.isOnline()) {
                    Location spawn = targetWorld.getSpawnLocation();
                    player.teleport(spawn);
                    if (setRespawn) {
                        try {
                            player.setBedSpawnLocation(spawn, true); 
                        } catch (NoSuchMethodError ignored) {
                            
                        }
                    }
                    player.sendMessage(ChatColor.GREEN + "Arena ready: " + baseName + (setRespawn ? ChatColor.GRAY + " (respawn set)" : ""));
                }
            }
        }
    }

    private String sanitizeWorldName(String raw) {
        return raw.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private void createAuxDimensions(MonsterBattle plugin, String baseName, long seed, World.Environment originEnv) {
        if (!plugin.getConfig().getBoolean("separate-dimensions", true)) return;
        String netherName = baseName + "_nether";
        if (Bukkit.getWorld(netherName) == null) {
            new WorldCreator(netherName).environment(World.Environment.NETHER).seed(seed).createWorld();
        }
        if (plugin.getConfig().getBoolean("create-end-dimensions", true)) {
            String endName = baseName + "_the_end";
            if (Bukkit.getWorld(endName) == null) {
                new WorldCreator(endName).environment(World.Environment.THE_END).seed(seed).createWorld();
            }
        }
    }

    private void copyWorld(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path destDir = target.resolve(relative.toString());
                if (!Files.exists(destDir)) {
                    Files.createDirectories(destDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if (fileName.equalsIgnoreCase("session.lock") || fileName.equalsIgnoreCase("uid.dat")) {
                    return FileVisitResult.CONTINUE; 
                }
                Path relative = source.relativize(file);
                Path destFile = target.resolve(relative.toString());
                Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

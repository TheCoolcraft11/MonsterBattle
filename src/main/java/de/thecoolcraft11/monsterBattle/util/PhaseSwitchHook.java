package de.thecoolcraft11.monsterBattle.util;

import com.destroystokyo.paper.Title;
import de.thecoolcraft11.monsterBattle.MonsterBattle;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PhaseSwitchHook {

    private volatile boolean arenaCloneInProgress = false;

    public void newPhase(MonsterBattle plugin, GameState newPhase) {
        switch (newPhase) {
            case FARMING -> handleFarmingPhase(plugin);
            case BATTLE -> handleBattlePhase(plugin);
            case ENDED -> handleEnded(plugin);
            default -> {
            }
        }
    }


    private void handleFarmingPhase(MonsterBattle plugin) {
        boolean setFarmRespawn = plugin.getConfig().getBoolean("set-farm-respawn", true);
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Set<Team> teams = manager.getMainScoreboard().getTeams();
        if (teams.isEmpty()) return;

        long configuredSeed = plugin.getConfig().getLong("world-seed", -1L);
        if (configuredSeed == -1L && !Bukkit.getWorlds().isEmpty()) {
            configuredSeed = Bukkit.getWorlds().getFirst().getSeed();
        }

        plugin.getDataController().resetTeamKillsForTeams(teams.stream().map(Team::getName).collect(Collectors.toSet()));

        for (Team team : teams) {
            String baseName = "Farm_" + sanitizeWorldName(team.getName());
            World world = Bukkit.getWorld(baseName);
            if (world == null) {
                world = new WorldCreator(baseName)
                        .seed(configuredSeed)
                        .environment(World.Environment.NORMAL)
                        .createWorld();
            }
            if (world == null) continue;
            createAuxDimensions(plugin, baseName, configuredSeed, null);

            for (String entry : team.getEntries()) {
                Player p = Bukkit.getPlayerExact(entry);
                if (p == null || !p.isOnline()) continue;
                Location spawn = world.getSpawnLocation();
                p.teleport(spawn);
                if (setFarmRespawn) {
                    try {
                        p.setRespawnLocation(spawn, true);
                    } catch (NoSuchMethodError ignored) {
                    }
                }
                p.sendMessage(ChatColor.GREEN + "Farming phase started: " + ChatColor.YELLOW + baseName + ChatColor.GRAY + " (seed: " + configuredSeed + ")" + (setFarmRespawn ? ChatColor.DARK_GRAY + " [respawn set]" : ""));
            }
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

        plugin.getDataController().battlePhaseStarted(teams.stream().map(Team::getName).collect(Collectors.toSet()));


        for (Team team : teams) {
            plugin.getBossbarController().initialize(team.getName());
        }

        plugin.notifyBattleStarted();

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
            startBattleSpawnCountdown(plugin, teams);
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
                startBattleSpawnCountdown(plugin, teams);
                if (unloaded) new WorldCreator(templateName).createWorld();
                arenaCloneInProgress = false;
            });
        });
    }

    private void startBattleSpawnCountdown(MonsterBattle plugin, Set<Team> teams) {
        int countdown = plugin.getConfig().getInt("battle-spawn-countdown-seconds", 20);
        if (countdown < 0) countdown = 0;
        if (countdown == 0) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Battle started! Spawners active.");
            for (Team t : teams) new MonsterSpawner().start(plugin, t.getName());
            return;
        }
        final int total = countdown;
        for (int i = 0; i <= total; i++) {
            int delay = i * 20;
            int secondsLeft = total - i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getDataController().getGameState() != GameState.BATTLE) return;
                if (secondsLeft > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(ChatColor.RED + "Battle in", ChatColor.YELLOW + String.valueOf(secondsLeft) + ChatColor.GOLD + "s", 0, 20, 0);
                    }
                    if (secondsLeft % 5 == 0 || secondsLeft <= 5) {
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "Battle starting in " + secondsLeft + "s...");
                    }
                } else {
                    Bukkit.broadcastMessage(ChatColor.GREEN + "Battle started! Spawners active.");
                    for (Team t : teams) new MonsterSpawner().start(plugin, t.getName());
                }
            }, delay);
        }
    }

    private void loadAndTeleportArenas(MonsterBattle plugin, Set<Team> teams, boolean separateDims) {
        boolean setRespawn = plugin.getConfig().getBoolean("set-arena-respawn", true);
        for (Team team : teams) {
            String baseName = "Arena_" + sanitizeWorldName(team.getName());
            World targetWorld = Bukkit.getWorld(baseName);
            if (targetWorld == null) {
                targetWorld = new WorldCreator(baseName)
                        .environment(World.Environment.NORMAL)
                        .type(WorldType.NORMAL)
                        .createWorld();
            }
            if (targetWorld == null) continue;


            for (String entry : team.getEntries()) {
                Player p = Bukkit.getPlayerExact(entry);
                if (p == null || !p.isOnline()) continue;
                Location spawn = findValidSpawnFloor(targetWorld, targetWorld.getSpawnLocation().getBlockX(), targetWorld.getSpawnLocation().getBlockZ());
                p.teleport(spawn);
                if (setRespawn) {
                    try {
                        p.setRespawnLocation(spawn, true);
                    } catch (NoSuchMethodError ignored) {
                    }
                }
                p.sendMessage(ChatColor.GREEN + "Arena ready: " + baseName + (setRespawn ? ChatColor.GRAY + " (respawn set)" : ""));
            }
        }
    }


    public Location findValidSpawnFloor(World world, int x, int z) {
        Block block = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int y = block.getY();

        while (y > world.getMinHeight()) {
            Material mat = world.getBlockAt(x, y, z).getType();

            if (mat == Material.BARRIER) {
                y--;
                continue;
            }
            if (!mat.isSolid()) {
                y--;
                continue;
            }
            break;
        }

        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }


    private void handleEnded(MonsterBattle plugin) {
        var dc = plugin.getDataController();
        Map<String, Long> finish = new LinkedHashMap<>(dc.getTeamFinishTimes());
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(finish.entrySet());
        ordered.sort(Map.Entry.comparingByValue());
        String winner = ordered.isEmpty() ? null : ordered.getFirst().getKey();


        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Set<Team> teams = manager.getMainScoreboard().getTeams();
            for (Team team : teams) {
                plugin.getBossbarController().hide(team.getName());
                plugin.getBossbarController().cleanup(team.getName());
            }
        }

        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty()) mainWorld = Bukkit.getWorlds().getFirst();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (mainWorld != null) p.teleport(mainWorld.getSpawnLocation());
        }

        Team winnerTeam = winner != null && manager != null ? manager.getMainScoreboard().getTeam(winner) : null;

        int countdownSeconds = plugin.getConfig().getInt("end-countdown-seconds", 10);
        if (countdownSeconds < 0) countdownSeconds = 0;

        final String winnerCopy = winner;
        final Team winnerTeamCopy = winnerTeam;
        final Map<String, Long> finishTimes = finish;
        final List<Map.Entry<String, Long>> orderedCopy = ordered;

        if (countdownSeconds == 0) {
            revealWinner(plugin, winnerCopy, winnerTeamCopy, finishTimes);
            broadcastSummary(plugin, orderedCopy);
            return;
        }

        for (int i = 0; i <= countdownSeconds; i++) {
            int secondsLeft = countdownSeconds - i;
            int delay = i * 20;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (secondsLeft > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(new Title(ChatColor.AQUA + "Winner reveal in", "" + ChatColor.YELLOW + secondsLeft + ChatColor.GOLD + "s", 0, 20, 0));
                    }
                } else {
                    revealWinner(plugin, winnerCopy, winnerTeamCopy, finishTimes);
                    broadcastSummary(plugin, orderedCopy);
                }
            }, delay);
        }
    }

    private void revealWinner(MonsterBattle plugin, String winner, Team winnerTeam, Map<String, Long> finishTimes) {
        if (winner == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(ChatColor.RED + "Game Over", ChatColor.GRAY + "No winner", 10, 80, 20);
            }
            Bukkit.broadcastMessage(ChatColor.RED + "Game Over - No winner");
            return;
        }
        long ms = finishTimes.getOrDefault(winner, 0L);
        double seconds = ms / 1000.0;


        String playersList = "";
        if (winnerTeam != null) {
            List<String> names = new ArrayList<>(winnerTeam.getEntries());
            names.sort(String.CASE_INSENSITIVE_ORDER);
            playersList = String.join(", ", names);
        }
        String titleMain = ChatColor.GOLD + "WINNER: " + ChatColor.GREEN + winner;

        int captured = plugin.getDataController().getCapturedTotal(winner);
        double ratio = captured > 0 ? seconds / captured : 0.0;
        String ratioPart = captured > 0 ? ChatColor.LIGHT_PURPLE + String.format(" %.2f s/mob", ratio) : ChatColor.DARK_GRAY + " N/A";
        String subtitle = ChatColor.GRAY + String.format("%.2f s", seconds) + ChatColor.DARK_GRAY + " - " + ChatColor.AQUA + captured + ChatColor.GRAY + " captured" + ChatColor.DARK_GRAY + " | " + ratioPart;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(titleMain, subtitle, 10, 80, 20);
        }
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Winner: " + ChatColor.GOLD + winner + ChatColor.WHITE + " (" + String.format("%.2f s", seconds) + ", " + captured + " captured, " + (captured > 0 ? String.format("%.2f s/mob", ratio) : "N/A") + ")");
    }

    private void broadcastSummary(MonsterBattle plugin, List<Map.Entry<String, Long>> ordered) {
        Bukkit.broadcastMessage(ChatColor.AQUA + "===== Game Summary =====");
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Scoreboard unavailable.");
            Bukkit.broadcastMessage(ChatColor.AQUA + "========================");
            return;
        }
        var scoreboardTeams = new ArrayList<>(sm.getMainScoreboard().getTeams());
        if (scoreboardTeams.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "No teams present.");
            Bukkit.broadcastMessage(ChatColor.AQUA + "========================");
            return;
        }


        Set<String> finishedNames = ordered.stream().map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new));
        int rank = 1;
        for (Map.Entry<String, Long> e : ordered) {
            String teamName = e.getKey();
            double seconds = e.getValue() / 1000.0;
            Team t = sm.getMainScoreboard().getTeam(teamName);
            String playersList = "";
            if (t != null) {
                List<String> names = new ArrayList<>(t.getEntries());
                names.sort(String.CASE_INSENSITIVE_ORDER);
                playersList = String.join(", ", names);
            }
            int captured = plugin.getDataController().getCapturedTotal(teamName);
            double ratio = captured > 0 ? seconds / captured : 0.0;
            String ratioStr = captured > 0 ? ChatColor.LIGHT_PURPLE + String.format("%.2f s/mob", ratio) : ChatColor.DARK_GRAY + "N/A";
            Bukkit.broadcastMessage(ChatColor.YELLOW + "#" + (rank++) + " " + ChatColor.GOLD + teamName + (playersList.isEmpty() ? "" : ChatColor.YELLOW + " (" + playersList + ")") + ChatColor.WHITE + " - " + String.format("%.2f s", seconds) + ChatColor.DARK_GRAY + " / " + ChatColor.AQUA + captured + ChatColor.GRAY + " mobs" + ChatColor.DARK_GRAY + " | " + ratioStr);
        }


        for (Team t : scoreboardTeams) {
            if (finishedNames.contains(t.getName())) continue;
            String teamName = t.getName();
            List<String> names = new ArrayList<>(t.getEntries());
            names.sort(String.CASE_INSENSITIVE_ORDER);
            String playersList = String.join(", ", names);
            int captured = plugin.getDataController().getCapturedTotal(teamName);
            String ratioStr = captured > 0 ? ChatColor.LIGHT_PURPLE + "? s/mob" : ChatColor.DARK_GRAY + "N/A";
            Bukkit.broadcastMessage(ChatColor.YELLOW + "#" + (rank++) + " " + ChatColor.GOLD + teamName + (playersList.isEmpty() ? "" : ChatColor.YELLOW + " (" + playersList + ")") + ChatColor.WHITE + " - " + ChatColor.RED + "DNF" + ChatColor.DARK_GRAY + " / " + ChatColor.AQUA + captured + ChatColor.GRAY + " mobs" + ChatColor.DARK_GRAY + " | " + ratioStr);
        }
        Bukkit.broadcastMessage(ChatColor.AQUA + "========================");
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
                if (!Files.exists(destDir)) Files.createDirectories(destDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                Path relative = source.relativize(file);
                Path destFile = target.resolve(relative.toString());
                Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}


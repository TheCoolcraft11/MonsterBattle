package de.thecoolcraft11.monsterBattle.util;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
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
            createAuxDimensions(plugin, baseName, configuredSeed);

            for (String entry : team.getEntries()) {
                Player p = Bukkit.getPlayerExact(entry);
                if (p == null || !p.isOnline()) continue;
                Location spawn = world.getSpawnLocation();
                p.teleport(spawn);
                resetPlayerStats(p);
                resetPlayerInv(p);
                if (setFarmRespawn) {
                    try {
                        p.setRespawnLocation(spawn, true);
                    } catch (NoSuchMethodError ignored) {
                    }
                }
                p.sendMessage(Component.text()
                        .append(Component.text("Farming phase started: ", NamedTextColor.GREEN))
                        .append(Component.text(baseName, NamedTextColor.YELLOW))
                        .append(setFarmRespawn ? Component.text(" [respawn set]", NamedTextColor.DARK_GRAY) : Component.empty())
                        .build());
            }
        }
    }


    private void handleBattlePhase(MonsterBattle plugin) {
        if (arenaCloneInProgress) {
            plugin.getLogger().warning("Battle phase cloning already in progress. Ignoring additional request.");
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Set<Team> teams = manager.getMainScoreboard().getTeams();
        if (teams.isEmpty()) return;

        plugin.getDataController().battlePhaseStarted(teams.stream().map(Team::getName).collect(Collectors.toSet()));


        for (Team team : teams) {
            plugin.getBossbarController().initialize(team.getName());
        }

        plugin.notifyBattleStarted();

        String templateName = plugin.getConfig().getString("arena-template-world", "Arena");
        World template = Bukkit.getWorld(templateName);
        if (template == null) {
            plugin.getLogger().warning("Battle phase aborted: template world '" + templateName + "' is not loaded.");
            return;
        }

        if (!template.getPlayers().isEmpty()) {
            World fallback = Bukkit.getWorlds().getFirst();
            for (Player p : new ArrayList<>(template.getPlayers())) {
                p.teleport(fallback.getSpawnLocation());
                resetPlayerStats(p);
                p.sendMessage(Component.text("Arena template resetting, you were moved.", NamedTextColor.YELLOW));
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
        String arenaPrefix = plugin.getArenaPrefix();
        for (Team t : teams) {
            String baseName = arenaPrefix + sanitizeWorldName(t.getName());
            if (!Files.exists(serverRoot.resolve(baseName))) creationNeeded.add(t);
        }

        for (Team t : creationNeeded) {
            for (String entry : t.getEntries()) {
                Player pl = Bukkit.getPlayerExact(entry);
                if (pl != null) pl.sendMessage(Component.text("Preparing your arena world...", NamedTextColor.GRAY));
            }
        }

        if (creationNeeded.isEmpty()) {
            loadAndTeleportArenas(plugin, teams);
            startBattleSpawnCountdown(plugin, teams);
            if (unloaded) new WorldCreator(templateName).createWorld();
            return;
        }

        arenaCloneInProgress = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AtomicInteger success = new AtomicInteger();
            for (Team t : creationNeeded) {
                String baseName = arenaPrefix + sanitizeWorldName(t.getName());
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
                loadAndTeleportArenas(plugin, teams);
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
            Bukkit.getServer().sendMessage(Component.text("Battle started! Spawners active.", NamedTextColor.GREEN));
            for (Team t : teams) plugin.getMonsterSpawner().start(plugin, t.getName());
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
                        p.showTitle(Title.title(
                                Component.text("Battle in", NamedTextColor.RED),
                                Component.text()
                                        .append(Component.text(String.valueOf(secondsLeft), NamedTextColor.YELLOW))
                                        .append(Component.text("s", NamedTextColor.GOLD))
                                        .build(),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                        ));
                    }
                    if (secondsLeft % 5 == 0 || secondsLeft <= 5) {
                        Bukkit.getServer().sendMessage(Component.text("Battle starting in " + secondsLeft + "s...", NamedTextColor.YELLOW));
                    }
                } else {
                    Bukkit.getServer().sendMessage(Component.text("Battle started! Spawners active.", NamedTextColor.GREEN));
                    for (Team t : teams) plugin.getMonsterSpawner().start(plugin, t.getName());
                }
            }, delay);
        }
    }

    private void loadAndTeleportArenas(MonsterBattle plugin, Set<Team> teams) {
        boolean setRespawn = plugin.getConfig().getBoolean("set-arena-respawn", true);
        String arenaPrefix = plugin.getArenaPrefix();
        for (Team team : teams) {
            String baseName = arenaPrefix + sanitizeWorldName(team.getName());
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
                p.sendMessage(Component.text()
                        .append(Component.text("Arena ready: " + baseName, NamedTextColor.GREEN))
                        .append(setRespawn ? Component.text(" (respawn set)", NamedTextColor.GRAY) : Component.empty())
                        .build());
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
        Set<Team> teams = manager.getMainScoreboard().getTeams();
        for (Team team : teams) {
            plugin.getBossbarController().hide(team.getName());
            plugin.getBossbarController().cleanup(team.getName());
        }

        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty()) mainWorld = Bukkit.getWorlds().getFirst();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            if (mainWorld != null) p.teleport(mainWorld.getSpawnLocation());
        }

        Team winnerTeam = winner != null ? manager.getMainScoreboard().getTeam(winner) : null;

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
                        p.showTitle(Title.title(
                                Component.text("Winner reveal in", NamedTextColor.AQUA),
                                Component.text()
                                        .append(Component.text(String.valueOf(secondsLeft), NamedTextColor.YELLOW))
                                        .append(Component.text("s", NamedTextColor.GOLD))
                                        .build(),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                        ));
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
                p.showTitle(Title.title(
                        Component.text("Game Over", NamedTextColor.RED),
                        Component.text("No winner", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))
                ));
            }
            Bukkit.getServer().sendMessage(Component.text("Game Over - No winner", NamedTextColor.RED));
            return;
        }
        long ms = finishTimes.getOrDefault(winner, 0L);
        double seconds = ms / 1000.0;


        if (winnerTeam != null) {
            List<String> names = new ArrayList<>(winnerTeam.getEntries());
            names.sort(String.CASE_INSENSITIVE_ORDER);
        }

        int captured = plugin.getDataController().getCapturedTotal(winner);
        double ratio = captured > 0 ? seconds / captured : 0.0;

        Component titleMain = Component.text()
                .append(Component.text("WINNER: ", NamedTextColor.GOLD))
                .append(Component.text(winner, NamedTextColor.GREEN))
                .build();

        Component subtitle = Component.text()
                .append(Component.text(String.format("%.2f s", seconds), NamedTextColor.GRAY))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.valueOf(captured), NamedTextColor.AQUA))
                .append(Component.text(" captured", NamedTextColor.GRAY))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(captured > 0 ?
                        Component.text(String.format(" %.2f s/mob", ratio), NamedTextColor.LIGHT_PURPLE) :
                        Component.text(" N/A", NamedTextColor.DARK_GRAY))
                .build();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(titleMain, subtitle, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))));
        }

        Bukkit.getServer().sendMessage(Component.text()
                .append(Component.text("Winner: ", NamedTextColor.YELLOW))
                .append(Component.text(winner, NamedTextColor.GOLD))
                .append(Component.text(" (" + String.format("%.2f s", seconds) + ", " + captured + " captured, " +
                        (captured > 0 ? String.format("%.2f s/mob", ratio) : "N/A") + ")", NamedTextColor.WHITE))
                .build());
    }

    private void broadcastSummary(MonsterBattle plugin, List<Map.Entry<String, Long>> ordered) {
        Bukkit.getServer().sendMessage(Component.text("===== Game Summary =====", NamedTextColor.AQUA));
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        var scoreboardTeams = new ArrayList<>(sm.getMainScoreboard().getTeams());
        if (scoreboardTeams.isEmpty()) {
            Bukkit.getServer().sendMessage(Component.text("No teams present.", NamedTextColor.GRAY));
            Bukkit.getServer().sendMessage(Component.text("========================", NamedTextColor.AQUA));
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

            Component message = Component.text()
                    .append(Component.text("#" + (rank++), NamedTextColor.YELLOW))
                    .append(Component.text(" "))
                    .append(Component.text(teamName, NamedTextColor.GOLD))
                    .append(playersList.isEmpty() ? Component.empty() :
                            Component.text(" (" + playersList + ")", NamedTextColor.YELLOW))
                    .append(Component.text(" - " + String.format("%.2f s", seconds), NamedTextColor.WHITE))
                    .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.valueOf(captured), NamedTextColor.AQUA))
                    .append(Component.text(" mobs", NamedTextColor.GRAY))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(captured > 0 ?
                            Component.text(String.format("%.2f s/mob", ratio), NamedTextColor.LIGHT_PURPLE) :
                            Component.text("N/A", NamedTextColor.DARK_GRAY))
                    .build();
            Bukkit.getServer().sendMessage(message);
        }


        for (Team t : scoreboardTeams) {
            if (finishedNames.contains(t.getName())) continue;
            String teamName = t.getName();
            List<String> names = new ArrayList<>(t.getEntries());
            names.sort(String.CASE_INSENSITIVE_ORDER);
            String playersList = String.join(", ", names);
            int captured = plugin.getDataController().getCapturedTotal(teamName);

            Component message = Component.text()
                    .append(Component.text("#" + (rank++), NamedTextColor.YELLOW))
                    .append(Component.text(" "))
                    .append(Component.text(teamName, NamedTextColor.GOLD))
                    .append(playersList.isEmpty() ? Component.empty() :
                            Component.text(" (" + playersList + ")", NamedTextColor.YELLOW))
                    .append(Component.text(" - ", NamedTextColor.WHITE))
                    .append(Component.text("DNF", NamedTextColor.RED))
                    .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.valueOf(captured), NamedTextColor.AQUA))
                    .append(Component.text(" mobs", NamedTextColor.GRAY))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(captured > 0 ?
                            Component.text("? s/mob", NamedTextColor.LIGHT_PURPLE) :
                            Component.text("N/A", NamedTextColor.DARK_GRAY))
                    .build();
            Bukkit.getServer().sendMessage(message);
        }
        Bukkit.getServer().sendMessage(Component.text("========================", NamedTextColor.AQUA));
    }

    private void resetPlayerStats(Player player) {
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getDefaultValue());
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
    }

    private void resetPlayerInv(Player player) {
        player.getInventory().clear();
        ItemStack[] empty = new ItemStack[3];
        player.getInventory().setArmorContents(empty);
        player.getInventory().setExtraContents(empty);
        player.updateInventory();
        player.setTotalExperience(0);
        player.setLevel(0);
    }

    private String sanitizeWorldName(String raw) {
        return raw.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private void createAuxDimensions(MonsterBattle plugin, String baseName, long seed) {
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
                Path relative = source.relativize(file);
                Path destFile = target.resolve(relative.toString());
                Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}


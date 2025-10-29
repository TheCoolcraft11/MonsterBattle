package de.thecoolcraft11.monsterBattle.util;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

public class BossbarController {
    private final MonsterBattle plugin;

    public static final String BOSSBAR_ID = "monsterbattle_bossbar_";

    public BossbarController(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    public void initialize(String team) {
        try {
            cleanup(team);
            String sanitizedTeam = team.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
            plugin.getLogger().info("[BossBar] Initializing BossBar for team: " + team + " (sanitized: " + sanitizedTeam + ")");

            BossBar bossBar = plugin.getServer().createBossBar(
                    NamespacedKey.minecraft(BOSSBAR_ID + sanitizedTeam),
                    "Preparing Arena...",
                    BarColor.RED,
                    BarStyle.SEGMENTED_20
            );
            bossBar.setVisible(true);
            bossBar.setProgress(1.0);

            plugin.getLogger().info("[BossBar] BossBar created: " + bossBar.getTitle() + " with ID: " + BOSSBAR_ID + sanitizedTeam);

            Team teamObj = plugin.getServer().getScoreboardManager().getMainScoreboard().getTeam(team);
            if (teamObj != null) {
                plugin.getLogger().info("[BossBar] Found team object: " + teamObj.getName() + " with " + teamObj.getEntries().size() + " entries");
                int playersAdded = 0;
                for (String entry : teamObj.getEntries()) {
                    Player player = Bukkit.getPlayerExact(entry);
                    if (player != null && player.isOnline()) {
                        bossBar.addPlayer(player);
                        playersAdded++;
                        plugin.getLogger().info("[BossBar] Added player: " + player.getName());
                    }
                }
                plugin.getLogger().info("[BossBar] Total players added to BossBar: " + playersAdded);
            } else {
                plugin.getLogger().warning("[BossBar] Team object not found for: " + team);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[BossBar] Error initializing BossBar for team " + team + ": " + e.getMessage());
        }
    }

    public void updateProgress(String team, double mobsKilled, double totalMobs, double spawnedMobs) {
        String sanitizedTeam = team.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        BossBar bossBar = plugin.getServer().getBossBar(NamespacedKey.minecraft(BOSSBAR_ID + sanitizedTeam));
        if (bossBar != null) {


            double progress = totalMobs > 0 ? Math.max(0.0, Math.min(1.0, (totalMobs - mobsKilled) / totalMobs)) : 0.0;
            bossBar.setProgress(progress);

            int remaining = (int) (totalMobs - mobsKilled);
            int currentlyAlive = (int) (spawnedMobs - mobsKilled);
            int notSpawned = (int) (totalMobs - spawnedMobs);

            String title = String.format("[%s] Remaining: %d/%d • Alive: %d • Not Spawned: %d",
                    team, remaining, (int) totalMobs, currentlyAlive, notSpawned);
            bossBar.setTitle(title);
        } else {
            plugin.getLogger().warning("[BossBar] Could not find BossBar for team: " + team + " (sanitized: " + sanitizedTeam + ")");
        }
    }

    public void finish(String team, double time) {
        String sanitizedTeam = team.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        BossBar bossBar = plugin.getServer().getBossBar(NamespacedKey.minecraft(BOSSBAR_ID + sanitizedTeam));
        if (bossBar != null) {
            bossBar.setProgress(0.0);
            bossBar.setColor(BarColor.GREEN);
            String title = String.format("[%s] All mobs defeated! Time: %.2f s", team, time / 1000.0);
            bossBar.setTitle(title);
        }
    }

    public void addSpectators(String team, Team spectatorTeam) {
        String sanitizedTeam = team.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        BossBar bossBar = plugin.getServer().getBossBar(NamespacedKey.minecraft(BOSSBAR_ID + sanitizedTeam));
        if (bossBar != null && spectatorTeam != null) {
            for (String entry : spectatorTeam.getEntries()) {
                Player player = Bukkit.getPlayerExact(entry);
                if (player != null && player.isOnline()) {
                    bossBar.addPlayer(player);
                    plugin.getLogger().info("[BossBar] Added spectator " + player.getName() + " to team " + team + "'s bossbar");
                }
            }
        }
    }

    public void hide(String team) {
        String sanitizedTeam = team.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        plugin.getLogger().info("[BossBar] Hiding BossBar for team: " + team);
        BossBar bossBar = plugin.getServer().getBossBar(NamespacedKey.minecraft(BOSSBAR_ID + sanitizedTeam));
        if (bossBar != null) {
            bossBar.setVisible(false);
            bossBar.removeAll();
            plugin.getLogger().info("[BossBar] BossBar hidden for team: " + team);
        } else {
            plugin.getLogger().warning("[BossBar] Could not find BossBar to hide for team: " + team);
        }
    }

    public void cleanup(String team) {
        String sanitizedTeam = team.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        BossBar bossBar = plugin.getServer().getBossBar(NamespacedKey.minecraft(BOSSBAR_ID + sanitizedTeam));
        if (bossBar != null) {
            bossBar.removeAll();
            try {
                plugin.getServer().removeBossBar(NamespacedKey.minecraft(BOSSBAR_ID + sanitizedTeam));
            } catch (Exception ignored) {

            }
        }
    }

    public void removeAll() {
        plugin.getLogger().info("[BossBar] Removing all boss bars...");
        var scoreboardManager = plugin.getServer().getScoreboardManager();
        for (Team team : scoreboardManager.getMainScoreboard().getTeams()) {
            cleanup(team.getName());
        }


        try {
            var iterator = plugin.getServer().getBossBars();
            while (iterator.hasNext()) {
                KeyedBossBar bar = iterator.next();
                if (bar.getKey().getKey().startsWith(BOSSBAR_ID)) {
                    plugin.getLogger().info("[BossBar] Removing orphaned boss bar: " + bar.getKey().getKey());
                    bar.removeAll();
                    plugin.getServer().removeBossBar(bar.getKey());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[BossBar] Error cleaning up orphaned boss bars: " + e.getMessage());
        }
        plugin.getLogger().info("[BossBar] All boss bars removed.");
    }
}

package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;

public class BattleMobDeathListener implements Listener {

    private final MonsterBattle plugin;

    public BattleMobDeathListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return;
        LivingEntity living = event.getEntity();
        var dc = plugin.getDataController();
        String team = dc.getTeamForMonster(living.getUniqueId());
        if (team == null) return;
        dc.registerMonsterDeath(living.getUniqueId());


        int killTimeReduction = plugin.getConfig().getInt("monster-spawner.kill-time-reduction-ticks", 20);
        if (killTimeReduction > 0) {
            plugin.getMonsterSpawner().reduceSpawnTimer(team, killTimeReduction);
        }

        int remaining = dc.getRemainingForTeam(team);

        boolean showRemaining = plugin.getConfig().getBoolean("battle-remaining-on-kill", true);
        boolean privateFinish = plugin.getConfig().getBoolean("battle-private-finish-message", true);

        ScoreboardManager sm = plugin.getServer().getScoreboardManager();
        Team t = sm.getMainScoreboard().getTeam(team);


        boolean hasWaitingMobs = hasWaitingMobsForTeam(dc, sm, team);

        if (t != null) {
            for (String entry : t.getEntries()) {
                Player p = Bukkit.getPlayerExact(entry);
                if (p == null) continue;
                if (remaining > 0) {
                    if (showRemaining) {
                        p.sendMessage(Component.text()
                                .append(Component.text("Remaining arena mobs: ", NamedTextColor.AQUA))
                                .append(Component.text(String.valueOf(remaining), NamedTextColor.YELLOW))
                                .build());
                    }
                } else if (!hasWaitingMobs && privateFinish) {

                    long ms = dc.getTeamFinishTimes().getOrDefault(team, 0L);
                    double seconds = ms / 1000.0;
                    p.sendMessage(Component.text()
                            .append(Component.text("All arena mobs defeated!", NamedTextColor.GOLD))
                            .append(Component.text(" (" + String.format("%.2f s", seconds) + ")", NamedTextColor.GRAY))
                            .build());
                    p.sendMessage(Component.text("(Hidden from other teams until game end.)", NamedTextColor.DARK_GRAY));
                }
            }
        }


        updateBossbarForTeam(team);


        if (remaining == 0 && !hasWaitingMobs && dc.isTeamFinished(team)) {
            long ms = dc.getTeamFinishTimes().getOrDefault(team, 0L);
            plugin.getBossbarController().finish(team, ms);
        }
    }

    private boolean hasWaitingMobsForTeam(de.thecoolcraft11.monsterBattle.util.DataController dc, ScoreboardManager sm, String teamName) {
        Set<Team> allTeams = new HashSet<>(sm.getMainScoreboard().getTeams());
        Team thisTeam = sm.getMainScoreboard().getTeam(teamName);
        if (thisTeam == null) return false;

        allTeams.remove(thisTeam);
        for (Team opponentTeam : allTeams) {
            if (!dc.getKillsForTeam(opponentTeam.getName()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void updateBossbarForTeam(String teamName) {
        var dc = plugin.getDataController();
        var sbManager = plugin.getServer().getScoreboardManager();

        Set<Team> allTeams = new HashSet<>(sbManager.getMainScoreboard().getTeams());
        Team thisTeam = sbManager.getMainScoreboard().getTeam(teamName);
        if (thisTeam == null) return;
        allTeams.remove(thisTeam);


        int totalMobs = 0;
        for (Team team : allTeams) {
            totalMobs += dc.getCapturedTotal(team.getName());
        }


        int spawnedMobs = dc.getRemainingForTeam(teamName);


        int waitingToSpawn = 0;
        for (Team team : allTeams) {
            waitingToSpawn += dc.getKillsForTeam(team.getName()).size();
        }
        int actualSpawned = totalMobs - waitingToSpawn;
        int mobsKilled = actualSpawned - spawnedMobs;

        plugin.getBossbarController().updateProgress(teamName, mobsKilled, totalMobs, actualSpawned);
    }
}
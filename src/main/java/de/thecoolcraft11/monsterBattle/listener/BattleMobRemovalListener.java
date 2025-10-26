package de.thecoolcraft11.monsterBattle.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;


public class BattleMobRemovalListener implements Listener {

    private final MonsterBattle plugin;

    public BattleMobRemovalListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    private boolean isActiveBattleMob(Entity e) {
        if (e == null) return false;
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return false;
        return plugin.getDataController().getTeamForMonster(e.getUniqueId()) != null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return;
        var entity = event.getEntity();
        if (!isActiveBattleMob(entity)) return;

        var dc = plugin.getDataController();
        String team = dc.getTeamForMonster(entity.getUniqueId());
        if (team == null) return;

        dc.registerMonsterDeath(entity.getUniqueId());


        handleMobDeath(team);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitiesUnload(org.bukkit.event.world.EntitiesUnloadEvent event) {
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return;

        event.getEntities().forEach(ent -> {
            if (isActiveBattleMob(ent)) {
                var dc = plugin.getDataController();
                String team = dc.getTeamForMonster(ent.getUniqueId());
                dc.registerMonsterDeath(ent.getUniqueId());
                if (team != null) {
                    handleMobDeath(team);
                }
            }
        });
    }

    private void handleMobDeath(String teamName) {
        var dc = plugin.getDataController();
        int remaining = dc.getRemainingForTeam(teamName);

        boolean showRemaining = plugin.getConfig().getBoolean("battle-remaining-on-kill", true);
        boolean privateFinish = plugin.getConfig().getBoolean("battle-private-finish-message", true);

        ScoreboardManager sm = plugin.getServer().getScoreboardManager();
        Team t = sm.getMainScoreboard().getTeam(teamName);

        boolean hasWaitingMobs = hasWaitingMobsForTeam(dc, sm, teamName);

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
                    long ms = dc.getTeamFinishTimes().getOrDefault(teamName, 0L);
                    double seconds = ms / 1000.0;
                    p.sendMessage(Component.text()
                            .append(Component.text("All arena mobs defeated!", NamedTextColor.GOLD))
                            .append(Component.text(" (" + String.format("%.2f s", seconds) + ")", NamedTextColor.GRAY))
                            .build());
                    p.sendMessage(Component.text("(Hidden from other teams until game end.)", NamedTextColor.DARK_GRAY));
                }
            }
        }


        updateBossbarForTeam(teamName);


        if (remaining == 0 && !hasWaitingMobs && dc.isTeamFinished(teamName)) {
            long ms = dc.getTeamFinishTimes().getOrDefault(teamName, 0L);
            plugin.getBossbarController().finish(teamName, ms);
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


    public void debugSweep() {
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return;
        int removed = 0;
        var dc = plugin.getDataController();
        Set<String> affectedTeams = new HashSet<>();

        for (var entry : dc.getActiveMonstersView().entrySet()) {
            for (var id : entry.getValue()) {
                Entity e = Bukkit.getEntity(id);
                if (e == null || e.isDead() || !e.isValid()) {
                    String team = dc.getTeamForMonster(id);
                    dc.registerMonsterDeath(id);
                    if (team != null) {
                        affectedTeams.add(team);
                    }
                    removed++;
                }
            }
        }


        for (String team : affectedTeams) {
            handleMobDeath(team);
        }

        if (removed > 0 && plugin.getConfig().getBoolean("battle-integrity-scan.debug-log", false)) {
            plugin.getLogger().info("Removal listener debug sweep pruned " + removed + " stale tracked mobs.");
        }
    }
}


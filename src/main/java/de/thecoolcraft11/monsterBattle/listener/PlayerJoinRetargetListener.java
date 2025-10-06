package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scoreboard.Team;

import java.util.UUID;


public class PlayerJoinRetargetListener implements Listener {

    private final MonsterBattle plugin;

    public PlayerJoinRetargetListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return;
        scheduleRetargetForPlayer(event.getPlayer(), 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return;

        scheduleRetargetForPlayer(event.getPlayer(), 30L);
    }

    private void scheduleRetargetForPlayer(Player player, long delayTicks) {
        var sb = Bukkit.getScoreboardManager();
        Team team = sb.getMainScoreboard().getEntryTeam(player.getName());
        if (team == null) return;
        String teamName = team.getName();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.getDataController().getGameState() != GameState.BATTLE) return;
            var activeView = plugin.getDataController().getActiveMonstersView();
            var ids = activeView.get(teamName);
            if (ids == null || ids.isEmpty()) return;
            for (UUID id : ids) {
                Entity e = Bukkit.getEntity(id);
                if (!(e instanceof Mob mob)) continue;
                if (mob.isDead() || mob.getWorld() != player.getWorld()) continue;

                var current = mob.getTarget();
                boolean needsTarget = !(current instanceof Player cp && cp.isOnline() && !cp.isDead() && cp.getWorld() == mob.getWorld());
                if (needsTarget) {
                    try {
                        mob.setTarget(player);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, delayTicks);
    }
}

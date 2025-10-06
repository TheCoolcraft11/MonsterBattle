package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import de.thecoolcraft11.monsterBattle.util.MobSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scoreboard.Team;

import java.util.List;

public class MobKillListener implements Listener {

    private final MonsterBattle plugin;

    public MobKillListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (plugin.getDataController().getGameState() != GameState.FARMING) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity() instanceof Player) return;
        LivingEntity living = event.getEntity();

        plugin.getServer().getScoreboardManager();
        Team team = plugin.getServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(killer);
        if (team != null) {
            plugin.getDataController().addKillForTeam(team.getName(), living);
            if (plugin.getConfig().getBoolean("farming-capture-feedback", true)) {
                List<MobSnapshot> snapshots = plugin.getDataController().getKillsForTeam(team.getName());
                int total = snapshots.size();
                EntityType type = living.getType();
                int ofType = 0;
                for (MobSnapshot snap : snapshots) {
                    if (snap.getType() == type) ofType++;
                }
                killer.sendMessage(
                        ChatColor.AQUA + "Captured: " + ChatColor.YELLOW + type.name() + ChatColor.GRAY + " (" +
                                ChatColor.GOLD + total + ChatColor.GRAY + " Total, " +
                                ChatColor.GOLD + ofType + ChatColor.GRAY + " " + type.name() + ")"
                );
            }
        }
    }
}
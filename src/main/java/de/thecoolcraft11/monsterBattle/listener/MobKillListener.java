package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import de.thecoolcraft11.monsterBattle.util.MobSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EnderDragon;
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
        if (event.getEntity() instanceof EnderDragon) return;
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
                killer.sendMessage(Component.text()
                        .append(Component.text("Captured: ", NamedTextColor.AQUA))
                        .append(Component.text(type.name(), NamedTextColor.YELLOW))
                        .append(Component.text(" (", NamedTextColor.GRAY))
                        .append(Component.text(String.valueOf(total), NamedTextColor.GOLD))
                        .append(Component.text(" Total, ", NamedTextColor.GRAY))
                        .append(Component.text(String.valueOf(ofType), NamedTextColor.GOLD))
                        .append(Component.text(" " + type.name() + ")", NamedTextColor.GRAY))
                        .build());
            }
        }
    }
}
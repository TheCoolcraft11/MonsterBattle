package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scoreboard.Team;

public class MobKillListener implements Listener {

    private final MonsterBattle plugin;

    public MobKillListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (plugin.getDataController().getGameState() != GameState.FARMING) return;
        if (event.getEntity().getKiller() != null) {
            System.out.println("Mob killed by player: " + event.getEntity().getKiller().getName());
            if (event.getEntity() instanceof Player) return;
            Player killer = event.getEntity().getKiller();

            Team team = plugin.getServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(killer);
            if (team != null) {
                plugin.getDataController().addKillForTeam(team.getName(), event.getEntity());
            }
        }
    }

}

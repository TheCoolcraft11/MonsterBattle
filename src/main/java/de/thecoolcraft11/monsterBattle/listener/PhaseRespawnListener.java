package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scoreboard.Team;


public class PhaseRespawnListener implements Listener {

    private final MonsterBattle plugin;

    public PhaseRespawnListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        GameState state = plugin.getDataController().getGameState();
        if (state != GameState.FARMING && state != GameState.BATTLE) return;

        Player player = event.getPlayer();
        var sbMan = Bukkit.getScoreboardManager();
        Team team = sbMan.getMainScoreboard().getEntryTeam(player.getName());
        if (team == null) return;

        boolean forceFarm = plugin.getConfig().getBoolean("set-farm-respawn", true);
        boolean forceArena = plugin.getConfig().getBoolean("set-arena-respawn", true);
        if (state == GameState.FARMING && !forceFarm) return;
        if (state == GameState.BATTLE && !forceArena) return;

        PlayerRespawnEvent.RespawnReason reason = event.getRespawnReason();
        if (reason == PlayerRespawnEvent.RespawnReason.END_PORTAL) {
            return;
        }

        String basePrefix = state == GameState.FARMING ? "Farm_" : plugin.getArenaPrefix();
        String baseWorldName = basePrefix + sanitize(team.getName());

        World currentWorld = event.getRespawnLocation().getWorld();
        if (currentWorld != null && currentWorld.getName().equals(baseWorldName)) return;

        World baseWorld = Bukkit.getWorld(baseWorldName);
        if (baseWorld == null) {
            baseWorld = new WorldCreator(baseWorldName)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL)
                    .createWorld();
        }
        if (baseWorld == null) return;

        Location spawn = baseWorld.getSpawnLocation().add(0, 1, 0);
        event.setRespawnLocation(spawn);
        player.sendMessage(Component.text()
                .append(Component.text("Respawn redirected to phase world: ", NamedTextColor.GRAY))
                .append(Component.text(baseWorldName, NamedTextColor.GREEN))
                .build());
    }

    private String sanitize(String raw) {
        return raw.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}

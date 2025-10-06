package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

public class BattleChunkListener implements Listener {

    private final MonsterBattle plugin;

    public BattleChunkListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return;
        if (!plugin.getConfig().getBoolean("battle-chunk-loading.enabled", true)) return;
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        boolean tracked = plugin.getDataController().isBattleChunkTracked(worldName, cx, cz);
        if (!tracked) {
            boolean containsBattleMob = false;
            var data = plugin.getDataController();
            for (var e : chunk.getEntities()) {
                if (data.getTeamForMonster(e.getUniqueId()) != null) {
                    containsBattleMob = true;
                    break;
                }
            }
            if (containsBattleMob) {
                int limit = plugin.getConfig().getInt("battle-chunk-loading.max-forced-chunks-per-world", 256);
                data.addBattleChunkIfUnderLimit(worldName, cx, cz, limit);
                tracked = true;
            }
        }
        if (tracked) {
            try {
                if (!chunk.isForceLoaded()) chunk.setForceLoaded(true);
            } catch (Throwable ignored) {
            }
            
        }
    }
}

package de.thecoolcraft11.monsterBattle.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;


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
        plugin.getDataController().registerMonsterDeath(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitiesUnload(org.bukkit.event.world.EntitiesUnloadEvent event) {
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return;
        
        event.getEntities().forEach(ent -> {
            if (isActiveBattleMob(ent)) {
                
                plugin.getDataController().registerMonsterDeath(ent.getUniqueId());
            }
        });
    }

    
    public void debugSweep() {
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return;
        int removed = 0;
        var dc = plugin.getDataController();
        for (var entry : dc.getActiveMonstersView().entrySet()) {
            for (var id : entry.getValue()) {
                Entity e = Bukkit.getEntity(id);
                if (e == null || e.isDead() || !e.isValid()) {
                    dc.registerMonsterDeath(id);
                    removed++;
                }
            }
        }
        if (removed > 0 && plugin.getConfig().getBoolean("battle-integrity-scan.debug-log", false)) {
            plugin.getLogger().info("Removal listener debug sweep pruned " + removed + " stale tracked mobs.");
        }
    }
}


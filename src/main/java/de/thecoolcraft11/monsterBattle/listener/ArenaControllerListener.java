package de.thecoolcraft11.monsterBattle.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.*;

public class ArenaControllerListener implements Listener {

    private boolean isInArena(org.bukkit.entity.Entity entity) {
        if (entity == null || entity.getWorld() == null) return false;
        String worldName = entity.getWorld().getName();
        return worldName.startsWith("Arena_") || entity.getScoreboardTags().contains("monsterbattle_arena");
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (isInArena(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (isInArena(event.getEntity())) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (isInArena(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (isInArena(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getWorld().getName().startsWith("Arena_")) {
            
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
            if (reason == CreatureSpawnEvent.SpawnReason.NATURAL ||
                    reason == CreatureSpawnEvent.SpawnReason.JOCKEY ||
                    reason == CreatureSpawnEvent.SpawnReason.MOUNT ||
                    reason == CreatureSpawnEvent.SpawnReason.NETHER_PORTAL ||
                    reason == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS ||
                    reason == CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE ||
                    reason == CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION ||
                    reason == CreatureSpawnEvent.SpawnReason.INFECTION ||
                    reason == CreatureSpawnEvent.SpawnReason.PATROL ||
                    reason == CreatureSpawnEvent.SpawnReason.RAID) {
                event.setCancelled(true);
            }
        }
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getBlock().getWorld().getName().startsWith("Arena_")) {
            
            if (event.getSource().getType().toString().contains("FIRE")) {
                event.setCancelled(true);
            }
        }
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (event.getBlock().getWorld().getName().startsWith("Arena_")) {
            event.setCancelled(true);
        }
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().getName().startsWith("Arena_")) {
            event.setDropItems(false);
        }
    }
}

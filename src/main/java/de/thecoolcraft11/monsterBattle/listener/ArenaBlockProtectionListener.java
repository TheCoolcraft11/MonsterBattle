package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.*;

/**
 * Protects original arena world blocks during the battle phase so that:
 * - Players can only break blocks they (or another player) placed during the current battle
 * - Explosions (TNT, creepers, etc.) only destroy player-placed blocks
 * <p>
 * Implementation detail:
 * We treat every block that exists at the start of a battle as "protected". We do not need to
 * scan all blocks; instead we whitelist blocks that players place during the battle. Only
 * whitelisted (placed) blocks may be broken or destroyed by explosions.
 */
public class ArenaBlockProtectionListener implements Listener {

    private final MonsterBattle plugin;
    
    private final Map<String, Set<Long>> placedBlocks = new HashMap<>();

    public ArenaBlockProtectionListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    /**
     * Called by plugin when a new battle phase starts to clear tracking.
     */
    public void battleStarted() {
        placedBlocks.clear();
    }

    private boolean isBattleArenaWorld(World world) {
        if (world == null) return false;
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return false;
        String name = world.getName();
        return name.startsWith("Arena_");
    }

    private long key(Block b) {
        return pack(b.getX(), b.getY(), b.getZ());
    }

    private static long pack(int x, int y, int z) {
        
        long lx = ((long) (x & 0x3FFFFFF)) << 38;
        long ly = ((long) (y & 0xFFF)) << 26;
        long lz = (long) (z & 0x3FFFFFF);
        return lx | ly | lz;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isBattleArenaWorld(event.getBlock().getWorld())) return;
        placedBlocks.computeIfAbsent(event.getBlock().getWorld().getName(), k -> new HashSet<>())
                .add(key(event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isBattleArenaWorld(event.getBlock().getWorld())) return;
        Set<Long> set = placedBlocks.get(event.getBlock().getWorld().getName());
        if (set == null || !set.contains(key(event.getBlock()))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou can only break blocks placed during the battle.");
            return;
        }
        
        set.remove(key(event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!isBattleArenaWorld(event.getLocation().getWorld())) return;
        filterExplosion(event.blockList(), event.getLocation().getWorld().getName());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!isBattleArenaWorld(event.getBlock().getWorld())) return;
        filterExplosion(event.blockList(), event.getBlock().getWorld().getName());
    }

    private void filterExplosion(List<Block> blocks, String worldName) {
        Set<Long> set = placedBlocks.get(worldName);
        if (set == null) {
            
            blocks.clear();
            return;
        }
        
        blocks.removeIf(b -> !set.contains(key(b)));
        
        if (!blocks.isEmpty()) {
            for (Block b : blocks) set.remove(key(b));
        }
    }
}


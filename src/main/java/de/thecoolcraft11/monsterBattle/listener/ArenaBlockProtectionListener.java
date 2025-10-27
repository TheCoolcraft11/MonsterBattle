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


public class ArenaBlockProtectionListener implements Listener {

    private final MonsterBattle plugin;

    private final Map<String, Set<Long>> placedBlocks = new HashMap<>();

    public ArenaBlockProtectionListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }


    public void battleStarted() {
        placedBlocks.clear();
    }

    private boolean isNotBattleArenaWorld(World world) {
        if (world == null) return true;
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return true;
        String name = world.getName();
        return !name.startsWith(plugin.getArenaPrefix());
    }

    private long key(Block b) {
        return pack(b.getX(), b.getY(), b.getZ());
    }

    private static long pack(int x, int y, int z) {

        long lx = ((long) (x & 0x3FFFFFF)) << 38;
        long ly = ((long) (y & 0xFFF)) << 26;
        long lz = z & 0x3FFFFFF;
        return lx | ly | lz;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isNotBattleArenaWorld(event.getBlock().getWorld())) return;
        placedBlocks.computeIfAbsent(event.getBlock().getWorld().getName(), k -> new HashSet<>())
                .add(key(event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isNotBattleArenaWorld(event.getBlock().getWorld())) return;
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
        if (isNotBattleArenaWorld(event.getLocation().getWorld())) return;
        filterExplosion(event.blockList(), event.getLocation().getWorld().getName());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isNotBattleArenaWorld(event.getBlock().getWorld())) return;
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


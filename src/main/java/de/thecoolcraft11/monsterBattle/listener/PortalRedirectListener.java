package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import org.bukkit.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public class PortalRedirectListener implements Listener {

    private final MonsterBattle plugin;

    public PortalRedirectListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!plugin.getConfig().getBoolean("separate-dimensions", true)) return;

        TeleportCause cause = event.getCause();
        World fromWorld = event.getFrom().getWorld();
        if (fromWorld == null) return;
        String fromName = fromWorld.getName();

        if (!(fromName.startsWith("Farm_"))) return;

        String base = extractBase(fromName);
        if (base == null) return;

        long seed = getBaseSeed(base, fromWorld.getSeed());

        String netherName = base + "_nether";
        String endName = base + "_the_end";
        boolean endEnabled = plugin.getConfig().getBoolean("create-end-dimensions", true);

        if (cause == TeleportCause.NETHER_PORTAL) {
            if (fromName.equals(base)) {
                World dest = ensureWorld(netherName, World.Environment.NETHER, seed);
                if (dest == null) return;
                Location to = scaleNether(event.getFrom(), dest, true);
                event.setTo(to);
                event.getPlayer().sendMessage(ChatColor.DARK_RED + "Entered nether: " + netherName);
            } else if (fromName.equals(netherName)) {
                World dest = ensureWorld(base, World.Environment.NORMAL, seed);
                if (dest == null) return;
                Location to = scaleNether(event.getFrom(), dest, false);
                event.setTo(to);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Returned to overworld: " + base);
            }
        } else if (cause == TeleportCause.END_PORTAL && endEnabled) {
            if (fromName.equals(base)) {
                World dest = ensureWorld(endName, World.Environment.THE_END, seed);
                if (dest == null) return;
                Location to = new Location(dest, 100, 49, 0, 90, 0);
                
                event.setTo(to);
                event.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "Entered the End: " + endName);
            } else if (fromName.equals(endName)) {
                World dest = ensureWorld(base, World.Environment.NORMAL, seed);
                if (dest == null) return;
                Location to = new Location(dest, 100, 49, 0, 90, 0);
                
                event.setTo(to);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Returned to overworld: " + base);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("separate-dimensions", true)) return;
        PlayerRespawnEvent.RespawnReason reason = event.getRespawnReason();
        if (reason != PlayerRespawnEvent.RespawnReason.END_PORTAL) return;
        World fromWorld = event.getPlayer().getWorld();
        String fromName = fromWorld.getName();
        if (!fromName.endsWith("_the_end") || !fromName.startsWith("Farm_")) return;
        String base = extractBase(fromName);
        if (base == null) return;
        World dest = ensureWorld(base, World.Environment.NORMAL, getBaseSeed(base, fromWorld.getSeed()));
        if (dest == null) return;
        Location to = new Location(dest, 100, 49, 0, 90, 0);
        event.setRespawnLocation(to);
    }

    private void copyOrientation(Location from, Location to) {
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
    }

    private Location scaleNether(Location from, World target, boolean overworldToNether) {
        double x = from.getX();
        double z = from.getZ();
        if (overworldToNether) {
            x /= 8.0;
            z /= 8.0;
        } else {
            x *= 8.0;
            z *= 8.0;
        }
        Location to = new Location(target, x, from.getY(), z, from.getYaw(), from.getPitch());
        return to;
    }

    private World ensureWorld(String name, World.Environment env, long seed) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        WorldCreator creator = new WorldCreator(name).environment(env).seed(seed).type(WorldType.NORMAL);
        return creator.createWorld();
    }

    private long getBaseSeed(String baseName, long fallback) {
        long configured = plugin.getConfig().getLong("world-seed", -1L);
        if (configured != -1L) return configured;
        World existingBase = Bukkit.getWorld(baseName);
        if (existingBase != null) return existingBase.getSeed();
        return fallback;
    }

    private String extractBase(String worldName) {
        if (worldName.endsWith("_nether")) {
            String pre = worldName.substring(0, worldName.length() - "_nether".length());
            if (pre.startsWith("Farm_") || pre.startsWith("Arena_")) return pre;
            return null;
        }
        if (worldName.endsWith("_the_end")) {
            String pre = worldName.substring(0, worldName.length() - "_the_end".length());
            if (pre.startsWith("Farm_") || pre.startsWith("Arena_")) return pre;
            return null;
        }
        return worldName;
    }
}

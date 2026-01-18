package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

public class BattleMobCombatListener implements Listener {

    private final MonsterBattle plugin;

    public BattleMobCombatListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    private boolean isTrackedBattleMob(Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (plugin.getDataController().getGameState() != GameState.BATTLE) return false;
        return plugin.getDataController().getTeamForMonster(e.getUniqueId()) != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (isTrackedBattleMob(event.getEntity())) {
            LivingEntity target = event.getTarget();
            if (target != null && !(target instanceof Player)) {
                event.setCancelled(true);
            }
            return;
        }
        LivingEntity target = event.getTarget();
        if (isTrackedBattleMob(target) && !(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        boolean damagerTracked = isTrackedBattleMob(damager);
        boolean victimTracked = isTrackedBattleMob(victim);

        if (victim instanceof Ghast) return;


        if (damager instanceof Player) return;


        if (victimTracked && (damager instanceof TNTPrimed ||
                damager instanceof EnderCrystal ||
                damager instanceof Projectile ||
                damager instanceof FallingBlock)) {
            return;
        }


        if (victimTracked && damagerTracked) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                return;
            }
        }


        if (damagerTracked && !(victim instanceof Player)) {
            event.setCancelled(true);
            retargetClosestPlayer(damager);
            return;
        }


        if (victimTracked) {
            event.setCancelled(true);
            retargetClosestPlayer(victim);
        }
    }
    

    private void retargetClosestPlayer(Entity source) {
        if (!(source instanceof Mob mob)) return;
        String team = plugin.getDataController().getTeamForMonster(mob.getUniqueId());
        if (team == null) return;
        var sb = Bukkit.getScoreboardManager();
        var t = sb.getMainScoreboard().getTeam(team);
        if (t == null) return;
        double best = Double.MAX_VALUE;
        Player bestP = null;
        for (String entry : t.getEntries()) {
            Player p = Bukkit.getPlayerExact(entry);
            if (p == null || !p.isOnline() || p.getWorld() != mob.getWorld()) continue;
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d < best) {
                best = d;
                bestP = p;
            }
        }
        if (bestP != null) {
            try {
                mob.setTarget(bestP);
            } catch (Throwable ignored) {
            }
        }
    }
}

package de.thecoolcraft11.monsterBattle.util;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class MonsterSpawner {

    private MonsterBattle plugin;
    private String currentTeam;

    public void start(MonsterBattle plugin, String currentTeam) {
        this.plugin = plugin;
        this.currentTeam = currentTeam;
        System.out.println("[MonsterSpawner] Starting for team: " + currentTeam);

        
        final boolean spawnAll = plugin.getConfig().getBoolean("monster-spawner.spawn-all-on-cycle", false);
        final int configuredMax = plugin.getConfig().getInt("monster-spawner.max-spawn-per-cycle", -1);
        int interval = plugin.getConfig().getInt("monster-spawner.tick-interval", 60);
        if (interval <= 0) interval = 60;

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    var sbManager = plugin.getServer().getScoreboardManager();
                    if (sbManager == null) return;
                    Set<Team> all = new HashSet<>(sbManager.getMainScoreboard().getTeams());
                    Team thisTeam = sbManager.getMainScoreboard().getTeam(currentTeam);
                    if (thisTeam == null) {
                        cancel();
                        return;
                    }
                    all.remove(thisTeam);
                    if (all.isEmpty()) return;

                    String baseName = "Arena_" + sanitizeWorldName(currentTeam);
                    World world = Bukkit.getWorld(baseName);
                    if (world == null) return;

                    List<SpawnData> spawnPoints = plugin.getDataController().getMonsterSpawns();
                    if (spawnPoints.isEmpty()) return;

                    if (spawnAll) {
                        
                        List<MobSnapshot> allSnapshots = new ArrayList<>();
                        for (Team t : all) {
                            
                            List<MobSnapshot> drained;
                            
                            while (!(drained = plugin.getDataController().pollKillsForTeam(t.getName(), Integer.MAX_VALUE)).isEmpty()) {
                                allSnapshots.addAll(drained);
                            }
                        }
                        if (allSnapshots.isEmpty()) return;
                        int spawned = 0;
                        int spCount = spawnPoints.size();
                        for (int i = 0; i < allSnapshots.size(); i++) {
                            MobSnapshot snap = allSnapshots.get(i);
                            SpawnData sp = spawnPoints.get(i % spCount); 
                            Location loc = new Location(world, sp.x, sp.y, sp.z);
                            var spawnedEntity = world.spawnEntity(loc, snap.getType());
                            if (spawnedEntity instanceof LivingEntity le) snap.apply(le);
                            spawned++;
                        }
                        if (spawned > 0)
                            System.out.println("[MonsterSpawner] Spawned ALL pending " + spawned + " mobs for team " + currentTeam);
                        return;
                    }

                    
                    int baseCapacity = spawnPoints.size();
                    int capacity;
                    if (configuredMax == -1) capacity = baseCapacity;
                    else capacity = Math.min(configuredMax, Math.max(0, configuredMax));
                    if (capacity <= 0) return;

                    List<MobSnapshot> batch = new ArrayList<>(capacity);
                    for (Team t : all) {
                        if (batch.size() >= capacity) break;
                        int remaining = capacity - batch.size();
                        batch.addAll(plugin.getDataController().pollKillsForTeam(t.getName(), remaining));
                    }
                    if (batch.isEmpty()) return;

                    int spawned = 0;
                    Iterator<MobSnapshot> iterator = batch.iterator();
                    for (SpawnData sp : spawnPoints) {
                        if (!iterator.hasNext() || spawned >= capacity) break;
                        MobSnapshot snap = iterator.next();
                        Location loc = new Location(world, sp.x, sp.y, sp.z);
                        var spawnedEntity = world.spawnEntity(loc, snap.getType());
                        if (spawnedEntity instanceof LivingEntity le) snap.apply(le);
                        spawned++;
                    }
                    if (spawned > 0)
                        System.out.println("[MonsterSpawner] Spawned " + spawned + " mobs (limited cycle) for team " + currentTeam);
                } catch (Exception ex) {
                    System.err.println("[MonsterSpawner] Error while spawning for team " + currentTeam + ": " + ex.getMessage());
                    ex.printStackTrace();
                    cancel();
                }
            }
        };
        runnable.runTaskTimer(plugin, 0L, interval);
    }

    private String sanitizeWorldName(String raw) {
        return raw.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}

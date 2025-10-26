package de.thecoolcraft11.monsterBattle.util;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class MonsterSpawner {

    private final Map<String, BukkitRunnable> activeSpawners = new HashMap<>();
    private final Map<String, Long> nextSpawnTicks = new HashMap<>();
    private final Map<String, Integer> baseIntervals = new HashMap<>();

    public void start(MonsterBattle plugin, String currentTeam) {
        plugin.getLogger().info("Starting MonsterSpawner for team: " + currentTeam);

        final boolean spawnAll = plugin.getConfig().getBoolean("monster-spawner.spawn-all-on-cycle", false);
        final int configuredMax = plugin.getConfig().getInt("monster-spawner.max-spawn-per-cycle", -1);
        int interval = plugin.getConfig().getInt("monster-spawner.tick-interval", 60);
        if (interval <= 0) interval = 60;

        final int baseInterval = interval;
        baseIntervals.put(currentTeam, baseInterval);
        nextSpawnTicks.put(currentTeam, 0L);

        BukkitRunnable runnable = new BukkitRunnable() {
            private long tickCounter = 0;

            @Override
            public void run() {
                try {
                    tickCounter++;


                    Long nextSpawn = nextSpawnTicks.get(currentTeam);
                    if (nextSpawn == null || tickCounter < nextSpawn) {
                        return;
                    }


                    int currentInterval = baseIntervals.getOrDefault(currentTeam, baseInterval);
                    nextSpawnTicks.put(currentTeam, tickCounter + currentInterval);


                    int maxActiveMobs = plugin.getConfig().getInt("monster-spawner.max-active-mobs", -1);
                    if (maxActiveMobs > 0) {
                        int currentActive = plugin.getDataController().getRemainingForTeam(currentTeam);
                        if (currentActive >= maxActiveMobs) {

                            return;
                        }
                    }
                    var sbManager = plugin.getServer().getScoreboardManager();
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

                    if (plugin.getDataController().getGameState() != GameState.BATTLE) return;

                    List<SpawnData> spawnPoints = plugin.getDataController().getMonsterSpawns();
                    if (spawnPoints.isEmpty()) return;

                    boolean showCycleMsg = plugin.getConfig().getBoolean("battle-remaining-on-spawn-cycle", true);
                    int spawnedThisCycle = 0;

                    if (spawnAll) {
                        List<MobSnapshot> allSnapshots = new ArrayList<>();
                        for (Team t : all) {
                            List<MobSnapshot> drained;
                            while (!(drained = plugin.getDataController().pollKillsForTeam(t.getName(), Integer.MAX_VALUE)).isEmpty()) {
                                allSnapshots.addAll(drained);
                            }
                        }
                        if (allSnapshots.isEmpty()) return;
                        int spCount = spawnPoints.size();
                        for (int i = 0; i < allSnapshots.size(); i++) {
                            MobSnapshot snap = allSnapshots.get(i);
                            SpawnData sp = spawnPoints.get(i % spCount);
                            Location loc = new Location(world, sp.x, sp.y, sp.z);
                            var spawnedEntity = world.spawnEntity(loc, snap.getType());
                            if (spawnedEntity instanceof LivingEntity le) {
                                snap.apply(le, plugin);
                                plugin.getDataController().registerSpawn(currentTeam, le);
                                applyPostSpawnEffects(plugin, thisTeam, le);
                                spawnedThisCycle++;
                            }
                        }
                        if (spawnedThisCycle > 0) {
                            plugin.getLogger().info("Spawned ALL pending " + spawnedThisCycle + " mobs for team " + currentTeam);
                        }
                    } else {
                        int baseCapacity = spawnPoints.size();
                        int capacity = configuredMax == -1 ? baseCapacity : Math.max(0, Math.min(configuredMax, baseCapacity));
                        if (capacity == 0) return;

                        List<MobSnapshot> batch = new ArrayList<>(capacity);
                        for (Team t : all) {
                            if (batch.size() >= capacity) break;
                            int remaining = capacity - batch.size();
                            batch.addAll(plugin.getDataController().pollKillsForTeam(t.getName(), remaining));
                        }
                        if (batch.isEmpty()) return;

                        Iterator<MobSnapshot> iterator = batch.iterator();
                        for (SpawnData sp : spawnPoints) {
                            if (!iterator.hasNext() || spawnedThisCycle >= capacity) break;
                            MobSnapshot snap = iterator.next();
                            Location loc = new Location(world, sp.x, sp.y, sp.z);
                            var spawnedEntity = world.spawnEntity(loc, snap.getType());
                            if (spawnedEntity instanceof LivingEntity le) {
                                snap.apply(le, plugin);
                                plugin.getDataController().registerSpawn(currentTeam, le);
                                applyPostSpawnEffects(plugin, thisTeam, le);
                                spawnedThisCycle++;
                            }
                        }
                        if (spawnedThisCycle > 0) {
                            plugin.getLogger().info("Spawned " + spawnedThisCycle + " mobs (limited cycle) for team " + currentTeam);
                        }
                    }

                    if (spawnedThisCycle > 0 && showCycleMsg) {
                        int active = plugin.getDataController().getRemainingForTeam(currentTeam);
                        for (String entry : thisTeam.getEntries()) {
                            var p = Bukkit.getPlayerExact(entry);
                            if (p != null) {
                                p.sendMessage(Component.text("Spawned: ", NamedTextColor.AQUA)
                                        .append(Component.text(spawnedThisCycle, NamedTextColor.YELLOW))
                                        .append(Component.text(" | Active arena mobs: ", NamedTextColor.AQUA))
                                        .append(Component.text(active, NamedTextColor.YELLOW)));
                            }
                        }
                    }


                    if (spawnedThisCycle > 0) {
                        updateBossbarForTeam(plugin, currentTeam);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().severe("Error while spawning for team " + currentTeam + ": " + ex.getMessage());
                    for (StackTraceElement element : ex.getStackTrace()) {
                        plugin.getLogger().severe("    at " + element.toString());
                    }
                    cancel();
                }
            }
        };
        activeSpawners.put(currentTeam, runnable);
        runnable.runTaskTimer(plugin, 0L, 1L);
    }

    private void applyPostSpawnEffects(MonsterBattle plugin, Team targetTeam, LivingEntity le) {
        try {
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, true, false, true));
        } catch (NoSuchFieldError | IllegalArgumentException ignored) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 1_000_000, 0, true, false, true));
        }
        if (plugin.getConfig().getBoolean("monster-spawner.persist-battle-mobs", true)) {
            try {
                le.setPersistent(true);
                le.setRemoveWhenFarAway(false);
            } catch (Throwable ignored) {
            }
            try {
                if (le instanceof Mob m) m.setRemoveWhenFarAway(false);
            } catch (Throwable ignored) {
            }
        }

        if (plugin.getConfig().getBoolean("battle-chunk-loading.enabled", true)) {
            var loc = le.getLocation();
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            String wName = loc.getWorld().getName();
            int limit = plugin.getConfig().getInt("battle-chunk-loading.max-forced-chunks-per-world", 256);
            var current = plugin.getDataController().getBattleChunkTicketsView().get(wName);
            if (limit <= 0 || current == null || current.size() < limit) {
                plugin.getDataController().addBattleChunk(wName, cx, cz);
                try {
                    loc.getWorld().getChunkAt(cx, cz).setForceLoaded(true);
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            le.addScoreboardTag("monsterbattle_arena");
        } catch (Throwable ignored) {
        }
        if (targetTeam == null) return;
        if (le instanceof Mob mob) {

            Player initial = findNearestTeamPlayer(mob.getLocation(), targetTeam);
            if (initial != null) {
                try {
                    mob.setTarget(initial);
                } catch (Throwable ignored) {
                }
            }

            scheduleRetarget(plugin, mob, targetTeam, 40L);
            scheduleRetarget(plugin, mob, targetTeam, 120L);
            scheduleRetarget(plugin, mob, targetTeam, 240L);
        }
    }

    private void scheduleRetarget(MonsterBattle plugin, Mob mob, Team team, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (mob.isDead()) {
                return;
            } else {
                mob.getWorld();
            }
            if (mob.getTarget() != null && mob.getTarget().isValid()) return;
            Player p = findNearestTeamPlayer(mob.getLocation(), team);
            if (p != null && p.isOnline()) {
                try {
                    mob.setTarget(p);
                } catch (Throwable ignored) {
                }
            }
        }, delay);
    }

    private Player findNearestTeamPlayer(Location from, Team team) {
        double best = Double.MAX_VALUE;
        Player bestPlayer = null;
        World world = from.getWorld();
        for (String entry : team.getEntries()) {
            Player p = Bukkit.getPlayerExact(entry);
            if (p == null || !p.isOnline() || p.getWorld() != world) continue;
            double dist = p.getLocation().distanceSquared(from);
            if (dist < best) {
                best = dist;
                bestPlayer = p;
            }
        }
        return bestPlayer;
    }

    private String sanitizeWorldName(String raw) {
        return raw.replaceAll("[^A-Za-z0-9_-]", "_");
    }


    public void reduceSpawnTimer(String team, long reductionTicks) {
        Long nextSpawn = nextSpawnTicks.get(team);
        if (nextSpawn != null && reductionTicks > 0) {

            nextSpawnTicks.put(team, Math.max(0, nextSpawn - reductionTicks));
        }
    }


    public void cancelSpawner(String team) {
        BukkitRunnable runnable = activeSpawners.remove(team);
        if (runnable != null) {
            try {
                runnable.cancel();
            } catch (Exception ignored) {
            }
        }
        nextSpawnTicks.remove(team);
        baseIntervals.remove(team);
    }


    public void cancelAllSpawners() {
        for (BukkitRunnable runnable : activeSpawners.values()) {
            try {
                runnable.cancel();
            } catch (Exception ignored) {
            }
        }
        activeSpawners.clear();
        nextSpawnTicks.clear();
        baseIntervals.clear();
    }

    private void updateBossbarForTeam(MonsterBattle plugin, String teamName) {
        var dc = plugin.getDataController();
        var sbManager = plugin.getServer().getScoreboardManager();

        Set<Team> allTeams = new HashSet<>(sbManager.getMainScoreboard().getTeams());
        Team thisTeam = sbManager.getMainScoreboard().getTeam(teamName);
        if (thisTeam == null) return;
        allTeams.remove(thisTeam);


        int totalMobs = 0;
        for (Team t : allTeams) {
            totalMobs += dc.getCapturedTotal(t.getName());
        }


        int spawnedMobs = dc.getRemainingForTeam(teamName);


        int waitingToSpawn = 0;
        for (Team t : allTeams) {
            waitingToSpawn += dc.getKillsForTeam(t.getName()).size();
        }
        int actualSpawned = totalMobs - waitingToSpawn;
        int mobsKilled = actualSpawned - spawnedMobs;

        plugin.getBossbarController().updateProgress(teamName, mobsKilled, totalMobs, actualSpawned);
    }
}

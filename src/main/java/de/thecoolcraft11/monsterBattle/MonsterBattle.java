package de.thecoolcraft11.monsterBattle;

import de.thecoolcraft11.monsterBattle.command.*;
import de.thecoolcraft11.monsterBattle.listener.*;
import de.thecoolcraft11.monsterBattle.util.BossbarController;
import de.thecoolcraft11.monsterBattle.util.DataController;
import de.thecoolcraft11.monsterBattle.util.GameState;
import de.thecoolcraft11.monsterBattle.util.PhaseSwitchHook;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public final class MonsterBattle extends JavaPlugin {

    private final DataController dataController = new DataController();
    private final PhaseSwitchHook phaseSwitchHook = new PhaseSwitchHook();
    private final BossbarController bossbarController = new BossbarController(this);
    private final de.thecoolcraft11.monsterBattle.util.MonsterSpawner monsterSpawner = new de.thecoolcraft11.monsterBattle.util.MonsterSpawner();
    private int maintenanceTaskId = -1;
    private int chunkRefreshTaskId = -1;
    private int integrityScanTaskId = -1;
    private ArenaBlockProtectionListener arenaBlockProtectionListener;
    private CapturedMobsInventoryListener capturedMobsInventoryListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();


        if (getConfig().getBoolean("cleanup-on-reload.delete-bossbars-on-enable", true)) {
            bossbarController.removeAll();
        }


        if (getConfig().getBoolean("cleanup-on-reload.remove-worlds-on-enable", false)) {
            cleanupGameWorlds();
        }

        if (getCommand("setphase") != null) {
            SetPhaseCommand sp = new SetPhaseCommand(this);
            Objects.requireNonNull(getCommand("setphase")).setExecutor(sp);
            Objects.requireNonNull(getCommand("setphase")).setTabCompleter(sp);
        }
        if (getCommand("mobspawn") != null) {
            MonsterSpawnCommand msc = new MonsterSpawnCommand(this);
            Objects.requireNonNull(getCommand("mobspawn")).setExecutor(msc);
            Objects.requireNonNull(getCommand("mobspawn")).setTabCompleter(msc);
        }
        if (getCommand("dtp") != null) {
            DimensionTeleportCommand dtp = new DimensionTeleportCommand(this);
            Objects.requireNonNull(getCommand("dtp")).setExecutor(dtp);
            Objects.requireNonNull(getCommand("dtp")).setTabCompleter(dtp);
        }
        if (getCommand("worldname") != null) {
            WorldNameCommand wn = new WorldNameCommand();
            Objects.requireNonNull(getCommand("worldname")).setExecutor(wn);
            Objects.requireNonNull(getCommand("worldname")).setTabCompleter(wn);
        }
        if (getCommand("mobtp") != null) {
            MobTeleportCommand mtc = new MobTeleportCommand(this);
            Objects.requireNonNull(getCommand("mobtp")).setExecutor(mtc);
            Objects.requireNonNull(getCommand("mobtp")).setTabCompleter(mtc);
        }
        if (getCommand("capturedmobs") != null) {
            CapturedMobsCommand cmc = new CapturedMobsCommand(this);
            Objects.requireNonNull(getCommand("capturedmobs")).setExecutor(cmc);
            Objects.requireNonNull(getCommand("capturedmobs")).setTabCompleter(cmc);
        }
        if (getCommand("arena") != null) {
            ArenaCommand ac = new ArenaCommand(this);
            Objects.requireNonNull(getCommand("arena")).setExecutor(ac);
            Objects.requireNonNull(getCommand("arena")).setTabCompleter(ac);
        }
        if (getCommand("army") != null) {
            ArmyCommand armyCmd = new ArmyCommand(this);
            Objects.requireNonNull(getCommand("army")).setExecutor(armyCmd);
            Objects.requireNonNull(getCommand("army")).setTabCompleter(armyCmd);
        }
        getServer().getPluginManager().registerEvents(new MobKillListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalRedirectListener(this), this);
        getServer().getPluginManager().registerEvents(new BattleMobDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BattleMobCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinRetargetListener(this), this);
        getServer().getPluginManager().registerEvents(new BattleChunkListener(this), this);

        capturedMobsInventoryListener = new CapturedMobsInventoryListener(this);
        getServer().getPluginManager().registerEvents(capturedMobsInventoryListener, this);

        if (getConfig().getBoolean("battle-removal-listeners.enabled", true)) {
            getServer().getPluginManager().registerEvents(new BattleMobRemovalListener(this), this);
        }

        arenaBlockProtectionListener = new ArenaBlockProtectionListener(this);
        getServer().getPluginManager().registerEvents(arenaBlockProtectionListener, this);

        getServer().getPluginManager().registerEvents(new ArenaControllerListener(this), this);

        getServer().getPluginManager().registerEvents(new PhaseRespawnListener(this), this);

        boolean enableMaint = getConfig().getBoolean("battle-maintenance.enabled", true);
        if (enableMaint) {
            long initialDelay = Math.max(0L, getConfig().getLong("battle-maintenance.initial-delay-ticks", 60L));
            long period = Math.max(1L, getConfig().getLong("battle-maintenance.period-ticks", 100L));
            maintenanceTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::runBattleMaintenance, initialDelay, period);
        }

        if (getConfig().getBoolean("battle-chunk-loading.enabled", true)) {
            long refresh = Math.max(100L, getConfig().getLong("battle-chunk-loading.refresh-ticks", 600L));
            chunkRefreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::refreshBattleChunks, refresh, refresh);
        }

        if (getConfig().getBoolean("battle-integrity-scan.enabled", true)) {
            long period = Math.max(20L, getConfig().getLong("battle-integrity-scan.period-ticks", 200L));
            integrityScanTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::runIntegrityScan, period, period);
        }
    }

    @Override
    public void onDisable() {
        if (maintenanceTaskId != -1) Bukkit.getScheduler().cancelTask(maintenanceTaskId);
        if (chunkRefreshTaskId != -1) Bukkit.getScheduler().cancelTask(chunkRefreshTaskId);
        if (integrityScanTaskId != -1) Bukkit.getScheduler().cancelTask(integrityScanTaskId);
        releaseBattleChunks();


        if (getConfig().getBoolean("cleanup-on-reload.delete-bossbars-on-disable", true)) {
            bossbarController.removeAll();
        }


        if (getConfig().getBoolean("cleanup-on-reload.remove-worlds-on-disable", false)) {
            cleanupGameWorlds();
        }

        HandlerList.unregisterAll(this);
    }

    public DataController getDataController() {
        return dataController;
    }

    public PhaseSwitchHook getPhaseSwitchHook() {
        return phaseSwitchHook;
    }

    public BossbarController getBossbarController() {
        return bossbarController;
    }

    public de.thecoolcraft11.monsterBattle.util.MonsterSpawner getMonsterSpawner() {
        return monsterSpawner;
    }

    public CapturedMobsInventoryListener getCapturedMobsInventoryListener() {
        return capturedMobsInventoryListener;
    }

    /**
     * Gets the arena world prefix from config (template world name + "_")
     *
     * @return The arena prefix (e.g., "Arena_" or "CustomArena_")
     */
    public String getArenaPrefix() {
        String templateWorld = getConfig().getString("arena-template-world", "Arena");
        return templateWorld + "_";
    }

    public void notifyBattleStarted() {
        if (arenaBlockProtectionListener != null) {
            arenaBlockProtectionListener.battleStarted();
        }
    }

    private void runBattleMaintenance() {
        if (dataController.getGameState() != GameState.BATTLE) return;
        boolean removeDead = getConfig().getBoolean("battle-maintenance.remove-dead", true);
        boolean doRetarget = getConfig().getBoolean("battle-maintenance.retarget", true);
        boolean reassert = getConfig().getBoolean("battle-maintenance.reassert-persistence", true);
        boolean chunkLoad = getConfig().getBoolean("battle-chunk-loading.enabled", true);
        int chunkLimit = getConfig().getInt("battle-chunk-loading.max-forced-chunks-per-world", 256);
        if (!removeDead && !doRetarget && !reassert && !chunkLoad) return;

        var sbMan = Bukkit.getScoreboardManager();
        int removed = 0;
        for (var entry : dataController.getActiveMonstersView().entrySet()) {
            String teamName = entry.getKey();
            Team team = sbMan.getMainScoreboard().getTeam(teamName);
            for (UUID id : entry.getValue()) {
                Entity e = Bukkit.getEntity(id);
                if (e == null || e.isDead() || !e.isValid()) {
                    if (removeDead) {
                        dataController.registerMonsterDeath(id);
                        removed++;
                    }
                    continue;
                }
                if (doRetarget && e instanceof Mob mob) {
                    var target = mob.getTarget();
                    boolean targetValid = target instanceof Player p && p.isOnline() && p.getWorld() == mob.getWorld() && !p.isDead();
                    if (!targetValid) {
                        Player best = findNearestTeamPlayer(mob, team);
                        if (best != null) {
                            try {
                                mob.setTarget(best);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
                if (reassert && e instanceof Mob mob2) {
                    try {
                        mob2.setRemoveWhenFarAway(false);
                    } catch (Throwable ignored) {
                    }
                    try {
                        mob2.setPersistent(true);
                    } catch (Throwable ignored) {
                    }
                    try {
                        mob2.addScoreboardTag("monsterbattle_arena");
                    } catch (Throwable ignored) {
                    }
                }
                if (chunkLoad) {
                    try {
                        var w = e.getWorld();
                        int cx = e.getLocation().getBlockX() >> 4;
                        int cz = e.getLocation().getBlockZ() >> 4;
                        boolean added = dataController.addBattleChunkIfUnderLimit(w.getName(), cx, cz, chunkLimit);
                        var chunk = w.getChunkAt(cx, cz);
                        if (added || !chunk.isForceLoaded()) {
                            chunk.setForceLoaded(true);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        if (removed > 0 && getConfig().getBoolean("battle-maintenance.debug-log", false)) {
            getLogger().info("Battle maintenance removed " + removed + " stale tracked mobs.");
        }
    }

    private Player findNearestTeamPlayer(Mob mob, Team team) {
        if (team == null) return null;
        double best = Double.MAX_VALUE;
        Player bestP = null;
        for (String entry : team.getEntries()) {
            Player p = Bukkit.getPlayerExact(entry);
            if (p == null || !p.isOnline() || p.getWorld() != mob.getWorld()) continue;
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d < best) {
                best = d;
                bestP = p;
            }
        }
        return bestP;
    }

    private void refreshBattleChunks() {
        if (dataController.getGameState() != GameState.BATTLE) return;
        if (!getConfig().getBoolean("battle-chunk-loading.enabled", true)) return;
        boolean debug = getConfig().getBoolean("battle-chunk-loading.debug-log", false);
        int reforced = 0;
        var tickets = dataController.getBattleChunkTicketsView();
        for (var entry : tickets.entrySet()) {
            World w = Bukkit.getWorld(entry.getKey());
            if (w == null) continue;
            for (long key : entry.getValue()) {
                int cx = (int) (key >> 32);
                int cz = (int) (key & 0xffffffffL);
                try {
                    Chunk chunk = w.getChunkAt(cx, cz);
                    if (!chunk.isForceLoaded()) {
                        chunk.setForceLoaded(true);
                        reforced++;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (debug && reforced > 0) {
            getLogger().info("Re-forced " + reforced + " battle chunks.");
        }
    }

    public void releaseBattleChunks() {
        var tickets = dataController.getBattleChunkTicketsView();
        for (var entry : tickets.entrySet()) {
            World w = Bukkit.getWorld(entry.getKey());
            if (w == null) continue;
            for (long key : entry.getValue()) {
                int cx = (int) (key >> 32);
                int cz = (int) (key & 0xffffffffL);
                try {
                    Chunk c = w.getChunkAt(cx, cz);
                    if (c.isForceLoaded()) c.setForceLoaded(false);
                } catch (Throwable ignored) {
                }
            }
        }
        dataController.clearBattleChunkTickets();
    }

    private void runIntegrityScan() {
        if (dataController.getGameState() != GameState.BATTLE) return;
        boolean debug = getConfig().getBoolean("battle-integrity-scan.debug-log", false);
        int removed = 0;
        for (var entry : dataController.getActiveMonstersView().entrySet()) {
            for (var id : entry.getValue()) {
                var ent = Bukkit.getEntity(id);
                if (ent == null || ent.isDead() || !ent.isValid()) {

                    dataController.registerMonsterDeath(id);
                    removed++;
                }
            }
        }
        if (removed > 0 && debug) {
            getLogger().info("Integrity scan pruned " + removed + " stale tracked mobs.");
        }
    }

    private void cleanupGameWorlds() {
        getLogger().info("[Cleanup] Starting world cleanup...");
        int removed = 0;


        World mainWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (mainWorld == null) {
            getLogger().warning("[Cleanup] Cannot find main world for teleporting players!");
            return;
        }


        for (World world : Bukkit.getWorlds()) {
            if (world == null) continue;
            String worldName = world.getName();

            String arenaPrefix = getArenaPrefix();
            boolean isGameWorld = worldName.startsWith(arenaPrefix) ||
                    worldName.startsWith("Farm_") ||
                    worldName.matches(arenaPrefix.replace("_", "") + "_.*_(nether|the_end)") ||
                    worldName.matches("Farm_.*_(nether|the_end)");

            if (!isGameWorld) continue;

            try {
                getLogger().info("[Cleanup] Removing loaded world: " + worldName);


                for (Player player : world.getPlayers()) {
                    player.teleport(mainWorld.getSpawnLocation());
                    player.sendMessage("§eYou were teleported because the world " + worldName + " is being cleaned up.");
                }


                world.save();
                boolean unloaded = Bukkit.unloadWorld(world, false);

                if (unloaded) {
                    getLogger().info("[Cleanup] Successfully unloaded world: " + worldName);


                    File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                    if (worldFolder.exists()) {
                        boolean deleted = deleteWorldFolder(worldFolder);
                        if (deleted) {
                            getLogger().info("[Cleanup] Successfully deleted world folder: " + worldName);
                            removed++;
                        } else {
                            getLogger().warning("[Cleanup] Failed to delete world folder: " + worldName);
                        }
                    }
                } else {
                    getLogger().warning("[Cleanup] Failed to unload world: " + worldName);
                }
            } catch (Exception e) {
                getLogger().warning("[Cleanup] Error cleaning up loaded world " + worldName + ": " + e.getMessage());
            }
        }


        try {
            File worldContainer = Bukkit.getWorldContainer();
            File[] worldFolders = worldContainer.listFiles(File::isDirectory);

            if (worldFolders != null) {
                String arenaPrefix = getArenaPrefix();
                for (File worldFolder : worldFolders) {
                    String worldName = worldFolder.getName();


                    boolean isGameWorld = worldName.startsWith(arenaPrefix) ||
                            worldName.startsWith("Farm_") ||
                            worldName.matches(arenaPrefix.replace("_", "") + "_.*_(nether|the_end)") ||
                            worldName.matches("Farm_.*_(nether|the_end)");

                    if (!isGameWorld) continue;


                    World loadedWorld = Bukkit.getWorld(worldName);
                    if (loadedWorld != null) continue;

                    try {
                        getLogger().info("[Cleanup] Removing unloaded world folder: " + worldName);
                        boolean deleted = deleteWorldFolder(worldFolder);
                        if (deleted) {
                            getLogger().info("[Cleanup] Successfully deleted unloaded world folder: " + worldName);
                            removed++;
                        } else {
                            getLogger().warning("[Cleanup] Failed to delete unloaded world folder: " + worldName);
                        }
                    } catch (Exception e) {
                        getLogger().warning("[Cleanup] Error cleaning up unloaded world folder " + worldName + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("[Cleanup] Error scanning for unloaded world folders: " + e.getMessage());
        }

        if (removed > 0) {
            getLogger().info("[Cleanup] Successfully cleaned up " + removed + " game worlds.");
        } else {
            getLogger().info("[Cleanup] No game worlds found to clean up.");
        }
    }

    private boolean deleteWorldFolder(File worldFolder) {
        try {

            try (Stream<Path> paths = Files.walk(worldFolder.toPath())) {
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                getLogger().warning("[Cleanup] Failed to delete: " + file.getAbsolutePath());
                            }
                        });
            }

            return !worldFolder.exists();
        } catch (Exception e) {
            getLogger().warning("[Cleanup] Error during world folder deletion: " + e.getMessage());
            return false;
        }
    }
}

package de.thecoolcraft11.monsterBattle.util;

import org.bukkit.entity.LivingEntity;

import java.util.*;

public class DataController {
    private Map<String, List<MobSnapshot>> teamKills = new HashMap<>();
    private final Map<String, List<MobSnapshot>> capturedMobsSnapshot = new HashMap<>(); 
    private GameState gameState = GameState.LOBBY;
    private final List<SpawnData> monsterSpawns = new ArrayList<>();


    private final Map<String, Set<UUID>> activeMonsters = new HashMap<>();
    private final Map<UUID, String> monsterToTeam = new HashMap<>();
    private final Map<String, Long> teamFinishTimes = new HashMap<>();
    private final Map<String, Integer> teamCapturedTotals = new HashMap<>();
    private long battleStartTime = 0L;

    private final Map<String, Set<Long>> battleChunkTickets = new HashMap<>();

    public Map<String, List<MobSnapshot>> getTeamKills() {
        return teamKills;
    }

    public void setTeamKills(Map<String, List<MobSnapshot>> teamKills) {
        this.teamKills = teamKills;
    }

    public List<MobSnapshot> getKillsForTeam(String team) {
        return teamKills.getOrDefault(team, Collections.emptyList());
    }

    public void addKillForTeam(String team, LivingEntity entity) {
        teamKills.computeIfAbsent(team, k -> new ArrayList<>()).add(MobSnapshot.fromEntity(entity));
    }

    public void clearKillsForTeam(String team) {
        teamKills.computeIfAbsent(team, k -> new ArrayList<>()).clear();
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public void resetTeamKillsForTeams(Collection<String> teamNames) {
        for (String name : teamNames) teamKills.put(name, new ArrayList<>());
    }

    public void addMonsterSpawn(SpawnData loc) {
        monsterSpawns.add(loc);
    }

    public List<SpawnData> getMonsterSpawns() {
        return monsterSpawns;
    }

    public void clearMonsterSpawns() {
        monsterSpawns.clear();
    }

    public List<MobSnapshot> consumeKillsForTeam(String team) {
        List<MobSnapshot> list = teamKills.getOrDefault(team, new ArrayList<>());
        if (list.isEmpty()) return Collections.emptyList();
        List<MobSnapshot> copy = new ArrayList<>(list);
        list.clear();
        return copy;
    }

    public List<MobSnapshot> pollKillsForTeam(String team, int max) {
        List<MobSnapshot> list = teamKills.get(team);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        int count = Math.min(max, list.size());
        List<MobSnapshot> out = new ArrayList<>(list.subList(0, count));

        list.subList(0, count).clear();
        return out;
    }


    public void battlePhaseStarted(Collection<String> teams) {
        activeMonsters.clear();
        monsterToTeam.clear();
        teamFinishTimes.clear();
        teamCapturedTotals.clear();
        battleStartTime = System.currentTimeMillis();

        
        capturedMobsSnapshot.clear();
        for (String t : teams) {
            activeMonsters.put(t, new HashSet<>());
            List<MobSnapshot> teamList = getKillsForTeam(t);
            teamCapturedTotals.put(t, teamList.size());
            
            capturedMobsSnapshot.put(t, new ArrayList<>(teamList));
        }
        clearBattleChunkTickets();
    }

    public void registerSpawn(String team, LivingEntity entity) {
        if (gameState != GameState.BATTLE) return;
        activeMonsters.computeIfAbsent(team, k -> new HashSet<>()).add(entity.getUniqueId());
        monsterToTeam.put(entity.getUniqueId(), team);
    }

    public void registerMonsterDeath(UUID uuid) {
        if (gameState != GameState.BATTLE) return;
        String team = monsterToTeam.remove(uuid);
        if (team == null) return;
        Set<UUID> set = activeMonsters.get(team);
        if (set != null) {
            set.remove(uuid);
            if (set.isEmpty() && !teamFinishTimes.containsKey(team)) {
                long elapsed = System.currentTimeMillis() - battleStartTime;
                teamFinishTimes.put(team, elapsed);
            }
        }
    }

    public String getTeamForMonster(UUID uuid) {
        return monsterToTeam.get(uuid);
    }

    public Map<String, Long> getTeamFinishTimes() {
        return Collections.unmodifiableMap(teamFinishTimes);
    }

    public boolean isTeamFinished(String team) {
        return teamFinishTimes.containsKey(team);
    }

    public long getBattleStartTime() {
        return battleStartTime;
    }

    public int getRemainingForTeam(String team) {
        return activeMonsters.getOrDefault(team, Collections.emptySet()).size();
    }

    public boolean allTeamsFinished() {

        for (Map.Entry<String, Set<UUID>> e : activeMonsters.entrySet()) {
            if (!e.getValue().isEmpty()) return false;
        }
        return true;
    }

    public int getCapturedTotal(String team) {
        return teamCapturedTotals.getOrDefault(team, 0);
    }

    
    public List<MobSnapshot> getCapturedMobsForTeam(String team) {
        
        if (gameState == GameState.BATTLE || gameState == GameState.ENDED) {
            return capturedMobsSnapshot.getOrDefault(team, Collections.emptyList());
        }
        
        return getKillsForTeam(team);
    }

    public Map<String, Set<UUID>> getActiveMonstersView() {

        Map<String, Set<UUID>> copy = new HashMap<>();
        for (Map.Entry<String, Set<UUID>> e : activeMonsters.entrySet()) {
            copy.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public synchronized void addBattleChunk(String worldName, int chunkX, int chunkZ) {

        battleChunkTickets.computeIfAbsent(worldName, k -> new HashSet<>())
                .add((((long) chunkX) << 32) | (chunkZ & 0xffffffffL));
    }

    public synchronized boolean isBattleChunkTracked(String worldName, int chunkX, int chunkZ) {
        Set<Long> set = battleChunkTickets.get(worldName);
        if (set == null) return false;
        long key = (((long) chunkX) << 32) | (chunkZ & 0xffffffffL);
        return set.contains(key);
    }

    public synchronized boolean addBattleChunkIfUnderLimit(String worldName, int chunkX, int chunkZ, int limit) {
        Set<Long> set = battleChunkTickets.computeIfAbsent(worldName, k -> new HashSet<>());
        if (limit > 0 && set.size() >= limit && !isBattleChunkTracked(worldName, chunkX, chunkZ)) return false;
        long key = (((long) chunkX) << 32) | (chunkZ & 0xffffffffL);
        return set.add(key);
    }

    public synchronized Map<String, Set<Long>> getBattleChunkTicketsView() {
        Map<String, Set<Long>> copy = new HashMap<>();
        for (var e : battleChunkTickets.entrySet()) {
            copy.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public synchronized void clearBattleChunkTickets() {
        battleChunkTickets.clear();
    }
}

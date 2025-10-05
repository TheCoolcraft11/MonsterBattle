package de.thecoolcraft11.monsterBattle.util;

import org.bukkit.entity.LivingEntity;

import java.util.*;

public class DataController {
    
    private Map<String, List<MobSnapshot>> teamKills = new HashMap<>();
    private GameState gameState = GameState.LOBBY;
    private List<SpawnData> monsterSpawns = new ArrayList<>();

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
}

package de.thecoolcraft11.monsterBattle;

import de.thecoolcraft11.monsterBattle.command.DimensionTeleportCommand;
import de.thecoolcraft11.monsterBattle.command.MonsterSpawnCommand;
import de.thecoolcraft11.monsterBattle.command.SetPhaseCommand;
import de.thecoolcraft11.monsterBattle.command.WorldNameCommand;
import de.thecoolcraft11.monsterBattle.listener.MobKillListener;
import de.thecoolcraft11.monsterBattle.listener.PortalRedirectListener;
import de.thecoolcraft11.monsterBattle.util.DataController;
import de.thecoolcraft11.monsterBattle.util.PhaseSwitchHook;
import org.bukkit.plugin.java.JavaPlugin;

public final class MonsterBattle extends JavaPlugin {

    private final DataController dataController = new DataController();
    private final PhaseSwitchHook phaseSwitchHook = new PhaseSwitchHook();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getCommand("setphase") != null) {
            getCommand("setphase").setExecutor(new SetPhaseCommand(this));
        }
        if (getCommand("mobspawn") != null) {
            getCommand("mobspawn").setExecutor(new MonsterSpawnCommand(this));
        }
        if (getCommand("dtp") != null) {
            DimensionTeleportCommand dtp = new DimensionTeleportCommand(this);
            getCommand("dtp").setExecutor(dtp);
            getCommand("dtp").setTabCompleter(dtp);
        }
        if (getCommand("worldname") != null) {
            WorldNameCommand wn = new WorldNameCommand();
            getCommand("worldname").setExecutor(wn);
            getCommand("worldname").setTabCompleter(wn);
        }
        getServer().getPluginManager().registerEvents(new MobKillListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalRedirectListener(this), this);
    }

    @Override
    public void onDisable() {
    }

    public DataController getDataController() {
        return dataController;
    }

    public PhaseSwitchHook getPhaseSwitchHook() {
        return phaseSwitchHook;
    }
}

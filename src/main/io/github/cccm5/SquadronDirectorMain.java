package io.github.cccm5;

import io.github.cccm5.commands.SDCommand;
import io.github.cccm5.listener.MovecraftListener;
import io.github.cccm5.listener.PlayerListener;
import io.github.cccm5.listener.SignChangeListener;
import io.github.cccm5.managers.DirectorManager;
import net.countercraft.movecraft.craft.CraftManager;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
public class SquadronDirectorMain extends JavaPlugin implements Listener {
public static Logger logger;
public static final String ERROR_TAG = ChatColor.RED + "Error: " + ChatColor.DARK_RED;
public static final String SUCCESS_TAG = ChatColor.DARK_AQUA + "Squadron Director: " + ChatColor.WHITE;


private static SquadronDirectorMain instance;
private CraftManager craftManager;
private FileConfiguration config;
private DirectorManager directorManager;
private static boolean debug;


    public void onEnable() {
        logger = this.getLogger();

        //************************
        //* Check server version *
        //************************
        String packageName = getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);
        String[] parts = version.split("_");

        //************************
        //*       Configs        *
        //************************
        config = super.getConfig();
        config.addDefault("Max crafts",12);
        config.addDefault("Max spacing",20);
        config.addDefault("Craft types", Arrays.asList("Airskiff","Subairskiff"));
        config.addDefault("Debug mode",false);
        config.options().copyDefaults(true);
        this.saveConfig();
        debug = config.getBoolean("Debug mode");
        //************************
        //*    Load Movecraft    *
        //************************
        if(getServer().getPluginManager().getPlugin("Movecraft") == null || !getServer().getPluginManager().getPlugin("Movecraft").isEnabled()) {
            logger.log(Level.SEVERE, "Movecraft not found or not enabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        craftManager = CraftManager.getInstance();

        // Run the asynch scheduled tasks. Should this be a separate class / method? Yes. Am I going to put it in one? No.
        directorManager = new DirectorManager();

        // Register events and commands
        this.getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        this.getServer().getPluginManager().registerEvents(new SignChangeListener(), this);
        getServer().getPluginManager().registerEvents(new MovecraftListener(), this);
        getCommand("squadronDirector").setExecutor(new SDCommand());
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    public void onDisable() {
        logger = null;
        instance = null;
    }

    public static boolean isDebug(){
        return debug;
    }

    public static SquadronDirectorMain getInstance(){
        return instance;
    }

    @Override
    public FileConfiguration getConfig() {
        return config;
    }

    public DirectorManager getDirectorManager() {
        return directorManager;
    }
}

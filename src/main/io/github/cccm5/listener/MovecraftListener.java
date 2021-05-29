package io.github.cccm5.listener;

import io.github.cccm5.SquadronDirectorMain;
import io.github.cccm5.managers.DirectorManager;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.events.CraftSinkEvent;
import net.countercraft.movecraft.events.SignTranslateEvent;
import net.countercraft.movecraft.utils.MathUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class MovecraftListener implements Listener {
    DirectorManager manager = SquadronDirectorMain.getInstance().getDirectorManager();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftSink(CraftSinkEvent event) {
        Craft craft = event.getCraft();
        DirectorManager manager = SquadronDirectorMain.getInstance().getDirectorManager();
        List<Craft> directed = manager.getDirectedCrafts().getOrDefault(craft.getNotificationPlayer(), new CopyOnWriteArrayList<>());
        if (!directed.contains(craft)) {
            return;
        }
        directed.remove(craft);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRelease(CraftReleaseEvent event) {
        Player player = event.getCraft().getNotificationPlayer();
        if (player == null) {
            return;
        }

        if (!manager.getDirectedCrafts().containsKey(player)) {
            return;
        }
        manager.getDirectedCrafts().get(player).remove(event.getCraft());

        if (!manager.getDirectedCrafts().get(player).isEmpty()) {
            return;
        }
        manager.getDirectedCrafts().remove(event.getCraft().getNotificationPlayer());
    }

    @EventHandler
    public void onSignTranslate(SignTranslateEvent event) {
        if (!event.getLine(0).equals(ChatColor.DARK_AQUA + "SquadronDirector") && !event.getLine(1).equals(ChatColor.DARK_BLUE + "[Recon]")) {
            return;
        }
        Map<Player, Craft> reconParentCrafts = SquadronDirectorMain.getInstance().getDirectorManager().getPlayersInReconParentCrafts();
        Map<Player, Location> reconSignLocs = SquadronDirectorMain.getInstance().getDirectorManager().getPlayersInReconSignLocation();
        Player director = null;
        for (Map.Entry<Player, Craft> entry : reconParentCrafts.entrySet()) {
            if (!entry.getValue().equals(event.getCraft()))
                continue;
            director = entry.getKey();
            break;
        }
        if (director == null)
            return;
        Location current = reconSignLocs.get(director);
        MovecraftLocation newLoc = null;
        double distance = Double.MAX_VALUE;
        for (MovecraftLocation ml : event.getLocations()) {
            if (ml.distance(MathUtils.bukkit2MovecraftLoc(current)) >= distance)
                continue;
            newLoc = ml;
            distance = ml.distance(MathUtils.bukkit2MovecraftLoc(current));
        }
        if (newLoc == null) {
            return;
        }
        reconSignLocs.put(director, newLoc.toBukkit(event.getCraft().getW()));
    }
}

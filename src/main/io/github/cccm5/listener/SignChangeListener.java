package io.github.cccm5.listener;

import io.github.cccm5.Formation;
import io.github.cccm5.SquadronDirectorMain;
import io.github.cccm5.managers.DirectorManager;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.Rotation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.craft.ICraft;
import net.countercraft.movecraft.events.CraftPilotEvent;
import net.countercraft.movecraft.utils.BitmapHitBox;
import net.countercraft.movecraft.utils.MathUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CopyOnWriteArrayList;

import static io.github.cccm5.SquadronDirectorMain.ERROR_TAG;
import static io.github.cccm5.SquadronDirectorMain.SUCCESS_TAG;

public class SignChangeListener implements Listener {
    
    DirectorManager manager = SquadronDirectorMain.getInstance().getDirectorManager();

    @EventHandler
    public void onSignPlace(SignChangeEvent e) {
        if (!(ChatColor.stripColor(e.getLine(0)).equalsIgnoreCase("SquadronDirector") || ChatColor.stripColor(e.getLine(0)).equalsIgnoreCase("SDir") || ChatColor.stripColor(e.getLine(0)).equalsIgnoreCase("SD"))) {
            return;
        }
        e.line(0, Component.text("SquadronDirector").color(NamedTextColor.DARK_AQUA));

        String secondLine = ChatColor.stripColor(e.getLine(1));
        if (secondLine == null) {
            return;
        }

        switch (secondLine.toUpperCase()) {
            case "[RELEASE]":
            case "[r]":
                e.line(1, Component.text("[Release]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[LAUNCH]":
            case "[LA]":
                e.line(1, Component.text("[Launch]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[CRUISE]":
            case "[C]":
                e.line(1, Component.text("[Cruise]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[ROTATE]":
            case "[RO]":
                e.line(1, Component.text("[Rotate]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[LEVER]":
            case "[LE]":
                e.line(1, Component.text("[Lever]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[BUTTON]":
            case "[B]":
                e.line(1, Component.text("[Button]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[RECON]":
            case "[RE]":
                e.line(1, Component.text("[Recon]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[REMOTE SIGN]":
            case "[RS]":
                e.line(1, Component.text("[Remote Sign]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[ASCEND]":
            case "[A]":
                e.line(1, Component.text("[Ascend]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[DESCEND]":
            case "[D]":
                e.line(1, Component.text("[Descend]").color(NamedTextColor.DARK_BLUE));
                break;
            case "[FORM UP]":
            case "[FU]":
                e.line(1, Component.text("[Form Up]").color(NamedTextColor.DARK_BLUE));
                String formName = "Echelon";
                if (!e.getLine(2).isEmpty()) {
                    Formation form = Formation.valueOf(e.getLine(2).toUpperCase());
                    if (form == Formation.ECHELON) {
                        formName = "Echelon";
                    } else if (form == Formation.VIC) {
                        formName = "Vic";
                    } else {
                        e.getPlayer().sendMessage(ERROR_TAG + "Invalid formation type: " + e.getLine(2));
                        e.setCancelled(true);
                    }
                }
                e.setLine(2, formName);
                if (e.getLine(3).isEmpty()) {
                    e.setLine(3, "10");
                }
            default:
                e.getPlayer().sendMessage(ERROR_TAG + "Squadron Director sign not recognized!");
                e.setCancelled(true);
                break;
        }
    }


    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        Player player = e.getPlayer();

        if (player.isFlying() && player.getFlySpeed() < 0.05) { // check fly speed instead of playersinrecon in case someone got trapped in recon mode due to server restart or crash
            if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
                if (manager.getPlayersInAimingMode().contains(player)) {
                    player.sendMessage(SUCCESS_TAG + "Leaving Aiming mode.");
                    manager.getPlayersInAimingMode().remove(player);
                    return;
                }
                // they have to have been in recon mode for at least a second before removing it, or bad things could happen
                if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY) || (player.getPotionEffect(PotionEffectType.INVISIBILITY).getDuration() < 2479 * 20)) {
                    player.sendMessage(SUCCESS_TAG + "Leaving Recon Mode.");
                    manager.leaveReconMode(player);
                    e.setCancelled(true);
                    return;
                }
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR) {
                if (!manager.getPlayersInAimingMode().contains(player)) {
                    player.sendMessage(SUCCESS_TAG + "Entering Aiming mode. Right-click to fire your crafts' weapons.");
                    manager.getPlayersInAimingMode().add(player);
                    return;
                }
                manager.fireReconWeapons(player);
                e.setCancelled(true);
                return;
            }
        }

        if (!(e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK)) {
            return;
        }

        if (e.getClickedBlock() == null || !Tag.SIGNS.isTagged(e.getClickedBlock().getType())) {
            return;
        }

        Sign sign = (Sign) e.getClickedBlock().getState(false);
        boolean signIsOnCraft = false;
        Craft craftSignIsOn = null;
        MovecraftLocation mloc = MathUtils.bukkit2MovecraftLoc(e.getClickedBlock().getLocation());
        for (Craft craft : CraftManager.getInstance().getCraftsInWorld(e.getClickedBlock().getWorld())) {
            if (craft == null || craft.getDisabled()) {
                continue;
            }
            if (craft.getHitBox().contains(mloc)) {
                signIsOnCraft = true;
                craftSignIsOn = craft;
                break;
            }
        }

        String secondLine = ChatColor.stripColor(sign.getLine(1));
        if (sign.getLine(0).equals(ChatColor.DARK_AQUA + "SquadronDirector")) {
            if(!signIsOnCraft) {
                player.sendMessage(ERROR_TAG + "The command sign is not on a piloted craft!");
                return;
            }

            switch (secondLine) {
                case "[Release]":
                    if (!player.hasPermission("Squadron.sign.release")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    manager.releaseSquadrons(player);
                    e.setCancelled(true);
                    return;

                case "[Launch]":
                    if (!player.hasPermission("Squadron.sign.launch")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    manager.launchModeToggle(player);
                    e.setCancelled(true);
                    return;

                case "[Cruise]":
                    if (!player.hasPermission("Squadron.sign.cruise")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    manager.cruiseToggle(player);
                    e.setCancelled(true);
                    return;

                case "[Ascend]":
                    if (!player.hasPermission("Squadron.sign.ascend")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    manager.ascendToggle(player);
                    e.setCancelled(true);
                    return;

                case "[Descend]":
                    if (!player.hasPermission("Squadron.sign.Descend")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    manager.descendToggle(player);
                    e.setCancelled(true);
                    return;

                case "[Form Up]":
                    if (!player.hasPermission("Squadron.sign.formup")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    Formation form = Formation.valueOf(ChatColor.stripColor(sign.getLine(2)).toUpperCase());
                    int spacing;
                    try {
                        spacing = Integer.parseInt(sign.getLine(3));
                    }
                    catch (NumberFormatException ex) {
                        spacing = 10;
                    }

                    if (spacing> SquadronDirectorMain.getInstance().getConfig().getInt("Max spacing")) {
                        player.sendMessage(ERROR_TAG + "Spacing is too high!");
                        return;
                    }
                    manager.toggleFormUp(player, form, spacing);
                    e.setCancelled(true);
                    return;

                case "[Rotate]":
                    if (!player.hasPermission("Squadron.sign.rotate")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                        manager.rotateSquadron(player, Rotation.ANTICLOCKWISE);
                    } else {
                        manager.rotateSquadron(player, Rotation.CLOCKWISE);
                    }
                    e.setCancelled(true);
                    return;

                case "[Recon]":
                    if(!player.hasPermission("Squadron.sign.recon")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }

                    if (manager.getDirectedCrafts().get(player) == null || manager.getDirectedCrafts().get(player).isEmpty()) {
                        player.sendMessage(ERROR_TAG+"You have no squadron craft to recon!");
                        return;
                    }
                    manager.getPlayersInReconParentCrafts().put(player,craftSignIsOn);
                    manager.getPlayersInReconSignLocation().put(player,player.getLocation().clone());
                    manager.getPlayersInReconMode().add(player);
                    player.sendMessage(SUCCESS_TAG+"You have entered Recon Mode. Left click leaves recon mode, right click triggers any active weapon systems. Strafing up, down, left, or right will move the squadron.");
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 2480 * 20, 1, false, false));
                    player.setInvulnerable(true);
                    player.setWalkSpeed(0.01F);
                    player.setFlySpeed(0.01F);
                    e.setCancelled(true);
                    return;

                case "[Lever]":
                    if (!player.hasPermission("Squadron.sign.lever")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    if((manager.getPlayersWeaponControl().get(player)!=null)&&(manager.getPlayersWeaponControl().get(player).equals("LEVER"))) {
                        player.sendMessage(SUCCESS_TAG + "You have released lever control");
                        manager.getPlayersWeaponControl().remove(player);
                    } else {
                        player.sendMessage(SUCCESS_TAG + "You are now controlling levers. In Recon Mode, right click to activate a lever on each craft");
                        manager.getPlayersWeaponControl().put(player, "LEVER");
                    }
                    return;

                case "[Button]":
                    if (!player.hasPermission("Squadron.sign.button")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    if ((manager.getPlayersWeaponControl().get(player)!=null)&&(manager.getPlayersWeaponControl().get(player).equals("BUTTON"))) {
                        player.sendMessage(SUCCESS_TAG + "You have released button control");
                        manager.getPlayersWeaponControl().remove(player);
                    }
                    else {
                        player.sendMessage(SUCCESS_TAG + "You are now controlling buttons. In Recon Mode, right click to activate a button on each craft");
                        manager.getPlayersWeaponControl().put(player, "BUTTON");
                    }
                    return;

                case "[Remote Sign]":
                    if (!player.hasPermission("Squadron.sign.remote")) {
                        player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                        return;
                    }
                    String targString = sign.getLine(2);
                    if((manager.getPlayersWeaponControl().get(player)!=null)&&(manager.getPlayersWeaponControl().get(player).equalsIgnoreCase(targString))) {
                        player.sendMessage(SUCCESS_TAG + "You have released remote sign control");
                        manager.getPlayersWeaponControl().remove(player);
                    }
                    else {
                        player.sendMessage(SUCCESS_TAG + "You are now controlling "+targString+" signs. In Recon Mode, right click to activate a sign on each craft");
                        manager.getPlayersWeaponControl().put(player, targString);
                    }
                    return;

                default:
                    player.sendMessage(ERROR_TAG + "Squadron Director sign not recognized!");
                    return;
            }
        }

        // now check to see if they are trying to launch a new craft
        if(manager.playerInLaunchMode(e.getPlayer()) && e.getAction() == Action.LEFT_CLICK_BLOCK) {

            String foundCraft = null;
            for (String craftName : SquadronDirectorMain.getInstance().getConfig().getStringList("Craft types")) {
                if (sign.getLine(0).equalsIgnoreCase(craftName)) {
                    foundCraft = craftName;
                }
            }
            if (foundCraft == null) {
                return;
            }

            if (!player.hasPermission("Squadron." + foundCraft)) {
                player.sendMessage(ERROR_TAG + "You do not have permissions to direct that craft type!");
                return;
            }

            if (manager.getDirectedCrafts().get(player) != null) {
                if (manager.getDirectedCrafts().get(player).size() == SquadronDirectorMain.getInstance().getConfig().getInt("Max crafts")) {
                    player.sendMessage(ERROR_TAG + "You are already directing the maximum number of crafts!");
                    return;
                }
            }

            if (signIsOnCraft) {
                if (CraftManager.getInstance().getPlayerFromCraft(craftSignIsOn) == null) {
                    player.sendMessage(ERROR_TAG + "This craft is already being directed!");
                    return;
                }
            }

            // now try to detect the craft
            Location loc = e.getClickedBlock().getLocation();
            MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            CraftType type = CraftManager.getInstance().getCraftTypeFromString(foundCraft);
            Craft c = new ICraft(type, loc.getWorld());

            if (c.getType().getCruiseOnPilot()) {
                player.sendMessage(ERROR_TAG + "You can not direct a CruiseOnPilot craft!");
                return;
            }

            // determine the cruise direction of the craft, release it if there is no cruise or helm sign
            Craft finalCraftSignIsOn = craftSignIsOn;
            new BukkitRunnable() {
                @Override
                public void run() {
                    manager.determineCruiseDirection(c);
                }
            }.runTaskLater(SquadronDirectorMain.getInstance(), (10));


            if (signIsOnCraft) { // stop the parent craft from moving during detection, and remove the child craft from the parent to prevent overlap
                craftSignIsOn.setProcessing(true);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        BitmapHitBox parentHitBox = finalCraftSignIsOn.getHitBox();
                        parentHitBox.removeAll(c.getHitBox());
                    }
                }.runTaskLater(SquadronDirectorMain.getInstance(), (10));
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        finalCraftSignIsOn.setProcessing(false);
                    }
                }.runTaskLater(SquadronDirectorMain.getInstance(), (20));
            }
            c.detect(null, e.getPlayer(), startPoint);
            Bukkit.getServer().getPluginManager().callEvent(new CraftPilotEvent(c, CraftPilotEvent.Reason.PLAYER));

            CopyOnWriteArrayList<Craft> cl = manager.getDirectedCrafts().get(player);
            if (cl == null) {
                cl = new CopyOnWriteArrayList<>();
            }
            cl.add(c);
            manager.getDirectedCrafts().put(player, cl);
            c.setLastCruiseUpdate(System.currentTimeMillis());
            player.sendMessage(SUCCESS_TAG + "You have attempted to launch a craft of type " + foundCraft);

            e.setCancelled(true);
        }
    }
}

package io.github.cccm5.listener;

import io.github.cccm5.SquadronDirectorMain;
import io.github.cccm5.managers.DirectorManager;
import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.Rotation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.craft.ICraft;
import net.countercraft.movecraft.events.CraftPilotEvent;
import net.countercraft.movecraft.utils.BitmapHitBox;
import net.countercraft.movecraft.utils.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;


import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.github.cccm5.SquadronDirectorMain.ERROR_TAG;
import static io.github.cccm5.SquadronDirectorMain.SUCCESS_TAG;

public class PlayerListener implements Listener {
    
    private final DirectorManager manager = SquadronDirectorMain.getInstance().getDirectorManager();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }

        Player player = event.getPlayer();
        if (manager.getPlayersInReconSignLocation().get(player) == null) {
            return;
        }

        if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return;
        }

        if (player.getPotionEffect(PotionEffectType.INVISIBILITY).getDuration() > 2479 * 20) { // wait a second before accepting any more move inputs
            return;
        }

        // make Movecraft not release the craft due to the player being in recon, and not on the craft
        Craft playerCraft = CraftManager.getInstance().getCraftByPlayer(player);
        if (playerCraft != null) {
            HandlerList handlers = event.getHandlers();
            RegisteredListener[] listeners = handlers.getRegisteredListeners();
            for (RegisteredListener l : listeners) {
                if (!l.getPlugin().isEnabled()) {
                    continue;
                }
                if (l.getListener() instanceof net.countercraft.movecraft.listener.PlayerListener) {
                    net.countercraft.movecraft.listener.PlayerListener pl = (net.countercraft.movecraft.listener.PlayerListener) l.getListener();
                    Class plclass = net.countercraft.movecraft.listener.PlayerListener.class;
                    try {
                        Field field = plclass.getDeclaredField("timeToReleaseAfter");
                        field.setAccessible(true);
                        final Map<Craft, Long> timeToReleaseAfter = (Map<Craft, Long>) field.get(pl);
                        if (timeToReleaseAfter.containsKey(playerCraft)) {
                            timeToReleaseAfter.put(playerCraft, System.currentTimeMillis() + 30000);
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        }
        Craft leadCraft = null;
        if (!manager.getDirectedCrafts().containsKey(player)) {
            return;
        }
        for (Craft c : manager.getDirectedCrafts().get(player)) {
            if ((c == null) || (c.getHitBox().isEmpty())) {
                continue;
            }
            manager.determineCruiseDirection(c);

            leadCraft = c;
            break;
        }
        if (leadCraft == null) {
            return;
        }

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dy = event.getTo().getY() - event.getFrom().getY();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        if (manager.getDirectedCrafts().get(player) == null || manager.getDirectedCrafts().get(player).isEmpty()) {
            return;
        }

        if (dy > 0.07) {
            event.setCancelled(true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 2480 * 20, 1, true, false));
            if (manager.getPlayersStrafingUpDown().get(player) == null) {
                manager.getPlayersStrafingUpDown().put(player, 1);
                player.sendMessage(SUCCESS_TAG + "Ascent enabled");
                return;
            }
            if (manager.getPlayersStrafingUpDown().get(player) == 2) {
                manager.getPlayersStrafingUpDown().remove(player);
                player.sendMessage(SUCCESS_TAG + "Descent disabled");
                return;
            }
        }

        if (dy < -0.07) {
            event.setCancelled(true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 2480 * 20, 1, true, false));
            if (manager.getPlayersStrafingUpDown().get(player) == null) {
                manager.getPlayersStrafingUpDown().put(player, 2);
                player.sendMessage(SUCCESS_TAG + "Descent enabled");
                return;
            }
            if (manager.getPlayersStrafingUpDown().get(player) == 1) {
                manager.getPlayersStrafingUpDown().remove(player);
                player.sendMessage(SUCCESS_TAG + "Ascent disabled");
                return;
            }
        }

        // ship faces west
        if (leadCraft.getCruiseDirection() == CruiseDirection.fromBlockFace(BlockFace.WEST)) {
            if (dz < -0.07) {
                event.setCancelled(true);
                if (manager.getPlayersStrafingLeftRight().get(player) == null) {
                    manager.getPlayersStrafingLeftRight().put(player, 2);
                    player.sendMessage(SUCCESS_TAG + "Strafe Right enabled");
                    return;
                }
                if (manager.getPlayersStrafingLeftRight().get(player) == 1) {
                    manager.getPlayersStrafingLeftRight().remove(player);
                    player.sendMessage(SUCCESS_TAG + "Strafe Left disabled");
                    return;
                }
            }
            if (dz > 0.07) {
                event.setCancelled(true);
                if (manager.getPlayersStrafingLeftRight().get(player) == null) {
                    manager.getPlayersStrafingLeftRight().put(player, 1);
                    player.sendMessage(SUCCESS_TAG + "Strafe Left enabled");
                    return;
                }
                if (manager.getPlayersStrafingLeftRight().get(player) == 2) {
                    manager.getPlayersStrafingLeftRight().remove(player);
                    player.sendMessage(SUCCESS_TAG + "Strafe Right disabled");
                    return;
                }
            }
            if (dx < -0.07) {
                event.setCancelled(true);
                manager.cruiseEnable(player);
            }
            if (dx > 0.07) {
                event.setCancelled(true);
                manager.cruiseDisable(player);
            }
        }

        // ship faces east
        if (leadCraft.getCruiseDirection() == CruiseDirection.fromBlockFace(BlockFace.EAST)) {
            if (dz > 0.07) {
                event.setCancelled(true);
                if (manager.getPlayersStrafingLeftRight().get(player) == null) {
                    manager.getPlayersStrafingLeftRight().put(player, 2);
                    player.sendMessage(SUCCESS_TAG + "Strafe Right enabled");
                    return;
                }
                if (manager.getPlayersStrafingLeftRight().get(player) == 1) {
                    manager.getPlayersStrafingLeftRight().remove(player);
                    player.sendMessage(SUCCESS_TAG + "Strafe Left disabled");
                    return;
                }
            }
            if (dz < -0.07) {
                event.setCancelled(true);
                if (manager.getPlayersStrafingLeftRight().get(player) == null) {
                    manager.getPlayersStrafingLeftRight().put(player, 1);
                    player.sendMessage(SUCCESS_TAG + "Strafe Left enabled");
                    return;
                }
                if (manager.getPlayersStrafingLeftRight().get(player) == 2) {
                    manager.getPlayersStrafingLeftRight().remove(player);
                    player.sendMessage(SUCCESS_TAG + "Strafe Right disabled");
                    return;
                }
            }
            if (dx > 0.07) {
                event.setCancelled(true);
                manager.cruiseEnable(player);
            }
            if (dx < -0.07) {
                event.setCancelled(true);
                manager.cruiseDisable(player);
            }
        }

        // ship faces north
        if (leadCraft.getCruiseDirection() == CruiseDirection.fromBlockFace(BlockFace.NORTH)) {
            if (dx < -0.07) {
                event.setCancelled(true);
                if (manager.getPlayersStrafingLeftRight().get(player) == null) {
                    manager.getPlayersStrafingLeftRight().put(player, 2);
                    player.sendMessage(SUCCESS_TAG + "Strafe Right enabled");
                    return;
                }
                if (manager.getPlayersStrafingLeftRight().get(player) == 1) {
                    manager.getPlayersStrafingLeftRight().remove(player);
                    player.sendMessage(SUCCESS_TAG + "Strafe Left disabled");
                    return;
                }
            }
            if (dx > 0.07) {
                event.setCancelled(true);
                if (manager.getPlayersStrafingLeftRight().get(player) == null) {
                    manager.getPlayersStrafingLeftRight().put(player, 1);
                    player.sendMessage(SUCCESS_TAG + "Strafe Left enabled");
                    return;
                }
                if (manager.getPlayersStrafingLeftRight().get(player) == 2) {
                    manager.getPlayersStrafingLeftRight().remove(player);
                    player.sendMessage(SUCCESS_TAG + "Strafe Right disabled");
                    return;
                }
            }
            if (dz > 0.07) {
                event.setCancelled(true);
                manager.cruiseEnable(player);
            }
            if (dz < -0.07) {
                event.setCancelled(true);
                manager.cruiseDisable(player);
            }
        }

        // ship faces south
        if (leadCraft.getCruiseDirection() == CruiseDirection.fromBlockFace(BlockFace.SOUTH)) {
            if (dx > 0.07) {
                event.setCancelled(true);
                if (manager.getPlayersStrafingLeftRight().get(player) == null) {
                    manager.getPlayersStrafingLeftRight().put(player, 2);
                    player.sendMessage(SUCCESS_TAG + "Strafe Right enabled");
                    return;
                }
                if (manager.getPlayersStrafingLeftRight().get(player) == 1) {
                    manager.getPlayersStrafingLeftRight().remove(player);
                    player.sendMessage(SUCCESS_TAG + "Strafe Left disabled");
                    return;
                }
            }
            if (dx < -0.07) {
                event.setCancelled(true);
                if (manager.getPlayersStrafingLeftRight().get(player) == null) {
                    manager.getPlayersStrafingLeftRight().put(player, 1);
                    player.sendMessage(SUCCESS_TAG + "Strafe Left enabled");
                    return;
                }
                if (manager.getPlayersStrafingLeftRight().get(player) == 2) {
                    manager.getPlayersStrafingLeftRight().remove(player);
                    player.sendMessage(SUCCESS_TAG + "Strafe Right disabled");
                    return;
                }
            }
            if (dz < -0.07) {
                event.setCancelled(true);
                manager.cruiseEnable(player);
            }
            if (dz > 0.07) {
                event.setCancelled(true);
                manager.cruiseDisable(player);
            }
        }
    }


    //Prevent players in recon mode from teleporting to other worlds
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld().equals(event.getTo().getWorld()))
            return;
        if (!manager.getPlayersInReconParentCrafts().containsKey(event.getPlayer()))
            return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.leaveReconMode(event.getPlayer());
    }


}

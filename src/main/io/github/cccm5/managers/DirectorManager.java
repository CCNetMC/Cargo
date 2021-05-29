package io.github.cccm5.managers;

import io.github.cccm5.Formation;
import io.github.cccm5.SquadronDirectorMain;
import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.Rotation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.utils.BitmapHitBox;
import net.countercraft.movecraft.utils.MutableHitBox;
import net.countercraft.movecraft.utils.TeleportUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.material.Button;
import org.bukkit.material.Lever;
import org.bukkit.material.MaterialData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.github.cccm5.SquadronDirectorMain.ERROR_TAG;
import static io.github.cccm5.SquadronDirectorMain.SUCCESS_TAG;

public class DirectorManager extends BukkitRunnable {

    private final ConcurrentHashMap<Player, CopyOnWriteArrayList<Craft>> directedCrafts = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Player, Integer> playersStrafingUpDown = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Player, Integer> playersStrafingLeftRight = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Player, Formation> playerFormations = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Player, Location> playersInReconSignLocation = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Player, Craft> playersInReconParentCrafts = new ConcurrentHashMap<>();
    private HashSet<Player> playersInAimingMode = new HashSet<>();
    private HashSet<Player> playersInReconMode = new HashSet<>();
    private ConcurrentHashMap<Player, String> playersWeaponControl = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Player, Integer> playersWeaponNumClicks = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Player, Integer> playersFormingUp = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Craft, Integer> pendingMoveDX = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Craft, Integer> pendingMoveDY = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Craft, Integer> pendingMoveDZ = new ConcurrentHashMap<>();

    private CopyOnWriteArrayList<Player> playersInLaunchMode = new CopyOnWriteArrayList<>();

    public DirectorManager() {
        runTaskTimer(SquadronDirectorMain.getInstance(), 20, 20);
    }

    @Override
    public void run() {
        for (Player p : directedCrafts.keySet()) {
            removeDeadCrafts(p);
        }

        for (Player player : playersInReconMode) {
            handleReconPlayers(player);
        }

        // update the directed crafts every 10 seconds, or Movecraft will remove them due to inactivity
        for (CopyOnWriteArrayList<Craft> cl : directedCrafts.values()) {
            for (Craft c : cl) {
                if (c == null)
                    continue;
                if (c.getCruising())
                    continue;
                if (System.currentTimeMillis() - c.getLastCruiseUpdate() > 10000) {
                    c.setLastCruiseUpdate(System.currentTimeMillis());
                }
            }
        }

        // now move any strafing crafts
        for (Player p : playersStrafingUpDown.keySet()) {
            removeDeadCrafts(p);
            for (Craft c : directedCrafts.get(p)) {
                if (c == null)
                    continue;
                if (playersStrafingUpDown.get(p) == 1) {
                    c.setCruising(true);
                    c.setCruiseDirection(CruiseDirection.UP);
                    //updatePendingMove(c, 0, 1, 0);
                } else if (playersStrafingUpDown.get(p) == 2) {
                    //updatePendingMove(c, 0, -1, 0);
                    c.setCruising(true);
                    c.setCruiseDirection(CruiseDirection.DOWN);
                }
            }
        }

        for (Player p : playersStrafingLeftRight.keySet()) {
            for (Craft c : directedCrafts.get(p)) {
                if (c == null)
                    continue;
                determineCruiseDirection(c);
                strafeLeftRight(c, playersStrafingLeftRight.get(p));
            }
        }

        // and make the crafts form up that are supposed to
        for (Player p : playersFormingUp.keySet()) {
            Formation form = playerFormations.get(p);
            if (form == Formation.ECHELON)
                formUpEchelon(p);
            else if (form == Formation.VIC) {
                formUpVic(p);
            }
        }

        // update the sign positions of any players in recon mode
        for (Player p : playersInReconSignLocation.keySet()) {
            if (playersInReconParentCrafts.get(p) == null) {
                return;
            }

            Craft craft = playersInReconParentCrafts.get(p);
            Location signLoc = null;
            for (MovecraftLocation tLoc : craft.getHitBox()) {
                Block block = tLoc.toBukkit(craft.getWorld()).getBlock();
                if (!Tag.SIGNS.isTagged(block.getType())) {
                    continue;
                }

                Sign sign = (Sign) block.getState(false);
                if (sign.getLine(1).equals(ChatColor.DARK_BLUE + "[Recon]")) {
                    craft.setCruiseDirection(CruiseDirection.fromRaw(sign.getRawData()));
                    signLoc = sign.getLocation();
                }
                if (signLoc == null) {
                    signLoc = craft.getHitBox().getMidPoint().toBukkit(craft.getWorld());
                }
                playersInReconSignLocation.put(p, signLoc);
            }
        }

        performPendingMoves();
    }


    public boolean playerControllingWeapon(Player player, String type) {
        return playersWeaponControl.containsKey(player) && playersWeaponControl.get(player).equals(type);
    }

    public boolean playerDirectingAnyCrafts(Player player) {
        return !directedCrafts.getOrDefault(player, new CopyOnWriteArrayList<>()).isEmpty();
    }

    public boolean playerInLaunchMode(Player player) {
        return playersInLaunchMode.contains(player);
    }

    public void removeDeadCrafts(Player player) {
        if (!playerDirectingAnyCrafts(player))
            return;
        CopyOnWriteArrayList<Craft> craftsToRemove = new CopyOnWriteArrayList<Craft>();

        for (Craft c : directedCrafts.get(player)) {
            if (c == null) {
                craftsToRemove.add(c);
                continue;
            }
            if (c.getHitBox().isEmpty() || c.getSinking()) {
                craftsToRemove.add(c);
            }
        }
        directedCrafts.get(player).removeAll(craftsToRemove);
    }

    public boolean pathObstructed(Craft c, int dx, int dy, int dz) {
        for (MovecraftLocation loc : c.getHitBox()) {
            MovecraftLocation translated = loc.translate(dx, dy, dz);
            Block test = translated.toBukkit(c.getWorld()).getBlock();
            if (c.getHitBox().contains(translated) || test.getType().isAir() || c.getType().getPassthroughBlocks().contains(test.getType()))
                continue;
            return true;
        }
        return false;
    }

    public void updatePendingMove(Craft c, int dx, int dy, int dz) {
        if (pendingMoveDX.get(c) != null) {
            dx += pendingMoveDX.get(c);
        }
        pendingMoveDX.put(c, dx);
        if (pendingMoveDY.get(c) != null) {
            dy += pendingMoveDY.get(c);
        }
        pendingMoveDY.put(c, dy);
        if (pendingMoveDZ.get(c) != null) {
            dz += pendingMoveDZ.get(c);
        }
        pendingMoveDZ.put(c, dz);
    }

    public void performPendingMoves() {
        for (Craft c : pendingMoveDX.keySet()) {
            int dx = 0;
            int dy = 0;
            int dz = 0;
            if (pendingMoveDX.get(c) != null) {
                dx += pendingMoveDX.get(c);
            }
            if (pendingMoveDY.get(c) != null) {
                dy += pendingMoveDY.get(c);
            }
            if (pendingMoveDZ.get(c) != null) {
                dz += pendingMoveDZ.get(c);
            }
            if (pathObstructed(c, dx, dy, dz))
                continue;
            c.translate(c.getWorld(), dx, dy, dz);
        }
        pendingMoveDX.clear();
        pendingMoveDY.clear();
        pendingMoveDZ.clear();
    }

    public void determineCruiseDirection(Craft craft) {
        if (craft == null)
            return;

        boolean foundCruise = false;
        boolean foundHelm = false;
        for (MovecraftLocation tLoc : craft.getHitBox()) {
            Block block = tLoc.toBukkit(craft.getWorld()).getBlock();
            if (!Tag.SIGNS.isTagged(block.getType())) {
                continue;
            }
            Sign sign = (Sign) block.getState(false);
            if (sign.getLine(0).equalsIgnoreCase("Cruise: OFF") || sign.getLine(0).equalsIgnoreCase("Cruise: ON")) {
                craft.setCruiseDirection(CruiseDirection.fromRaw(sign.getRawData()));
                foundCruise = true;
            }
            if (ChatColor.stripColor(sign.getLine(0)).equals("\\  ||  /") &&
                    ChatColor.stripColor(sign.getLine(1)).equals("==      ==") &&
                    ChatColor.stripColor(sign.getLine(2)).equals("/  ||  \\")) {
                foundHelm = true;
            }
        }

        if (!foundCruise) {
            craft.getNotificationPlayer().sendMessage(ERROR_TAG + "This craft has no Cruise sign and can not be directed");
            CraftManager.getInstance().removeCraft(craft, CraftReleaseEvent.Reason.FORCE);
        }

        if (!foundHelm) {
            craft.getNotificationPlayer().sendMessage(ERROR_TAG + "This craft has no Helm sign and can not be directed");
            CraftManager.getInstance().removeCraft(craft, CraftReleaseEvent.Reason.FORCE);
        }
    }

    public void strafeLeftRight(Craft c, Integer leftRight) {
        boolean bankLeft = (leftRight == 1);
        boolean bankRight = (leftRight == 2);
        int dx = 0;
        int dz = 0;
        CruiseDirection cruiseDirection = c.getCruiseDirection();
        // ship faces west
        if (cruiseDirection == CruiseDirection.EAST) {
            if (bankRight) {
                dz = (-1 - c.getType().getCruiseSkipBlocks()) >> 1;
            }
            if (bankLeft) {
                dz = (1 + c.getType().getCruiseSkipBlocks()) >> 1;
            }
        }
        // ship faces east
        if (cruiseDirection == CruiseDirection.WEST) {
            if (bankLeft) {
                dz = (-1 - c.getType().getCruiseSkipBlocks()) >> 1;
            }
            if (bankRight) {
                dz = (1 + c.getType().getCruiseSkipBlocks()) >> 1;
            }
        }
        // ship faces north
        if (c.getCruiseDirection() == CruiseDirection.SOUTH) {
            if (bankRight) {
                dx = (-1 - c.getType().getCruiseSkipBlocks()) >> 1;
            }
            if (bankLeft) {
                dx = (1 + c.getType().getCruiseSkipBlocks()) >> 1;
            }
        }
        // ship faces south
        if (c.getCruiseDirection() == CruiseDirection.NORTH) {
            if (bankLeft) {
                dx = (-1 - c.getType().getCruiseSkipBlocks()) >> 1;
            }
            if (bankRight) {
                dx = (1 + c.getType().getCruiseSkipBlocks()) >> 1;
            }
        }
        updatePendingMove(c, dx, 0, dz);
    }

    public void releaseSquadrons(Player player) {
        if (directedCrafts.get(player) == null || directedCrafts.get(player).isEmpty()) {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to release");
            return;
        }

        int numCraft = 0;
        for (Craft c : directedCrafts.get(player)) {
            CraftManager.getInstance().removeCraft(c, CraftReleaseEvent.Reason.PLAYER);
            numCraft++;
        }

        playersFormingUp.remove(player);
        playersStrafingUpDown.remove(player);
        playersStrafingLeftRight.remove(player);
        directedCrafts.get(player).clear();

        if (numCraft > 1) {
            player.sendMessage(SUCCESS_TAG + "You have released " + numCraft + " squadron crafts");
        } else if (numCraft > 0) {
            player.sendMessage(SUCCESS_TAG + "You have released " + numCraft + " squadron craft");
        } else {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to release");
        }
    }

    public void cruiseToggle(Player player) {
        if (directedCrafts.get(player) == null || directedCrafts.get(player).isEmpty()) {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to direct");
            return;
        }
        if (allCraftsNotCruising(directedCrafts.get(player)))
            return;
        for (Craft c : directedCrafts.get(player)) {
            if (c == null)
                continue;
            boolean setCruise = !c.getCruising();
            determineCruiseDirection(c);
            c.setCruising(setCruise);
        }
    }

    public void cruiseEnable(Player player) {
        if (directedCrafts.get(player) == null || directedCrafts.get(player).isEmpty()) {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to direct");
            return;
        }
        if (allCraftsCruising(directedCrafts.get(player)))
            return;
        player.sendMessage(SUCCESS_TAG + "Cruise enabled");
        for (Craft c : directedCrafts.get(player)) {
            if (c == null)
                continue;
            determineCruiseDirection(c);
            c.setCruising(true);
        }
    }

    public void cruiseDisable(Player player) {
        if (directedCrafts.get(player) == null || directedCrafts.get(player).isEmpty()) {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to direct");
            return;
        }
        player.sendMessage(SUCCESS_TAG + "Cruise disabled");
        for (Craft c : directedCrafts.get(player)) {
            if (c == null)
                continue;
            determineCruiseDirection(c);
            c.setCruising(false);
        }
    }

    public void leverControl(Player player) {
        if (playerControllingWeapon(player, "LEVER")) {
            player.sendMessage(SUCCESS_TAG + "You have released lever control");
            playersWeaponControl.remove(player);
        } else {
            player.sendMessage(SUCCESS_TAG + "You are now controlling levers. In Recon Mode, right click to activate a lever on each craft");
            playersWeaponControl.put(player, "LEVER");
        }
    }

    public void buttonControl(Player player) {
        if (playerControllingWeapon(player, "BUTTON")) {
            player.sendMessage(SUCCESS_TAG + "You have released button control");
            playersWeaponControl.remove(player);
        } else {
            player.sendMessage(SUCCESS_TAG + "You are now controlling buttons. In Recon Mode, right click to activate a button on each craft");
            playersWeaponControl.put(player, "BUTTON");
        }
    }

    public void ascendToggle(Player player) {
        if (directedCrafts.get(player) == null || directedCrafts.get(player).isEmpty()) {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to direct");
            return;
        }

        if (playersStrafingUpDown.get(player) == null) {
            playersStrafingUpDown.put(player, 1);
            player.sendMessage(SUCCESS_TAG + "Ascent enabled");
            return;
        }
        if (playersStrafingUpDown.get(player) == 1) {
            playersStrafingUpDown.remove(player);
            player.sendMessage(SUCCESS_TAG + "Ascent disabled");
            return;
        }
        playersStrafingUpDown.put(player, 1);
        player.sendMessage(SUCCESS_TAG + "Ascent enabled");
    }

    public void descendToggle(Player player) {
        if (directedCrafts.get(player) == null || directedCrafts.get(player).isEmpty()) {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to direct");
            return;
        }
        if (playersStrafingUpDown.get(player) == null) {
            playersStrafingUpDown.put(player, 2);
            player.sendMessage(SUCCESS_TAG + "Descent enabled");
            return;
        }
        if (playersStrafingUpDown.get(player) == 2) {
            playersStrafingUpDown.remove(player);
            player.sendMessage(SUCCESS_TAG + "Descent disabled");
            return;
        }
        playersStrafingUpDown.put(player, 1);
        player.sendMessage(SUCCESS_TAG + "Descent enabled");
    }

    public boolean allCraftsCruising(Collection<Craft> crafts) {
        int cruising = 0;
        for (Craft c : crafts) {
            if (!c.getCruising())
                continue;
            cruising++;
        }
        return cruising == crafts.size();
    }

    public boolean allCraftsNotCruising(Collection<Craft> crafts) {
        int notCruising = 0;
        for (Craft c : crafts) {
            if (c.getCruising())
                continue;
            notCruising++;
        }
        return notCruising == crafts.size();
    }

    public void leaveReconMode(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        player.setWalkSpeed(0.2F);
        player.setFlySpeed(0.1F);

        if (playersInReconSignLocation.get(player) != null) {
            TeleportUtils.teleport(player, playersInReconSignLocation.get(player), 0.0F);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setInvulnerable(false);
        }

        playersInReconMode.remove(player);
        playersInReconParentCrafts.remove(player);
        playersInReconSignLocation.remove(player);
    }

    public void fireReconWeapons(Player player) {
        if (playersWeaponControl.get(player) == null) {
            return;
        }
        if (directedCrafts.get(player) == null || directedCrafts.get(player).isEmpty()) {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to direct");
            return;
        }

        int numFound = 0;
        String targString;

        if (playersWeaponControl.get(player).equals("LEVER")) {
            targString = "LEVER";
        } else if (playersWeaponControl.get(player).equals("BUTTON")) {
            targString = "BUTTON";
        } else {
            targString = playersWeaponControl.get(player);
        }

        if (playersWeaponNumClicks.get(player) == null) {
            playersWeaponNumClicks.put(player, 0);
        } else {
            playersWeaponNumClicks.put(player, playersWeaponNumClicks.get(player) + 1);
        }

        for (Craft craft : directedCrafts.get(player)) {
            ArrayList<Block> targBlocks = new ArrayList<>();
            for (MovecraftLocation tLoc : craft.getHitBox()) {
                Block block = tLoc.toBukkit(craft.getWorld()).getBlock();
                if (targString.equals("BUTTON") && Tag.BUTTONS.isTagged(block.getType())) {
                    targBlocks.add(block);
                    numFound++;
                }

                else if (targString.equals("LEVER") && block.getType() == Material.LEVER) {
                    targBlocks.add(block);
                    numFound++;
                }

                else {
                    BlockState state = block.getState(false);
                    if (state instanceof Sign) {
                        Sign s = (Sign) state;
                        for (String line: s.getLines()) {
                            if (ChatColor.stripColor(line).equalsIgnoreCase(targString)) {
                                targBlocks.add(block);
                                numFound++;
                            }
                        }
                    }
                }
            }

            if (targString.equals("LEVER")) {
                for (Block block : targBlocks) {
                    if (block.getType() != Material.LEVER) {
                        continue;
                    }
                    Switch lever = (Switch) block.getState(false).getBlockData();
                    lever.setPowered(!lever.isPowered());
                    block.setBlockData(lever, true);
                    //block.getState(false).update(true, true);
                }
            } else if (targString.equals("BUTTON")) {
                for (Block block : targBlocks) {
                    if (!Tag.BUTTONS.isTagged(block.getType())) {
                        continue;
                    }
                    Switch button = (Switch) block.getBlockData();
                    button.setPowered(!button.isPowered());
                    block.setBlockData(button, true);
                    //block.getState(false).update(true, true);
                }
            } else {
                Block targBlock = targBlocks.get(playersWeaponNumClicks.get(player) % numFound); // the point of this is to activate a different sign each time you click
                PlayerInteractEvent newEvent = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getItemOnCursor(), targBlock, BlockFace.EAST);
                Bukkit.getServer().getPluginManager().callEvent(newEvent);
            }
        }

        if (numFound == 0) {
            player.sendMessage(ERROR_TAG + "No triggers found for firing");
            return;
        }

        player.sendMessage(SUCCESS_TAG + "Triggered " + numFound + " devices");
    }

    public void handleReconPlayers(Player player) {
        if ((directedCrafts.get(player) == null) || (directedCrafts.get(player).isEmpty())) {
            player.sendMessage(ERROR_TAG + "You no longer have any craft to direct. Leaving Recon Mode.");
            leaveReconMode(player);
            return;
        }

        int leadX = 0;
        int leadY = 0;
        int leadZ = 0;
        Craft leadCraft = null;
        for (Craft c : directedCrafts.get(player)) {
            if (c == null || c.getHitBox().isEmpty()) {
                continue;
            }
            determineCruiseDirection(c);

            if (leadY == 0) {
                leadX = c.getHitBox().getMidPoint().getX();
                leadY = c.getHitBox().getMidPoint().getY();
                leadZ = c.getHitBox().getMidPoint().getZ();
            }

            if (leadCraft == null)
                leadCraft = c;
        }
        Location loc = new Location(player.getWorld(), leadX, leadY + 50, leadZ);
        if (playersInAimingMode.contains(player)) {
            // ship faces west
            switch (leadCraft.getCruiseDirection()) {
                case EAST:
                    loc = new Location(player.getWorld(), leadX - (leadCraft.getHitBox().getXLength() + 2), leadY + 10, leadZ);
                    break;
                // ship faces east
                case WEST:
                    loc = new Location(player.getWorld(), leadX + (leadCraft.getHitBox().getXLength() + 2), leadY + 10, leadZ);
                    break;
                // ship faces north
                case SOUTH:
                    loc = new Location(player.getWorld(), leadX, leadY + 10, leadZ - (leadCraft.getHitBox().getXLength() + 2));
                    break;
                // ship faces south
                case NORTH:
                    loc = new Location(player.getWorld(), leadX, leadY + 10, leadZ + (leadCraft.getHitBox().getXLength() + 2));
                    break;
            }
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        TeleportUtils.teleport(player, loc, 0.0F);
        // TODO: Draw a HUD for weapons control
    }


    public void rotateSquadron(Player player, Rotation rotation) {
        if (directedCrafts.get(player) == null || directedCrafts.get(player).isEmpty()) {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to direct");
            return;
        }
        final CopyOnWriteArrayList<Craft> directed = directedCrafts.get(player);
        final MutableHitBox rotationBox = new BitmapHitBox();
        for (Craft c : directed) {
            rotationBox.addAll(c.getHitBox());
        }
        final MovecraftLocation origin = rotationBox.getMidPoint();
        for (Craft c : directed) {
            c.rotate(rotation, origin);
            determineCruiseDirection(c);
        }
    }

    public void scuttleSquadrons(Player player) {
        if (directedCrafts.get(player) == null || directedCrafts.get(player).isEmpty()) {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to scuttle");
            return;
        }
        int numCraft = 0;
        for (Craft c : directedCrafts.get(player)) {
            c.sink();
            numCraft++;
        }
        playersFormingUp.remove(player);
        playersStrafingUpDown.remove(player);
        playersStrafingLeftRight.remove(player);
        directedCrafts.get(player).clear();
        if (numCraft > 1) {
            player.sendMessage(SUCCESS_TAG + "You have scuttled " + numCraft + " squadron crafts");
        } else if (numCraft > 0) {
            player.sendMessage(SUCCESS_TAG + "You have scuttled " + numCraft + " squadron craft");
        } else {
            player.sendMessage(ERROR_TAG + "You have no squadron craft to scuttle");
        }
    }

    public void toggleFormUp(Player player, Formation formation, int spacing) {
        if (formation == null) {
            player.sendMessage(ERROR_TAG + "Invalid formation");
            return;
        }
        if (spacing > SquadronDirectorMain.getInstance().getConfig().getInt("Max spacing")) {
            player.sendMessage(ERROR_TAG + "Spacing is too high!");
            return;
        }
        if (playersFormingUp.containsKey(player) && playersFormingUp.get(player) == spacing) {
            playersFormingUp.remove(player);
            playerFormations.remove(player);
            player.sendMessage(SUCCESS_TAG + "No longer forming up");
        } else {
            playersFormingUp.put(player, spacing);
            playerFormations.put(player, formation);
            player.sendMessage(SUCCESS_TAG + "Forming up");
        }
    }

    private int bukkitDirToClockwiseDir(CruiseDirection cruiseDirection) {
        if (cruiseDirection == CruiseDirection.SOUTH) // north
            return 0;
        else if (cruiseDirection == CruiseDirection.EAST) // east
            return 1;
        else if (cruiseDirection == CruiseDirection.NORTH) // south
            return 2;
        else // west
            return 3;
    }

    public void formUpVic(Player p) {
        int leadX = 0;
        int leadY = 0;
        int leadZ = 0;
        int leadIndex = 0;
        int leadDir = 0;
        boolean leadIsCruising = false;
        int spacing = playersFormingUp.get(p);
        int craftIndex = -1;
        boolean leftOfLead = false;
        for (Craft c : directedCrafts.get(p)) {
            if (!leftOfLead)
                craftIndex++;
            if ((c == null) || (c.getHitBox().isEmpty())) {
                continue;
            }
            determineCruiseDirection(c);

            if (leadY == 0) { // if it's the lead craft, store it's info. If it isn't adjust it's heading and position
                leadX = c.getHitBox().getMidPoint().getX();
                leadY = c.getHitBox().getMidPoint().getY();
                leadZ = c.getHitBox().getMidPoint().getZ();
                leadIndex = craftIndex;
                leadDir = bukkitDirToClockwiseDir(c.getCruiseDirection());
                leadIsCruising = c.getCruising();
            } else {
                // rotate the crafts to face the direction the lead craft is facing
                int craftDir = bukkitDirToClockwiseDir(c.getCruiseDirection());
                if (craftDir != leadDir) {
                    if (Math.abs(craftDir - leadDir) == 1 || Math.abs(craftDir - leadDir) == 3) { // are they close?
                        if (craftDir - leadDir == -1 || craftDir - leadDir == 3) {
                            c.rotate(Rotation.ANTICLOCKWISE, c.getHitBox().getMidPoint());
                        } else {
                            c.rotate(Rotation.CLOCKWISE, c.getHitBox().getMidPoint());
                        }
                    } else if (craftDir != leadDir) {
                        c.rotate(Rotation.CLOCKWISE, c.getHitBox().getMidPoint()); // if they aren't close, the direction doesn't matter
                    }
//                    determineCruiseDirection(c);
                }


                // move the crafts to their position in formation
                int posInFormation = craftIndex - leadIndex;
                int offset = posInFormation * spacing;
                int targX = leadX + offset;
                int targY = leadY;
                int targZ = leadZ + offset;
                CruiseDirection cruiseDirection = c.getCruiseDirection();

                // South/North
                if (cruiseDirection == CruiseDirection.fromBlockFace(BlockFace.NORTH) || cruiseDirection == CruiseDirection.fromBlockFace(BlockFace.SOUTH)) {
                    targX = leadX + (leftOfLead ? -offset : offset);
                    targY = leadY;
                    targZ = leadZ + (cruiseDirection == CruiseDirection.fromBlockFace(BlockFace.SOUTH) ? -1 : 1) * offset;
                }
                // East/West
                if (cruiseDirection == CruiseDirection.fromBlockFace(BlockFace.EAST) || cruiseDirection == CruiseDirection.fromBlockFace(BlockFace.WEST)) {
                    targX = leadX + (cruiseDirection == CruiseDirection.EAST ? -1 : 1) * offset;
                    targY = leadY;
                    targZ = leadZ + (leftOfLead ? -offset : offset);
                }

                int dx = 0;
                int dy = 0;
                int dz = 0;

                if (c.getHitBox().getMidPoint().getX() < targX) {
                    if (targX - c.getHitBox().getMidPoint().getX() == 1) {
                        dx = 1;
                    } else {
                        dx = 2;
                    }
                } else if (c.getHitBox().getMidPoint().getX() > targX) {
                    if (targX - c.getHitBox().getMidPoint().getX() == -1) {
                        dx = -1;
                    } else {
                        dx = -2;
                    }
                }
                if (c.getHitBox().getMidPoint().getY() < targY) {
                    if (targY - c.getHitBox().getMidPoint().getY() == 1) {
                        dy = 1;
                    } else {
                        dy = 2;
                    }
                } else if (c.getHitBox().getMidPoint().getY() > targY) {
                    if (targY - c.getHitBox().getMidPoint().getY() == -1) {
                        dy = -1;
                    } else {
                        dy = -2;
                    }
                }
                if (c.getHitBox().getMidPoint().getZ() < targZ) {
                    if (targZ - c.getHitBox().getMidPoint().getZ() == 1) {
                        dz = 1;
                    } else {
                        dz = 2;
                    }
                } else if (c.getHitBox().getMidPoint().getZ() > targZ) {
                    if (targZ - c.getHitBox().getMidPoint().getZ() == -1) {
                        dz = -1;
                    } else {
                        dz = -2;
                    }
                }
                if (dy == 0 && dx == 0 && pathObstructed(c, dx, dy, dz)) {
                    dx = 2;
                }
                if (dy == 0 && dx > 0 && pathObstructed(c, dx, dy, dz)) {
                    dx = -2;
                }
                if (dy == 0 && dz == 0 && pathObstructed(c, dx, dy, dz)) {
                    dz = 2;
                }
                if (dy == 0 && dz > 0 && pathObstructed(c, dx, dy, dz)) {
                    dz = -2;
                }
                if (dy == 0 && pathObstructed(c, dx, dy, dz)) {
                    dy = -2;
                }
                if (dy < 0 && pathObstructed(c, dx, dy, dz)) {
                    dx = 2;
                }
                updatePendingMove(c, dx, dy, dz);

                // set cruising to whatever the lead is doing
                c.setCruising(leadIsCruising);
                leftOfLead = !leftOfLead;
            }
        }
    }

    public void formUpEchelon(Player p) {
        int leadX = 0;
        int leadY = 0;
        int leadZ = 0;
        int leadIndex = 0;
        int leadDir = 0;
        boolean leadIsCruising = false;
        int spacing = playersFormingUp.get(p);
        int craftIndex = -1;
        for (Craft c : directedCrafts.get(p)) {
            craftIndex++;
            if ((c == null) || (c.getHitBox().isEmpty())) {
                continue;
            }
            determineCruiseDirection(c);

            if (leadY == 0) { // if it's the lead craft, store it's info. If it isn't adjust it's heading and position
                leadX = c.getHitBox().getMidPoint().getX();
                leadY = c.getHitBox().getMidPoint().getY();
                leadZ = c.getHitBox().getMidPoint().getZ();
                leadIndex = craftIndex;
                leadDir = bukkitDirToClockwiseDir(c.getCruiseDirection());
                leadIsCruising = c.getCruising();
            } else {
                // rotate the crafts to face the direction the lead craft is facing
                int craftDir = bukkitDirToClockwiseDir(c.getCruiseDirection());
                if (craftDir != leadDir) {
                    if (Math.abs(craftDir - leadDir) == 1 || Math.abs(craftDir - leadDir) == 3) { // are they close?
                        if (craftDir - leadDir == -1 || craftDir - leadDir == 3) {
                            c.rotate(Rotation.ANTICLOCKWISE, c.getHitBox().getMidPoint());
                        } else {
                            c.rotate(Rotation.CLOCKWISE, c.getHitBox().getMidPoint());
                        }
                    } else if (craftDir != leadDir) {
                        c.rotate(Rotation.CLOCKWISE, c.getHitBox().getMidPoint()); // if they aren't close, the direction doesn't matter
                    }
//                    determineCruiseDirection(c);
                }

                // move the crafts to their position in formation
                int posInFormation = craftIndex - leadIndex;
                int offset = posInFormation * spacing;
                int targX = leadX + offset;
                int targY = leadY + (offset >> 1);
                int targZ = leadZ + offset;

                int dx = 0;
                int dy = 0;
                int dz = 0;

                if (c.getHitBox().getMidPoint().getX() < targX) {
                    if (targX - c.getHitBox().getMidPoint().getX() == 1) {
                        dx = 1;
                    } else {
                        dx = 2;
                    }
                } else if (c.getHitBox().getMidPoint().getX() > targX) {
                    if (targX - c.getHitBox().getMidPoint().getX() == -1) {
                        dx = -1;
                    } else {
                        dx = -2;
                    }
                }
                if (c.getHitBox().getMidPoint().getY() < targY) {
                    if (targY - c.getHitBox().getMidPoint().getY() == 1) {
                        dy = 1;
                    } else {
                        dy = 2;
                    }
                } else if (c.getHitBox().getMidPoint().getY() > targY) {
                    if (targY - c.getHitBox().getMidPoint().getY() == -1) {
                        dy = -1;
                    } else {
                        dy = -2;
                    }
                }
                if (c.getHitBox().getMidPoint().getZ() < targZ) {
                    if (targZ - c.getHitBox().getMidPoint().getZ() == 1) {
                        dz = 1;
                    } else {
                        dz = 2;
                    }
                } else if (c.getHitBox().getMidPoint().getZ() > targZ) {
                    if (targZ - c.getHitBox().getMidPoint().getZ() == -1) {
                        dz = -1;
                    } else {
                        dz = -2;
                    }
                }
                updatePendingMove(c, dx, dy, dz);

                // set cruising to whatever the lead is doing
                c.setCruising(leadIsCruising);
            }
        }

    }

    public void launchModeToggle(Player player) {
        if (playersInLaunchMode.contains(player)) {
            playersInLaunchMode.remove(player);
            player.sendMessage(SUCCESS_TAG + "You have left Launch Mode.");
        } else {
            playersInLaunchMode.add(player);
            player.sendMessage(SUCCESS_TAG + "You have entered Launch Mode. Left click crafts you wish to direct.");
        }
    }

    public ConcurrentHashMap<Craft, Integer> getPendingMoveDX() {
        return pendingMoveDX;
    }

    public ConcurrentHashMap<Craft, Integer> getPendingMoveDY() {
        return pendingMoveDY;
    }

    public ConcurrentHashMap<Craft, Integer> getPendingMoveDZ() {
        return pendingMoveDZ;
    }

    public ConcurrentHashMap<Player, CopyOnWriteArrayList<Craft>> getDirectedCrafts() {
        return directedCrafts;
    }

    public ConcurrentHashMap<Player, Craft> getPlayersInReconParentCrafts() {
        return playersInReconParentCrafts;
    }

    public ConcurrentHashMap<Player, Formation> getPlayerFormations() {
        return playerFormations;
    }

    public ConcurrentHashMap<Player, Integer> getPlayersFormingUp() {
        return playersFormingUp;
    }

    public ConcurrentHashMap<Player, Integer> getPlayersStrafingLeftRight() {
        return playersStrafingLeftRight;
    }

    public ConcurrentHashMap<Player, Integer> getPlayersStrafingUpDown() {
        return playersStrafingUpDown;
    }

    public ConcurrentHashMap<Player, Integer> getPlayersWeaponNumClicks() {
        return playersWeaponNumClicks;
    }

    public ConcurrentHashMap<Player, Location> getPlayersInReconSignLocation() {
        return playersInReconSignLocation;
    }

    public ConcurrentHashMap<Player, String> getPlayersWeaponControl() {
        return playersWeaponControl;
    }

    public HashSet<Player> getPlayersInAimingMode() {
        return playersInAimingMode;
    }

    public HashSet<Player> getPlayersInReconMode() {
        return playersInReconMode;
    }
}


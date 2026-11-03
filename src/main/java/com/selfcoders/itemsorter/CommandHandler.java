package com.selfcoders.itemsorter;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CommandHandler {
    private final ItemSorter plugin;
    private final CommandSender sender;
    private final String action;
    private final String[] args;

    public CommandHandler(ItemSorter plugin, CommandSender sender, String[] args) {
        this.plugin = plugin;
        this.sender = sender;

        if (args.length == 0) {
            this.action = "help";
            this.args = new String[0];
        } else {
            this.action = args[0];
            this.args = Arrays.copyOfRange(args, 1, args.length);
        }
    }

    public void execute() {
        switch (action) {
            case "help":
                printHelp();
                break;
            case "source":
                createSign(SignHelper.TAG_SOURCE);
                break;
            case "target":
                createSign(SignHelper.TAG_TARGET);
                break;
            case "persistent":
                persistent();
                break;
            case "remove-signs":
                if (!sender.hasPermission("itemsorter.command")) {
                    printError("You do not have the required permissions to use this action!");
                    break;
                }

                if (args.length == 2) {
                    removeSigns(args[0], args[1]);
                } else {
                    printError("Invalid number of arguments for 'remove-signs', expected 'player name' and 'sign name'!");
                }
                break;
            default:
                printError("Invalid action: " + action);
                sender.sendMessage("Use " + ChatColor.GOLD + "/itemsorter" + ChatColor.WHITE + " to see a list of available actions");
                break;
        }
    }

    private void createSign(String tag) {
        String action = tag.equals(SignHelper.TAG_SOURCE) ? "source" : "target";

        if (!(sender instanceof Player)) {
            printError("This action can only be used by players!");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 1 || args.length > 2) {
            printError("Usage: /itemsorter " + action + " <name> [options]");
            return;
        }

        RayTraceResult rayTraceResult = player.rayTraceBlocks(6);
        Block hitBlock = rayTraceResult == null ? null : rayTraceResult.getHitBlock();
        BlockFace face = rayTraceResult == null ? null : rayTraceResult.getHitBlockFace();

        if (hitBlock == null) {
            printError("You have to look at an inventory block (i.e. chest or hopper) or at an existing ItemSorter sign!");
            return;
        }

        // Looking at an existing sign or at an inventory block with a sign attached to it: Edit the sign
        Sign existingSign = null;
        if (Tag.SIGNS.isTagged(hitBlock.getType())) {
            existingSign = SignHelper.getSignFromBlock(hitBlock);
            if (existingSign == null) {
                printError("This is not an ItemSorter sign!");
                return;
            }
        } else if (hitBlock.getState() instanceof Container) {
            existingSign = SignHelper.getSignAttachedToBlock(hitBlock);
        }

        if (existingSign != null) {
            editSign(player, tag, existingSign);
            return;
        }

        if (!(hitBlock.getState() instanceof Container)) {
            printError("You have to look at an inventory block (i.e. chest or hopper) or at an existing ItemSorter sign!");
            return;
        }

        if (face != BlockFace.NORTH && face != BlockFace.SOUTH && face != BlockFace.EAST && face != BlockFace.WEST) {
            printError("You have to look at the side of the inventory block, not at the top or bottom!");
            return;
        }

        Block signBlock = hitBlock.getRelative(face);
        if (!signBlock.getType().isAir()) {
            printError("There is no free space for the sign at this side of the inventory block!");
            return;
        }

        signBlock.setType(Material.OAK_WALL_SIGN, false);

        WallSign wallSign = (WallSign) signBlock.getBlockData();
        wallSign.setFacing(face);
        signBlock.setBlockData(wallSign, false);

        if (!submitSign(player, signBlock, tag)) {
            signBlock.setType(Material.AIR, false);
        }
    }

    private void editSign(Player player, String tag, Sign sign) {
        SignData oldData = new SignData(sign);
        String oldType = oldData.isSource() ? SignHelper.TYPE_SOURCE : SignHelper.TYPE_TARGET;
        Location location = sign.getLocation();

        if (!player.getName().equalsIgnoreCase(oldData.player)) {
            printError("You can only edit your own ItemSorter signs!");
            return;
        }

        try {
            Database database = plugin.getDatabase();
            Set<Material> persistentMaterials = database.getPersistentMaterials(location);

            // The sign is registered again by the regular sign handling, persistent items are kept
            database.removeLocation(oldData.player, oldData.name, oldType, location);
            plugin.removeSignLocation(location);

            if (submitSign(player, sign.getBlock(), tag)) {
                if (new SignData((Sign) sign.getBlock().getState()).persistent) {
                    database.setPersistentMaterials(location, persistentMaterials);
                }
                return;
            }

            // Rejected: restore the old sign
            database.addLocation(player, oldData.name, oldType, location, oldData.order);
            database.setPersistentMaterials(location, persistentMaterials);
            plugin.addSignLocation(location);
        } catch (SQLException exception) {
            plugin.getLogger().severe("Unable to edit sign: " + exception.getMessage());
            printError("An error occurred while editing the sign!");
        }
    }

    /**
     * Let the normal sign creation logic (permissions, limits, database, other plugins) handle the new sign text and write it to the sign if accepted.
     *
     * @return true if the sign has been accepted
     */
    private boolean submitSign(Player player, Block signBlock, String tag) {
        SignChangeEvent event = new SignChangeEvent(signBlock, player, new String[]{tag, args[0], player.getName(), args.length == 2 ? args[1] : ""}, Side.FRONT);
        plugin.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        Sign sign = (Sign) signBlock.getState();
        SignSide signSide = sign.getSide(Side.FRONT);
        for (int index = 0; index < 4; index++) {
            signSide.setLine(index, event.getLine(index));
        }

        // Sign should not be editable
        sign.setWaxed(true);
        sign.update();

        return true;
    }

    private void persistent() {
        if (!(sender instanceof Player)) {
            printError("This action can only be used by players!");
            return;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("itemsorter.persistent")) {
            printError("You do not have the required permissions to manage persistent items!");
            return;
        }

        String subAction = args.length > 0 ? args[0].toLowerCase() : "";
        if (!subAction.equals("list") && !subAction.equals("add") && !subAction.equals("remove") && !subAction.equals("clear")) {
            printError("Usage: /itemsorter persistent <list|add <material>|remove <material>|clear>");
            return;
        }

        Block targetBlock = player.getTargetBlockExact(6);
        Sign sign = targetBlock == null ? null : SignHelper.getSignFromBlock(targetBlock);
        SignData signData = sign == null ? null : new SignData(sign);

        if (signData == null || !signData.isTarget()) {
            printError("You have to look at an [ItemTarget] sign!");
            return;
        }

        if (!signData.persistent) {
            printError("This sign has no persistent items enabled (add 'p' to the fourth line)!");
            return;
        }

        if (!player.hasPermission("itemsorter.destroyAny") && !player.getName().equalsIgnoreCase(signData.player)) {
            printError("You can only manage persistent items of your own signs!");
            return;
        }

        Location location = sign.getLocation();

        try {
            Database database = plugin.getDatabase();
            Set<Material> materials = database.getPersistentMaterials(location);

            switch (subAction) {
                case "list":
                    List<String> names = new ArrayList<>();
                    for (Material material : materials) {
                        names.add(material.name().toLowerCase());
                    }
                    Collections.sort(names);

                    if (names.isEmpty()) {
                        sender.sendMessage("No persistent items stored");
                    } else {
                        sender.sendMessage("Persistent items (" + names.size() + "): " + String.join(", ", names));
                    }
                    return;
                case "clear":
                    database.removePersistentMaterials(location);
                    printSuccess("Removed " + materials.size() + " persistent items");
                    return;
            }

            if (args.length != 2) {
                printError("Missing material, usage: /itemsorter persistent " + subAction + " <material>");
                return;
            }

            Material material = Material.matchMaterial(args[1]);
            if (material == null || !material.isItem()) {
                printError("Unknown item: " + args[1]);
                return;
            }

            if (subAction.equals("add")) {
                materials.add(material);
                database.setPersistentMaterials(location, materials);
                printSuccess("Added " + material.name().toLowerCase() + " to the persistent items");
            } else if (materials.remove(material)) {
                database.setPersistentMaterials(location, materials);
                printSuccess("Removed " + material.name().toLowerCase() + " from the persistent items");
            } else {
                printError(material.name().toLowerCase() + " is not a persistent item of this sign");
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Unable to update persistent items in database: " + exception.getMessage());
            printError("An error occurred while updating the persistent items!");
        }
    }

    private void removeSigns(String player, String name) {
        try {
            Database database = plugin.getDatabase();
            List<Location> locations = database.getLocations(player, name);

            for (Location location : locations) {
                location.getBlock().breakNaturally();
                plugin.removeSignLocation(location);
            }

            plugin.getDatabase().removeSigns(player, name);

            printSuccess(locations.size() + " signs removed from database");
        } catch (SQLException exception) {
            plugin.getLogger().severe("Unable to remove signs from database: " + exception.getMessage());
            printError("An error occurred while removing the signs!");
        }
    }

    private void printHelp() {
        sender.sendMessage(ChatColor.YELLOW + "-----" + ChatColor.WHITE + " Actions " + ChatColor.YELLOW + "-----");

        this.printUsage("source <name> [options]", "Place an [ItemSource] sign at the side of the inventory block you are looking at (or change the sign you are looking at)");
        this.printUsage("target <name> [options]", "Place an [ItemTarget] sign at the side of the inventory block you are looking at or change the sign you are looking at (options as on the fourth line of the sign, e.g. a,m)");
        this.printUsage("persistent <list|add <material>|remove <material>|clear>", "Manage the persistent items of the [ItemTarget] sign you are looking at");

        if (sender.hasPermission("itemsorter.command")) {
            this.printUsage("remove-signs <player name> <sign name>", "Remove all signs with the specified name of the specified player");
        }
    }

    private void printUsage(String args, String description) {
        sender.sendMessage(ChatColor.GOLD + "/itemsorter " + args + ChatColor.WHITE + ": " + description);
    }

    private void printSuccess(String string) {
        sender.sendMessage(ChatColor.GREEN + string);
    }

    private void printError(String string) {
        sender.sendMessage(ChatColor.RED + string);
    }
}

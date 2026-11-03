package com.selfcoders.itemsorter;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The target inventories of a source inventory.
 */
public class TargetInventories {
    /**
     * Inventories accepting a specific item type.
     */
    final Map<Material, List<Inventory>> perType = new HashMap<>();

    /**
     * Inventories accepting any item type (signs with the "a" option).
     */
    final List<Inventory> allItems = new ArrayList<>();

    /**
     * Inventories which share the items equally with each other (signs with the "m" option).
     */
    final List<Inventory> split = new ArrayList<>();

    /**
     * The maximum number of transfers per interval for the split inventories (0 = unlimited).
     * If multiple signs define a limit, the lowest one is used.
     */
    int splitMaxTransfers = 0;

    void addAcceptedType(Material material, Inventory inventory) {
        List<Inventory> list = perType.computeIfAbsent(material, key -> new ArrayList<>());
        if (!list.contains(inventory)) {
            list.add(inventory);
        }
    }

    void addAllItems(Inventory inventory) {
        if (!allItems.contains(inventory)) {
            allItems.add(inventory);
        }
    }

    void setSplitMaxTransfers(int maxTransfers) {
        if (maxTransfers > 0 && (splitMaxTransfers == 0 || maxTransfers < splitMaxTransfers)) {
            splitMaxTransfers = maxTransfers;
        }
    }

    void addSplit(Inventory inventory) {
        if (!split.contains(inventory)) {
            split.add(inventory);
        }
    }
}

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

    void addSplit(Inventory inventory) {
        if (!split.contains(inventory)) {
            split.add(inventory);
        }
    }
}

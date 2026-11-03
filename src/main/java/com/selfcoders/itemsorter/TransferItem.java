package com.selfcoders.itemsorter;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class TransferItem {
    ItemStack stack;
    Inventory source;
    List<Inventory> targets;
    List<Inventory> splitTargets;
    int splitMaxTransfers;

    TransferItem(ItemStack stack, Inventory source, List<Inventory> targets, List<Inventory> splitTargets, int splitMaxTransfers) {
        this.stack = stack;
        this.source = source;
        this.targets = targets;
        this.splitTargets = splitTargets;
        this.splitMaxTransfers = splitMaxTransfers;
    }
}

package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class BankMenuHolder implements InventoryHolder {
    private final Map<Integer, BankMenuAction> actions;
    private Inventory inventory;

    public BankMenuHolder(Map<Integer, BankMenuAction> actions) {
        this.actions = actions;
    }

    public BankMenuAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, BankMenuAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class SkyBlockMenuHolder implements InventoryHolder {
    private final Map<Integer, MenuAction> actions;
    private Inventory inventory;

    public SkyBlockMenuHolder(Map<Integer, MenuAction> actions) {
        this.actions = actions;
    }

    public MenuAction action(int slot) {
        return actions.getOrDefault(slot, MenuAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

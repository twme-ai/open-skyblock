package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class EnchantingAnvilHolder implements InventoryHolder {
    private final Map<Integer, EnchantingAnvilAction> actions;
    private Inventory inventory;

    public EnchantingAnvilHolder(Map<Integer, EnchantingAnvilAction> actions) {
        this.actions = actions;
    }

    public EnchantingAnvilAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, EnchantingAnvilAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

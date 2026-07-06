package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class EnchantingTableHolder implements InventoryHolder {
    private final Map<Integer, String> enchantmentsBySlot;
    private final Map<Integer, EnchantingTableAction> actions;
    private Inventory inventory;

    public EnchantingTableHolder(Map<Integer, String> enchantmentsBySlot, Map<Integer, EnchantingTableAction> actions) {
        this.enchantmentsBySlot = enchantmentsBySlot;
        this.actions = actions;
    }

    public String enchantmentId(int rawSlot) {
        return enchantmentsBySlot.get(rawSlot);
    }

    public EnchantingTableAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, EnchantingTableAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ShopSelectorHolder implements InventoryHolder {
    private final Map<Integer, String> shopsBySlot;
    private Inventory inventory;

    public ShopSelectorHolder(Map<Integer, String> shopsBySlot) {
        this.shopsBySlot = shopsBySlot;
    }

    public String shopId(int rawSlot) {
        return shopsBySlot.get(rawSlot);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ShopMenuHolder implements InventoryHolder {
    private final String shopId;
    private final Map<Integer, String> itemsBySlot;
    private Inventory inventory;

    public ShopMenuHolder(String shopId, Map<Integer, String> itemsBySlot) {
        this.shopId = shopId;
        this.itemsBySlot = itemsBySlot;
    }

    public String shopId() {
        return shopId;
    }

    public String itemId(int rawSlot) {
        return itemsBySlot.get(rawSlot);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

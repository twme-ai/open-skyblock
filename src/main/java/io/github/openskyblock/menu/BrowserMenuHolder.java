package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class BrowserMenuHolder implements InventoryHolder {
    private final BrowserMenuType type;
    private final int page;
    private final int maxPage;
    private final Map<Integer, BrowserMenuAction> actions;
    private Inventory inventory;

    public BrowserMenuHolder(BrowserMenuType type, int page, int maxPage, Map<Integer, BrowserMenuAction> actions) {
        this.type = type;
        this.page = page;
        this.maxPage = maxPage;
        this.actions = actions;
    }

    public BrowserMenuType type() {
        return type;
    }

    public int page() {
        return page;
    }

    public int maxPage() {
        return maxPage;
    }

    public BrowserMenuAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, BrowserMenuAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

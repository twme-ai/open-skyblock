package io.github.openskyblock.profile;

import org.bukkit.inventory.ItemStack;

public final class OwnedBackpack {
    private final int slot;
    private final String id;
    private final ItemStack[] contents;

    public OwnedBackpack(int slot, String id, ItemStack[] contents) {
        this.slot = slot;
        this.id = id;
        this.contents = contents;
    }

    public int slot() {
        return slot;
    }

    public String id() {
        return id;
    }

    public ItemStack[] contents() {
        return contents;
    }
}

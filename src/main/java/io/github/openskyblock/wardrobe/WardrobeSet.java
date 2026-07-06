package io.github.openskyblock.wardrobe;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record WardrobeSet(
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots
) {
    public boolean empty() {
        return isEmpty(helmet) && isEmpty(chestplate) && isEmpty(leggings) && isEmpty(boots);
    }

    public int pieceCount() {
        int count = 0;
        if (!isEmpty(helmet)) {
            count++;
        }
        if (!isEmpty(chestplate)) {
            count++;
        }
        if (!isEmpty(leggings)) {
            count++;
        }
        if (!isEmpty(boots)) {
            count++;
        }
        return count;
    }

    public List<ItemStack> pieces() {
        List<ItemStack> pieces = new ArrayList<>();
        addIfPresent(pieces, helmet);
        addIfPresent(pieces, chestplate);
        addIfPresent(pieces, leggings);
        addIfPresent(pieces, boots);
        return pieces;
    }

    public Material iconMaterial(Material fallback) {
        if (!isEmpty(chestplate)) {
            return chestplate.getType();
        }
        if (!isEmpty(helmet)) {
            return helmet.getType();
        }
        if (!isEmpty(leggings)) {
            return leggings.getType();
        }
        if (!isEmpty(boots)) {
            return boots.getType();
        }
        return fallback;
    }

    public WardrobeSet copy() {
        return new WardrobeSet(copy(helmet), copy(chestplate), copy(leggings), copy(boots));
    }

    public static WardrobeSet of(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
        return new WardrobeSet(copy(helmet), copy(chestplate), copy(leggings), copy(boots));
    }

    private static void addIfPresent(List<ItemStack> pieces, ItemStack itemStack) {
        if (!isEmpty(itemStack)) {
            pieces.add(itemStack.clone());
        }
    }

    private static ItemStack copy(ItemStack itemStack) {
        if (isEmpty(itemStack)) {
            return null;
        }
        return itemStack.clone();
    }

    private static boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir() || itemStack.getAmount() <= 0;
    }
}

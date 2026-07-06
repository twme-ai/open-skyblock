package io.github.openskyblock.shop;

import org.bukkit.Material;

import java.util.List;

public record ShopDefinition(String id, String displayName, Material material, int rows, ShopNpcDefinition npc, List<ShopItemDefinition> items) {
}

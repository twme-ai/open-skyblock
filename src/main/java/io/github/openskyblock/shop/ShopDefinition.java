package io.github.openskyblock.shop;

import java.util.List;
import org.bukkit.Material;

public record ShopDefinition(String id, String displayName, Material material, int rows, List<ShopItemDefinition> items) {
}

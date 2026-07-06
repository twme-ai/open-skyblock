package io.github.openskyblock.command;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.backpack.BackpackDefinition;
import io.github.openskyblock.bestiary.BestiaryFamilyDefinition;
import io.github.openskyblock.cake.CakeDefinition;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.enchant.SkyBlockEnchantmentDefinition;
import io.github.openskyblock.equipment.EquipmentSlotDefinition;
import io.github.openskyblock.gemstone.GemstoneDefinition;
import io.github.openskyblock.gemstone.GemstoneSlotDefinition;
import io.github.openskyblock.mob.SkyBlockMobDefinition;
import io.github.openskyblock.mobspawn.MobSpawnZoneDefinition;
import io.github.openskyblock.pet.AutoPetTrigger;
import io.github.openskyblock.pet.PetDefinition;
import io.github.openskyblock.potion.PotionBundleDefinition;
import io.github.openskyblock.profile.PlacedMinion;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.quiver.QuiverItemDefinition;
import io.github.openskyblock.reforge.ReforgeDefinition;
import io.github.openskyblock.sack.SackDefinition;
import io.github.openskyblock.sack.SackItemDefinition;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.MinionDefinition;
import io.github.openskyblock.service.SkillDefinition;
import io.github.openskyblock.slayer.SlayerDefinition;
import io.github.openskyblock.upgrade.UpgradeDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SkyBlockCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "help",
            "menu",
            "island",
            "bank",
            "shops",
            "shop",
            "auctions",
            "auction",
            "bazaar",
            "bz",
            "trade",
            "storage",
            "enderchest",
            "ec",
            "backpacks",
            "backpack",
            "shopnpcs",
            "sell",
            "sacks",
            "sack",
            "quiver",
            "potions",
            "potion",
            "godpotion",
            "cakes",
            "cake",
            "upgrades",
            "upgrade",
            "reforges",
            "reforge",
            "enchants",
            "enchantments",
            "enchant",
            "anvil",
            "stars",
            "star",
            "essence",
            "essences",
            "gemstones",
            "gemstone",
            "equipment",
            "wardrobe",
            "mobs",
            "mob",
            "mobzones",
            "mobzone",
            "bestiary",
            "slayers",
            "slayer",
            "accessorybag",
            "tuning",
            "autopet",
            "pets",
            "pet",
            "profile",
            "purse",
            "skills",
            "stats",
            "collections",
            "recipes",
            "giveitem",
            "minion",
            "reload"
    );

    private final OpenSkyBlockPlugin plugin;

    public SkyBlockCommand(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("openskyblock.command")) {
            plugin.text().send(sender, "errors.no-permission");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "profile" -> profile(sender);
            case "menu" -> menu(sender);
            case "island" -> island(sender, args);
            case "bank" -> bank(sender, args);
            case "shops" -> shops(sender);
            case "shop" -> shop(sender, args);
            case "auctions", "auction" -> auctions(sender, args);
            case "bazaar", "bz" -> bazaar(sender, args);
            case "trade" -> trade(sender, args);
            case "storage", "enderchest", "ec" -> storage(sender, args);
            case "backpacks", "backpack" -> backpack(sender, args);
            case "shopnpcs" -> shopNpcs(sender, args);
            case "sell" -> sell(sender, args);
            case "sacks", "sack" -> sacks(sender, args);
            case "quiver" -> quiver(sender, args);
            case "potions", "potion", "godpotion" -> potions(sender, args);
            case "cakes", "cake" -> cakes(sender, args);
            case "upgrades", "upgrade" -> upgrades(sender, args);
            case "reforges" -> reforges(sender);
            case "reforge" -> reforge(sender, args);
            case "enchants", "enchantments" -> enchants(sender);
            case "enchant" -> enchant(sender, args);
            case "anvil" -> anvil(sender);
            case "stars" -> stars(sender);
            case "star" -> star(sender, args);
            case "essence", "essences" -> essence(sender, args);
            case "gemstones" -> gemstones(sender);
            case "gemstone" -> gemstone(sender, args);
            case "equipment" -> equipment(sender, args);
            case "wardrobe" -> wardrobe(sender, args);
            case "mobs", "mob" -> mobs(sender, args);
            case "mobzones", "mobzone" -> mobZones(sender, args);
            case "bestiary" -> bestiary(sender, args);
            case "slayers", "slayer" -> slayers(sender, args);
            case "accessorybag" -> accessoryBag(sender, args);
            case "tuning" -> tuning(sender, args);
            case "autopet" -> autopet(sender, args);
            case "pets" -> pets(sender);
            case "pet" -> pet(sender, args);
            case "purse" -> purse(sender);
            case "skills" -> skills(sender);
            case "stats" -> stats(sender);
            case "collections" -> collections(sender);
            case "recipes" -> recipes(sender);
            case "giveitem" -> giveItem(sender, args);
            case "minion" -> minion(sender, args);
            case "reload" -> reload(sender);
            default -> plugin.text().send(sender, "errors.unknown-command");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            return startsWith(ROOT_SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("giveitem")) {
            return startsWith(plugin.customItems().definitions().stream().map(CustomItemDefinition::id).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("island")) {
            return startsWith(List.of("create", "home", "info"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bank")) {
            return startsWith(List.of("balance", "deposit", "withdraw"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("shop")) {
            return startsWith(plugin.shops().shops().stream().map(shop -> shop.id()).toList(), args[1]);
        }
        if (args.length == 2 && isAuctionCommand(args[0])) {
            return startsWith(List.of("list", "create", "buy", "cancel", "claim", "mine"), args[1]);
        }
        if (args.length == 3 && isAuctionCommand(args[0]) && args[1].equalsIgnoreCase("create")) {
            return startsWith(List.of("1000", "10000", "100000"), args[2]);
        }
        if (args.length == 3 && isAuctionCommand(args[0]) && (args[1].equalsIgnoreCase("buy") || args[1].equalsIgnoreCase("cancel"))) {
            return startsWith(plugin.auctions().listingIds(), args[2]);
        }
        if (args.length == 2 && isBazaarCommand(args[0])) {
            return startsWith(List.of("products", "info", "instabuy", "instasell", "buyorder", "selloffer", "claim", "cancel", "orders"), args[1]);
        }
        if (args.length == 3 && isBazaarCommand(args[0]) && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("instabuy") || args[1].equalsIgnoreCase("instasell") || args[1].equalsIgnoreCase("buyorder") || args[1].equalsIgnoreCase("selloffer"))) {
            return startsWith(plugin.bazaar().productIds(), args[2]);
        }
        if (args.length == 3 && isBazaarCommand(args[0]) && args[1].equalsIgnoreCase("cancel") && sender instanceof Player player) {
            return startsWith(plugin.bazaar().orderIds(player), args[2]);
        }
        if (args.length == 4 && isBazaarCommand(args[0]) && (args[1].equalsIgnoreCase("instabuy") || args[1].equalsIgnoreCase("buyorder"))) {
            return startsWith(List.of("64", "160", "1024", "71680"), args[3]);
        }
        if (args.length == 4 && isBazaarCommand(args[0]) && (args[1].equalsIgnoreCase("instasell") || args[1].equalsIgnoreCase("selloffer"))) {
            return startsWith(List.of("all", "64", "160", "1024"), args[3]);
        }
        if (args.length == 5 && isBazaarCommand(args[0]) && (args[1].equalsIgnoreCase("buyorder") || args[1].equalsIgnoreCase("selloffer"))) {
            return startsWith(List.of("1", "2.5", "10", "100"), args[4]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("trade")) {
            List<String> values = new ArrayList<>(List.of("accept", "deny", "offerhand", "offercoins", "remove", "ready", "confirm", "cancel", "status"));
            values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trade") && (args[1].equalsIgnoreCase("accept") || args[1].equalsIgnoreCase("deny"))) {
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("offercoins")) {
            return startsWith(List.of("100", "1000", "10000"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("remove")) {
            return startsWith(List.of("1", "2", "3", "4", "5"), args[2]);
        }
        if (args.length == 2 && isStorageCommand(args[0])) {
            List<String> values = new ArrayList<>(List.of("search", "sort"));
            values.addAll(numberRange(plugin.storage().pages()));
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && isStorageCommand(args[0]) && args[1].equalsIgnoreCase("sort")) {
            List<String> values = new ArrayList<>(List.of("all"));
            values.addAll(numberRange(plugin.storage().pages()));
            return startsWith(values, args[2]);
        }
        if (args.length == 2 && isBackpackCommand(args[0])) {
            List<String> values = new ArrayList<>(List.of("list", "install", "open", "remove", "give"));
            values.addAll(numberRange(plugin.backpacks().slots()));
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && isBackpackCommand(args[0]) && (args[1].equalsIgnoreCase("open") || args[1].equalsIgnoreCase("remove"))) {
            return startsWith(numberRange(plugin.backpacks().slots()), args[2]);
        }
        if (args.length == 3 && isBackpackCommand(args[0]) && args[1].equalsIgnoreCase("give")) {
            return startsWith(plugin.backpacks().definitions().stream().map(BackpackDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && isBackpackCommand(args[0]) && args[1].equalsIgnoreCase("give")) {
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("shopnpcs")) {
            return startsWith(List.of("refresh", "remove"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return startsWith(List.of("hand", "all"), args[1]);
        }
        if (args.length == 2 && isSackCommand(args[0])) {
            return startsWith(List.of("open", "deposit", "withdraw", "summary"), args[1]);
        }
        if (args.length == 3 && isSackCommand(args[0]) && (args[1].equalsIgnoreCase("open") || args[1].equalsIgnoreCase("deposit") || args[1].equalsIgnoreCase("withdraw") || args[1].equalsIgnoreCase("summary"))) {
            return startsWith(plugin.sacks().definitions().stream().map(SackDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && isSackCommand(args[0]) && args[1].equalsIgnoreCase("withdraw")) {
            return plugin.sacks().definition(args[2])
                    .map(sack -> startsWith(sack.items().stream().map(SackItemDefinition::id).toList(), args[3]))
                    .orElseGet(List::of);
        }
        if (args.length == 5 && isSackCommand(args[0]) && args[1].equalsIgnoreCase("withdraw")) {
            return startsWith(List.of("all", "64", "128", "512"), args[4]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("quiver")) {
            return startsWith(List.of("open", "deposit", "withdraw", "select", "summary"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("quiver") && (args[1].equalsIgnoreCase("withdraw") || args[1].equalsIgnoreCase("select"))) {
            return startsWith(plugin.quiver().definitions().stream().map(QuiverItemDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("quiver") && args[1].equalsIgnoreCase("withdraw")) {
            return startsWith(List.of("all", "64", "128", "512"), args[3]);
        }
        if (args.length == 2 && isPotionCommand(args[0])) {
            return startsWith(List.of("status", "clear", "activate"), args[1]);
        }
        if (args.length == 3 && isPotionCommand(args[0]) && args[1].equalsIgnoreCase("activate")) {
            return startsWith(plugin.potions().bundles().stream().map(PotionBundleDefinition::id).toList(), args[2]);
        }
        if (args.length == 2 && isCakeCommand(args[0])) {
            return startsWith(List.of("status", "placed", "clear", "list"), args[1]);
        }
        if (args.length == 2 && isUpgradeCommand(args[0])) {
            return startsWith(List.of("list", "buy", "info"), args[1]);
        }
        if (args.length == 3 && isUpgradeCommand(args[0]) && (args[1].equalsIgnoreCase("buy") || args[1].equalsIgnoreCase("info"))) {
            return startsWith(plugin.upgrades().definitions().stream().map(UpgradeDefinition::id).toList(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reforge")) {
            List<String> values = new ArrayList<>();
            values.add("remove");
            values.addAll(plugin.reforges().definitions().stream().map(ReforgeDefinition::id).toList());
            return startsWith(values, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("enchant")) {
            List<String> values = new ArrayList<>();
            values.add("remove");
            values.add("book");
            values.add("anvil");
            values.addAll(plugin.enchantments().definitions().stream().map(SkyBlockEnchantmentDefinition::id).toList());
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("enchant") && args[1].equalsIgnoreCase("book")) {
            return startsWith(plugin.enchantments().definitions().stream().map(SkyBlockEnchantmentDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("enchant") && args[1].equalsIgnoreCase("book")) {
            return startsWith(plugin.enchantments().levelSuggestions(args[2]), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("enchant") && args[1].equalsIgnoreCase("book")) {
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[4]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("enchant") && args[1].equalsIgnoreCase("remove")) {
            return startsWith(plugin.enchantments().definitions().stream().map(SkyBlockEnchantmentDefinition::id).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("enchant") && !args[1].equalsIgnoreCase("remove") && !args[1].equalsIgnoreCase("book") && !args[1].equalsIgnoreCase("anvil")) {
            return startsWith(plugin.enchantments().levelSuggestions(args[1]), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("star")) {
            return startsWith(List.of("add", "set", "clear"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("star") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("set"))) {
            return startsWith(List.of("1", "2", "3", "4", "5"), args[2]);
        }
        if (args.length == 2 && isEssenceCommand(args[0])) {
            List<String> values = new ArrayList<>(List.of("give"));
            values.addAll(plugin.stars().essenceTypes());
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && isEssenceCommand(args[0]) && args[1].equalsIgnoreCase("give")) {
            return startsWith(plugin.stars().essenceTypes(), args[2]);
        }
        if (args.length == 4 && isEssenceCommand(args[0]) && args[1].equalsIgnoreCase("give")) {
            return startsWith(List.of("10", "100", "1000", "10000"), args[3]);
        }
        if (args.length == 5 && isEssenceCommand(args[0]) && args[1].equalsIgnoreCase("give")) {
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[4]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("gemstone")) {
            return startsWith(List.of("slots", "apply", "remove", "unlock"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("gemstone") && (args[1].equalsIgnoreCase("apply") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("unlock"))) {
            return startsWith(plugin.gemstones().slots().stream().map(GemstoneSlotDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("gemstone") && args[1].equalsIgnoreCase("apply")) {
            return startsWith(plugin.gemstones().gemstones().stream().map(GemstoneDefinition::id).toList(), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("gemstone") && args[1].equalsIgnoreCase("apply")) {
            return startsWith(plugin.gemstones().tiers(args[3]), args[4]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("equipment")) {
            return startsWith(List.of("open", "equip", "unequip", "summary"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("equipment") && (args[1].equalsIgnoreCase("equip") || args[1].equalsIgnoreCase("unequip"))) {
            return startsWith(plugin.equipment().slots().stream().map(EquipmentSlotDefinition::id).toList(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("wardrobe")) {
            return startsWith(List.of("open", "save", "equip", "withdraw", "summary"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("wardrobe") && (args[1].equalsIgnoreCase("save") || args[1].equalsIgnoreCase("equip") || args[1].equalsIgnoreCase("withdraw"))) {
            return startsWith(numberRange(plugin.wardrobe().slotCount()), args[2]);
        }
        if (args.length == 2 && isMobCommand(args[0])) {
            return startsWith(List.of("list", "spawn"), args[1]);
        }
        if (args.length == 3 && isMobCommand(args[0]) && args[1].equalsIgnoreCase("spawn")) {
            return startsWith(plugin.mobs().definitions().stream().map(SkyBlockMobDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && isMobCommand(args[0]) && args[1].equalsIgnoreCase("spawn")) {
            return startsWith(List.of("1", "5", "10", Integer.toString(plugin.mobs().spawnLimitPerCommand())), args[3]);
        }
        if (args.length == 2 && isMobZoneCommand(args[0])) {
            List<String> values = new ArrayList<>(List.of("list", "spawn"));
            values.addAll(plugin.mobSpawns().zoneIds());
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && isMobZoneCommand(args[0]) && args[1].equalsIgnoreCase("spawn")) {
            return startsWith(plugin.mobSpawns().zones().stream().map(MobSpawnZoneDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && isMobZoneCommand(args[0]) && args[1].equalsIgnoreCase("spawn")) {
            return startsWith(List.of("1", "5", "10"), args[3]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bestiary")) {
            return startsWith(plugin.bestiary().families().stream().map(BestiaryFamilyDefinition::id).toList(), args[1]);
        }
        if (args.length == 2 && isSlayerCommand(args[0])) {
            return startsWith(List.of("list", "start", "status", "cancel"), args[1]);
        }
        if (args.length == 3 && isSlayerCommand(args[0]) && args[1].equalsIgnoreCase("start")) {
            return startsWith(plugin.slayers().definitions().stream().map(SlayerDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && isSlayerCommand(args[0]) && args[1].equalsIgnoreCase("start")) {
            return plugin.slayers().definition(args[2])
                    .map(definition -> startsWith(definition.sortedTierNumbers().stream().map(Object::toString).toList(), args[3]))
                    .orElseGet(List::of);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accessorybag")) {
            return startsWith(List.of("add", "remove", "summary", "open"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tuning")) {
            return startsWith(List.of("add", "remove", "reset", "summary", "open"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pet")) {
            return startsWith(List.of("open", "list", "score", "activate", "give", "xp"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("autopet")) {
            return startsWith(List.of("list", "add", "remove", "clear"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("autopet") && args[1].equalsIgnoreCase("add")) {
            return startsWith(java.util.Arrays.stream(AutoPetTrigger.values()).map(AutoPetTrigger::key).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("autopet") && args[1].equalsIgnoreCase("add")) {
            return startsWith(plugin.pets().definitions().stream().map(PetDefinition::id).toList(), args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("autopet") && args[1].equalsIgnoreCase("remove")) {
            if (sender instanceof Player player) {
                return startsWith(numberRange(plugin.profiles().profile(player).autoPetRules().size()), args[2]);
            }
            return startsWith(numberRange(plugin.pets().autoPetRuleLimit()), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("tuning") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return startsWith(plugin.tuning().tunableStats(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pet") && args[1].equalsIgnoreCase("activate")) {
            if (sender instanceof Player player) {
                SkyBlockProfile profile = plugin.profiles().profile(player);
                List<String> slots = new ArrayList<>();
                for (int index = 1; index <= profile.pets().size(); index++) {
                    slots.add(Integer.toString(index));
                }
                return startsWith(slots, args[2]);
            }
            return List.of();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pet") && args[1].equalsIgnoreCase("give")) {
            return startsWith(plugin.pets().definitions().stream().map(PetDefinition::id).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pet") && args[1].equalsIgnoreCase("xp")) {
            return startsWith(List.of("100", "1000", "10000"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("pet") && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("xp"))) {
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("accessorybag") && args[1].equalsIgnoreCase("remove")) {
            return startsWith(plugin.profiles().loadedProfiles().stream()
                    .filter(profile -> sender instanceof Player player && profile.uniqueId().equals(player.getUniqueId()))
                    .flatMap(profile -> profile.accessoryBag().stream())
                    .toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bank") && (args[1].equalsIgnoreCase("deposit") || args[1].equalsIgnoreCase("withdraw"))) {
            return startsWith(List.of("all", "100", "1000", "10000"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("giveitem")) {
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("minion")) {
            return startsWith(List.of("add", "give", "list", "claim"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("minion") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("give"))) {
            return startsWith(plugin.minions().definitions().stream().map(MinionDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("minion") && args[1].equalsIgnoreCase("give")) {
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("minion") && args[1].equalsIgnoreCase("claim")) {
            return startsWith(List.of("all", "1", "2", "3", "4", "5"), args[2]);
        }
        return List.of();
    }

    private void help(CommandSender sender, String label) {
        TextService text = plugin.text();
        text.send(sender, "commands.help-header");
        helpLine(sender, label + " menu", "commands.help.menu");
        helpLine(sender, label + " island create|home|info", "commands.help.island");
        helpLine(sender, label + " bank [deposit|withdraw] [amount|all]", "commands.help.bank");
        helpLine(sender, label + " shops", "commands.help.shop");
        helpLine(sender, label + " shop <id>", "commands.help.shop");
        helpLine(sender, label + " auctions", "commands.help.auctions");
        helpLine(sender, label + " auction create|buy|cancel|claim", "commands.help.auction");
        helpLine(sender, label + " bazaar", "commands.help.bazaar");
        helpLine(sender, label + " bazaar instabuy|instasell|buyorder|selloffer", "commands.help.bazaar-order");
        helpLine(sender, label + " trade <player>", "commands.help.trade");
        helpLine(sender, label + " trade offerhand|offercoins|ready|confirm", "commands.help.trade-session");
        helpLine(sender, label + " storage [page]", "commands.help.storage");
        helpLine(sender, label + " backpack [slot|list|install]", "commands.help.backpack");
        helpLine(sender, label + " sell <hand|all>", "commands.help.sell");
        helpLine(sender, label + " sacks", "commands.help.sacks");
        helpLine(sender, label + " sack deposit|withdraw", "commands.help.sack");
        helpLine(sender, label + " quiver", "commands.help.quiver");
        helpLine(sender, label + " quiver deposit|withdraw|select", "commands.help.quiver-edit");
        helpLine(sender, label + " potions", "commands.help.potions");
        helpLine(sender, label + " cakes", "commands.help.cakes");
        helpLine(sender, label + " upgrades", "commands.help.upgrades");
        helpLine(sender, label + " reforges", "commands.help.reforges");
        helpLine(sender, label + " reforge [id|remove]", "commands.help.reforge");
        helpLine(sender, label + " enchants", "commands.help.enchants");
        helpLine(sender, label + " enchant [id|anvil] [level]", "commands.help.enchant");
        helpLine(sender, label + " anvil", "commands.help.anvil");
        helpLine(sender, label + " stars", "commands.help.stars");
        helpLine(sender, label + " star add|set|clear [amount]", "commands.help.star");
        helpLine(sender, label + " essence", "commands.help.essence");
        helpLine(sender, label + " gemstones", "commands.help.gemstones");
        helpLine(sender, label + " gemstone apply|remove|unlock", "commands.help.gemstone");
        helpLine(sender, label + " equipment", "commands.help.equipment");
        helpLine(sender, label + " equipment equip [slot]", "commands.help.equipment-equip");
        helpLine(sender, label + " equipment unequip <slot>", "commands.help.equipment-unequip");
        helpLine(sender, label + " wardrobe", "commands.help.wardrobe");
        helpLine(sender, label + " wardrobe save|equip|withdraw <slot>", "commands.help.wardrobe-slot");
        helpLine(sender, label + " mobs", "commands.help.mobs");
        helpLine(sender, label + " mobzones", "commands.help.mob-zones");
        helpLine(sender, label + " bestiary [family]", "commands.help.bestiary");
        helpLine(sender, label + " slayer start|status|cancel", "commands.help.slayer");
        helpLine(sender, label + " accessorybag [add|remove|summary]", "commands.help.accessory-bag");
        helpLine(sender, label + " tuning [add|remove|reset|summary]", "commands.help.tuning");
        helpLine(sender, label + " autopet add|remove|list|clear", "commands.help.autopet");
        helpLine(sender, label + " pets", "commands.help.pets");
        helpLine(sender, label + " pet score", "commands.help.pet-score");
        helpLine(sender, label + " pet activate <slot>", "commands.help.pet-activate");
        helpLine(sender, label + " profile", "commands.help.profile");
        helpLine(sender, label + " purse", "commands.help.purse");
        helpLine(sender, label + " skills", "commands.help.skills");
        helpLine(sender, label + " stats", "commands.help.stats");
        helpLine(sender, label + " collections", "commands.help.collections");
        helpLine(sender, label + " recipes", "commands.help.recipes");
        if (sender.hasPermission("openskyblock.admin")) {
            helpLine(sender, label + " giveitem <id> [player]", "commands.help.giveitem");
            helpLine(sender, label + " enchant book <id> [level] [player]", "commands.help.enchant-book");
            helpLine(sender, label + " essence give <type> <amount> [player]", "commands.help.essence-give");
            helpLine(sender, label + " pet give <id> [player]", "commands.help.pet-give");
            helpLine(sender, label + " pet xp <amount> [player]", "commands.help.pet-xp");
            helpLine(sender, label + " minion give <id> [player]", "commands.help.minion-give");
            helpLine(sender, label + " mob spawn <id> [amount]", "commands.help.mob-spawn");
            helpLine(sender, label + " mobzone spawn <id> [amount]", "commands.help.mob-zone-spawn");
            helpLine(sender, label + " backpack give <id> [player]", "commands.help.backpack-give");
            helpLine(sender, label + " shopnpcs refresh|remove", "commands.help.shop-npcs");
            helpLine(sender, label + " reload", "commands.help.reload");
        }
        helpLine(sender, label + " minion add <id>", "commands.help.minion-add");
        helpLine(sender, label + " minion list", "commands.help.minion-list");
        helpLine(sender, label + " minion claim [slot|all]", "commands.help.minion-claim");
    }

    private void helpLine(CommandSender sender, String command, String descriptionPath) {
        plugin.text().send(sender, "commands.help-line", List.of(
                TextService.raw("command", command),
                TextService.raw("description", plugin.configService().messages().getString(descriptionPath, ""))
        ));
    }

    private void profile(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        SkyBlockProfile profile = plugin.profiles().profile(player);
        plugin.text().send(player, "commands.profile-summary", List.of(
                TextService.raw("player", profile.playerName()),
                TextService.raw("level", Integer.toString(plugin.skills().skyBlockLevel(profile))),
                TextService.raw("purse", plugin.text().formatNumber(profile.purse())),
                TextService.raw("bank", plugin.text().formatNumber(profile.bank()))
        ));
    }

    private void menu(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.menus().openSkyBlockMenu(player);
    }

    private void island(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.islands().teleportHome(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> plugin.islands().createOrTeleport(player);
            case "home" -> plugin.islands().teleportHome(player);
            case "info" -> plugin.islands().sendInfo(player);
            default -> plugin.text().send(player, "errors.unknown-command");
        }
    }

    private void bank(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.menus().openBankMenu(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "balance" -> plugin.economy().sendBalance(player);
            case "deposit" -> bankDeposit(player, args);
            case "withdraw" -> bankWithdraw(player, args);
            default -> plugin.text().send(player, "commands.bank-usage");
        }
    }

    private void bankDeposit(Player player, String[] args) {
        if (args.length < 3) {
            plugin.text().send(player, "commands.bank-usage");
            return;
        }
        if (args[2].equalsIgnoreCase("all")) {
            plugin.economy().depositAll(player);
            return;
        }
        parsePositiveAmount(player, args[2]).ifPresent(amount -> plugin.economy().deposit(player, amount));
    }

    private void bankWithdraw(Player player, String[] args) {
        if (args.length < 3) {
            plugin.text().send(player, "commands.bank-usage");
            return;
        }
        if (args[2].equalsIgnoreCase("all")) {
            plugin.economy().withdrawAll(player);
            return;
        }
        parsePositiveAmount(player, args[2]).ifPresent(amount -> plugin.economy().withdraw(player, amount));
    }

    private void shops(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.menus().openShopSelector(player);
    }

    private void shop(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.menus().openShopSelector(player);
            return;
        }
        plugin.shops().shop(args[1]).ifPresentOrElse(
                shop -> plugin.menus().openShop(player, shop),
                () -> plugin.text().send(player, "commands.shop-unknown", List.of(TextService.raw("shop", args[1])))
        );
    }

    private void auctions(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.auctions().sendListings(player, 1);
            return;
        }
        if (args[0].equalsIgnoreCase("auctions") && numeric(args[1])) {
            parsePositiveInt(player, args[1]).ifPresent(page -> plugin.auctions().sendListings(player, page));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                if (args.length >= 3) {
                    parsePositiveInt(player, args[2]).ifPresent(page -> plugin.auctions().sendListings(player, page));
                    return;
                }
                plugin.auctions().sendListings(player, 1);
            }
            case "create" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.auction-usage");
                    return;
                }
                parsePositiveAmount(player, args[2]).ifPresent(price -> plugin.auctions().create(player, price));
            }
            case "buy" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.auction-usage");
                    return;
                }
                plugin.auctions().buy(player, args[2]);
            }
            case "cancel" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.auction-usage");
                    return;
                }
                plugin.auctions().cancel(player, args[2]);
            }
            case "claim" -> plugin.auctions().claim(player);
            case "mine" -> plugin.auctions().sendMine(player);
            default -> plugin.text().send(player, "commands.auction-usage");
        }
    }

    private void bazaar(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.bazaar().sendProducts(player, 1);
            return;
        }
        if (numeric(args[1])) {
            parsePositiveInt(player, args[1]).ifPresent(page -> plugin.bazaar().sendProducts(player, page));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "products", "list" -> {
                if (args.length >= 3) {
                    parsePositiveInt(player, args[2]).ifPresent(page -> plugin.bazaar().sendProducts(player, page));
                    return;
                }
                plugin.bazaar().sendProducts(player, 1);
            }
            case "info" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.bazaar-usage");
                    return;
                }
                plugin.bazaar().sendInfo(player, args[2]);
            }
            case "instabuy" -> {
                if (args.length < 4) {
                    plugin.text().send(player, "commands.bazaar-usage");
                    return;
                }
                parsePositiveLong(player, args[3]).ifPresent(amount -> plugin.bazaar().instantBuy(player, args[2], amount));
            }
            case "instasell" -> {
                if (args.length < 4) {
                    plugin.text().send(player, "commands.bazaar-usage");
                    return;
                }
                Optional<Long> amount = parseBazaarAmount(player, args[3]);
                amount.ifPresent(value -> plugin.bazaar().instantSell(player, args[2], value));
            }
            case "buyorder" -> {
                if (args.length < 5) {
                    plugin.text().send(player, "commands.bazaar-usage");
                    return;
                }
                Optional<Long> amount = parsePositiveLong(player, args[3]);
                Optional<Double> price = parsePositiveAmount(player, args[4]);
                if (amount.isPresent() && price.isPresent()) {
                    plugin.bazaar().createBuyOrder(player, args[2], amount.get(), price.get());
                }
            }
            case "selloffer" -> {
                if (args.length < 5) {
                    plugin.text().send(player, "commands.bazaar-usage");
                    return;
                }
                Optional<Long> amount = parseBazaarAmount(player, args[3]);
                Optional<Double> price = parsePositiveAmount(player, args[4]);
                if (amount.isPresent() && price.isPresent()) {
                    plugin.bazaar().createSellOffer(player, args[2], amount.get(), price.get());
                }
            }
            case "claim" -> plugin.bazaar().claim(player);
            case "cancel" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.bazaar-usage");
                    return;
                }
                plugin.bazaar().cancel(player, args[2]);
            }
            case "orders", "mine" -> plugin.bazaar().sendOrders(player);
            default -> plugin.text().send(player, "commands.bazaar-usage");
        }
    }

    private void trade(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.text().send(player, "commands.trade-usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "accept" -> {
                Player requester = tradePlayer(player, args);
                if (requester != null) {
                    plugin.trades().accept(player, requester);
                }
            }
            case "deny" -> {
                Player requester = tradePlayer(player, args);
                if (requester != null) {
                    plugin.trades().deny(player, requester);
                }
            }
            case "offerhand" -> plugin.trades().offerHand(player);
            case "offercoins" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.trade-usage");
                    return;
                }
                parsePositiveAmount(player, args[2]).ifPresent(amount -> plugin.trades().offerCoins(player, amount));
            }
            case "remove" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.trade-usage");
                    return;
                }
                parsePositiveInt(player, args[2]).ifPresent(slot -> plugin.trades().removeItem(player, slot));
            }
            case "ready" -> plugin.trades().ready(player);
            case "confirm" -> plugin.trades().confirm(player);
            case "cancel" -> plugin.trades().cancel(player);
            case "status" -> plugin.trades().status(player);
            default -> {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.text().send(player, "errors.unknown-player");
                    return;
                }
                plugin.trades().request(player, target);
            }
        }
    }

    private Player tradePlayer(Player player, String[] args) {
        if (args.length < 3) {
            plugin.text().send(player, "commands.trade-usage");
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.text().send(player, "errors.unknown-player");
            return null;
        }
        return target;
    }

    private void storage(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("search")) {
            if (args.length < 3) {
                plugin.text().send(player, "commands.storage-usage");
                return;
            }
            plugin.storage().search(player, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("sort")) {
            plugin.storage().sort(player, args.length >= 3 ? args[2] : "all");
            return;
        }
        int page = 1;
        if (args.length >= 2) {
            Optional<Integer> parsed = parsePositiveInt(player, args[1]);
            if (parsed.isEmpty()) {
                return;
            }
            page = parsed.get();
        }
        plugin.storage().open(player, page);
    }

    private void backpack(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("give")) {
            backpackGive(sender, args);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.backpacks().open(player, 1);
            return;
        }
        if (numeric(args[1])) {
            parsePositiveInt(player, args[1]).ifPresent(slot -> plugin.backpacks().open(player, slot));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> plugin.backpacks().sendList(player);
            case "install" -> plugin.backpacks().installHeld(player);
            case "open" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.backpack-usage");
                    return;
                }
                parsePositiveInt(player, args[2]).ifPresent(slot -> plugin.backpacks().open(player, slot));
            }
            case "remove" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.backpack-usage");
                    return;
                }
                parsePositiveInt(player, args[2]).ifPresent(slot -> plugin.backpacks().remove(player, slot));
            }
            default -> plugin.text().send(player, "commands.backpack-usage");
        }
    }

    private void backpackGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.text().send(sender, "commands.backpack-usage");
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                plugin.text().send(sender, "errors.unknown-player");
                return;
            }
        } else {
            target = requirePlayer(sender);
            if (target == null) {
                return;
            }
        }
        BackpackDefinition definition = plugin.backpacks().definition(args[2]).orElse(null);
        if (definition == null) {
            plugin.text().send(sender, "commands.backpack-unknown", List.of(TextService.raw("backpack", args[2])));
            return;
        }
        plugin.backpacks().give(target, definition);
        plugin.text().send(sender, "commands.backpack-given", List.of(
                TextService.parsed("backpack", definition.displayName()),
                TextService.raw("player", target.getName())
        ));
    }

    private void sell(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.text().send(player, "commands.shop-usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "hand" -> plugin.shops().sellHand(player);
            case "all" -> plugin.shops().sellAll(player);
            default -> plugin.text().send(player, "commands.shop-usage");
        }
    }

    private void sacks(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("open")) {
            if (args.length >= 3) {
                plugin.sacks().definition(args[2]).ifPresentOrElse(
                        sack -> plugin.menus().openSackMenu(player, sack),
                        () -> plugin.text().send(player, "commands.sack-unknown", List.of(TextService.raw("sack", args[2])))
                );
                return;
            }
            plugin.menus().openSacksMenu(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "deposit" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.sack-usage");
                    return;
                }
                plugin.sacks().depositInventory(player, args[2]);
            }
            case "withdraw" -> {
                if (args.length < 4) {
                    plugin.text().send(player, "commands.sack-usage");
                    return;
                }
                if (args.length >= 5 && args[4].equalsIgnoreCase("all")) {
                    plugin.sacks().withdraw(player, args[2], args[3], 0);
                    return;
                }
                int amount = args.length >= 5 ? parsePositiveInt(player, args[4]).orElse(-1) : 64;
                if (amount > 0) {
                    plugin.sacks().withdraw(player, args[2], args[3], amount);
                }
            }
            case "summary" -> plugin.sacks().sendSummary(player, args.length >= 3 ? args[2] : null);
            default -> plugin.text().send(player, "commands.sack-usage");
        }
    }

    private void quiver(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("open")) {
            plugin.menus().openQuiverMenu(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "deposit" -> plugin.quiver().depositInventory(player);
            case "withdraw" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.quiver-usage");
                    return;
                }
                if (args.length >= 4 && args[3].equalsIgnoreCase("all")) {
                    plugin.quiver().withdraw(player, args[2], 0);
                    return;
                }
                int amount = args.length >= 4 ? parsePositiveInt(player, args[3]).orElse(-1) : 64;
                if (amount > 0) {
                    plugin.quiver().withdraw(player, args[2], amount);
                }
            }
            case "select" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.quiver-usage");
                    return;
                }
                plugin.quiver().select(player, args[2]);
            }
            case "summary" -> plugin.quiver().sendSummary(player);
            default -> plugin.text().send(player, "commands.quiver-usage");
        }
    }

    private void potions(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            plugin.potions().sendSummary(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "clear" -> plugin.potions().clear(player);
            case "activate" -> {
                if (!sender.hasPermission("openskyblock.admin")) {
                    plugin.text().send(sender, "errors.no-permission");
                    return;
                }
                if (args.length < 3) {
                    plugin.text().send(player, "commands.potion-usage");
                    return;
                }
                plugin.potions().activateBundle(player, args[2]);
            }
            default -> plugin.text().send(player, "commands.potion-usage");
        }
    }

    private void cakes(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            plugin.cakes().sendSummary(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "placed" -> plugin.cakes().sendPlaced(player);
            case "clear" -> plugin.cakes().clear(player);
            case "list" -> {
                plugin.text().send(player, "commands.cake-list-header");
                for (CakeDefinition definition : plugin.cakes().definitions()) {
                    plugin.text().send(player, "commands.cake-list-line", List.of(
                            TextService.raw("id", definition.id()),
                            TextService.parsed("cake", definition.displayName()),
                            TextService.raw("duration", plugin.cakes().formatDuration(definition.durationSeconds()))
                    ));
                }
            }
            default -> plugin.text().send(player, "commands.cake-usage");
        }
    }

    private void upgrades(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            plugin.upgrades().sendSummary(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "buy" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.upgrade-usage");
                    return;
                }
                plugin.upgrades().purchase(player, args[2]);
            }
            case "info" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.upgrade-usage");
                    return;
                }
                plugin.upgrades().sendDetails(player, args[2]);
            }
            default -> plugin.text().send(player, "commands.upgrade-usage");
        }
    }

    private void reforges(CommandSender sender) {
        plugin.reforges().sendList(sender);
    }

    private void reforge(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.menus().openReforgeAnvil(player);
            return;
        }
        if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("clear")) {
            plugin.reforges().removeHeld(player);
            return;
        }
        plugin.reforges().applyHeld(player, args[1]);
    }

    private void enchants(CommandSender sender) {
        plugin.enchantments().sendList(sender);
    }

    private void enchant(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("book")) {
            enchantBook(sender, args);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.menus().openEnchantingTable(player);
            return;
        }
        if (args[1].equalsIgnoreCase("anvil")) {
            plugin.menus().openEnchantingAnvil(player);
            return;
        }
        if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("clear")) {
            if (args.length < 3) {
                plugin.text().send(player, "commands.enchantment-usage");
                return;
            }
            plugin.enchantments().removeHeld(player, args[2]);
            return;
        }
        if (args.length < 3) {
            plugin.text().send(player, "commands.enchantment-usage");
            return;
        }
        try {
            int level = Integer.parseInt(args[2]);
            if (level <= 0) {
                plugin.text().send(player, "errors.invalid-number");
                return;
            }
            plugin.enchantments().applyHeld(player, args[1], level);
        } catch (NumberFormatException ignored) {
            plugin.text().send(player, "errors.invalid-number");
        }
    }

    private void anvil(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.menus().openEnchantingAnvil(player);
    }

    private void enchantBook(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        if (!plugin.enchantments().enabled()) {
            plugin.text().send(sender, "commands.enchantment-disabled");
            return;
        }
        if (args.length < 3) {
            plugin.text().send(sender, "commands.enchantment-book-usage");
            return;
        }
        SkyBlockEnchantmentDefinition definition = plugin.enchantments().definition(args[2]).orElse(null);
        if (definition == null) {
            plugin.text().send(sender, "errors.unknown-enchantment", List.of(TextService.raw("enchantment", args[2])));
            return;
        }
        int level = 1;
        if (args.length >= 4) {
            Optional<Integer> parsed = parsePositiveInt(sender, args[3]);
            if (parsed.isEmpty()) {
                return;
            }
            level = parsed.get();
        }
        Player target;
        if (args.length >= 5) {
            target = Bukkit.getPlayerExact(args[4]);
            if (target == null) {
                plugin.text().send(sender, "errors.unknown-player");
                return;
            }
        } else {
            target = requirePlayer(sender);
            if (target == null) {
                return;
            }
        }
        ItemStack book = plugin.enchantments().createBook(definition, level).orElse(null);
        if (book == null) {
            plugin.text().send(sender, "commands.enchantment-book-config-missing", List.of(TextService.raw("item", plugin.enchantments().bookItemId())));
            return;
        }
        int clampedLevel = Math.max(1, Math.min(definition.maxLevel(), level));
        target.getInventory().addItem(book).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        plugin.text().send(sender, "commands.enchantment-book-given", List.of(
                TextService.parsed("enchantment", definition.displayName()),
                TextService.raw("level", plugin.enchantments().levelLabel(clampedLevel)),
                TextService.raw("player", target.getName())
        ));
    }

    private void stars(CommandSender sender) {
        plugin.stars().sendInfo(sender);
    }

    private void star(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.text().send(player, "commands.star-usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                int amount = args.length >= 3 ? parsePositiveInt(player, args[2]).orElse(-1) : 1;
                if (amount > 0) {
                    plugin.stars().addHeld(player, amount);
                }
            }
            case "set" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.star-usage");
                    return;
                }
                parseNonNegativeInt(player, args[2]).ifPresent(stars -> plugin.stars().setHeld(player, stars));
            }
            case "clear" -> plugin.stars().clearHeld(player);
            default -> plugin.text().send(player, "commands.star-usage");
        }
    }

    private void essence(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("give")) {
            essenceGive(sender, args);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.stars().sendEssence(player);
    }

    private void essenceGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        if (args.length < 4) {
            plugin.text().send(sender, "commands.essence-usage");
            return;
        }
        String essenceId = plugin.stars().normalizeEssence(args[2]);
        if (!plugin.stars().knownEssence(essenceId)) {
            plugin.text().send(sender, "commands.essence-unknown", List.of(TextService.raw("essence", args[2])));
            return;
        }
        Optional<Double> amount = parsePositiveAmount(sender, args[3]);
        if (amount.isEmpty()) {
            return;
        }
        Player target;
        if (args.length >= 5) {
            target = Bukkit.getPlayerExact(args[4]);
            if (target == null) {
                plugin.text().send(sender, "errors.unknown-player");
                return;
            }
        } else {
            target = requirePlayer(sender);
            if (target == null) {
                return;
            }
        }
        SkyBlockProfile profile = plugin.profiles().profile(target);
        plugin.stars().addEssence(profile, essenceId, amount.get());
        plugin.text().send(sender, "commands.essence-given", List.of(
                TextService.raw("amount", plugin.text().formatNumber(amount.get())),
                TextService.parsed("essence", plugin.stars().essenceDisplayName(essenceId)),
                TextService.raw("player", target.getName()),
                TextService.raw("balance", plugin.text().formatNumber(plugin.stars().essenceBalance(profile, essenceId)))
        ));
    }

    private void gemstones(CommandSender sender) {
        plugin.gemstones().sendInfo(sender);
    }

    private void gemstone(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("slots")) {
            plugin.gemstones().sendHeldSlots(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "apply" -> {
                if (args.length < 5) {
                    plugin.text().send(player, "commands.gemstone-usage");
                    return;
                }
                plugin.gemstones().applyHeld(player, args[2], args[3], args[4]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.gemstone-usage");
                    return;
                }
                plugin.gemstones().removeHeld(player, args[2]);
            }
            case "unlock" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.gemstone-usage");
                    return;
                }
                plugin.gemstones().unlockHeld(player, args[2]);
            }
            default -> plugin.text().send(player, "commands.gemstone-usage");
        }
    }

    private void equipment(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("open")) {
            plugin.menus().openEquipmentMenu(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "equip" -> plugin.equipment().equipHeld(player, args.length >= 3 ? args[2] : null);
            case "unequip" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.equipment-usage");
                    return;
                }
                plugin.equipment().unequip(player, args[2]);
            }
            case "summary" -> plugin.equipment().sendSummary(player);
            default -> plugin.text().send(player, "commands.equipment-usage");
        }
    }

    private void wardrobe(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("open")) {
            plugin.menus().openWardrobeMenu(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "save" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.wardrobe-usage");
                    return;
                }
                parsePositiveInt(player, args[2]).ifPresent(slot -> plugin.wardrobe().saveCurrentArmor(player, slot));
            }
            case "equip" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.wardrobe-usage");
                    return;
                }
                parsePositiveInt(player, args[2]).ifPresent(slot -> plugin.wardrobe().swap(player, slot));
            }
            case "withdraw", "clear" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.wardrobe-usage");
                    return;
                }
                parsePositiveInt(player, args[2]).ifPresent(slot -> plugin.wardrobe().withdraw(player, slot));
            }
            case "summary" -> plugin.wardrobe().sendSummary(player);
            default -> plugin.text().send(player, "commands.wardrobe-usage");
        }
    }

    private void mobs(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            Player player = requirePlayer(sender);
            if (player != null) {
                plugin.mobs().sendList(player);
            }
            return;
        }
        if (args[1].equalsIgnoreCase("spawn")) {
            mobSpawn(sender, args);
            return;
        }
        plugin.text().send(sender, "commands.mob-usage");
    }

    private void mobSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            plugin.text().send(player, "commands.mob-usage");
            return;
        }
        SkyBlockMobDefinition definition = plugin.mobs().definition(args[2]).orElse(null);
        if (definition == null) {
            plugin.text().send(player, "commands.mob-unknown", List.of(TextService.raw("mob", args[2])));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            Optional<Integer> parsed = parsePositiveInt(player, args[3]);
            if (parsed.isEmpty()) {
                return;
            }
            amount = parsed.get();
        }
        int spawned = plugin.mobs().spawn(player, definition, amount);
        if (spawned > 0) {
            plugin.text().send(player, "commands.mob-spawned", List.of(
                    TextService.raw("amount", Integer.toString(spawned)),
                    TextService.parsed("mob", definition.displayName())
            ));
        }
    }

    private void mobZones(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("spawn")) {
            mobZoneSpawn(sender, args);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            plugin.mobSpawns().sendList(player);
            return;
        }
        plugin.mobSpawns().sendDetail(player, args[1]);
    }

    private void mobZoneSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            plugin.text().send(player, "commands.mob-zone-usage");
            return;
        }
        MobSpawnZoneDefinition zone = plugin.mobSpawns().zone(args[2]).orElse(null);
        if (zone == null) {
            plugin.text().send(player, "commands.mob-zone-unknown", List.of(TextService.raw("zone", args[2])));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            Optional<Integer> parsed = parsePositiveInt(player, args[3]);
            if (parsed.isEmpty()) {
                return;
            }
            amount = parsed.get();
        }
        int spawned = plugin.mobSpawns().spawn(player, zone, amount);
        if (spawned > 0) {
            plugin.text().send(player, "commands.mob-zone-spawned", List.of(
                    TextService.raw("amount", Integer.toString(spawned)),
                    TextService.parsed("zone", zone.displayName())
            ));
        }
    }

    private void bestiary(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.bestiary().sendSummary(player);
            return;
        }
        plugin.bestiary().sendDetail(player, args[1]);
    }

    private void slayers(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            plugin.slayers().sendList(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (args.length < 4) {
                    plugin.text().send(player, "commands.slayer-usage");
                    return;
                }
                parsePositiveInt(player, args[3]).ifPresent(tier -> plugin.slayers().start(player, args[2], tier));
            }
            case "status" -> plugin.slayers().status(player);
            case "cancel" -> plugin.slayers().cancel(player);
            default -> plugin.text().send(player, "commands.slayer-usage");
        }
    }

    private void accessoryBag(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("open")) {
            plugin.menus().openAccessoryBag(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> plugin.accessories().addHeld(player);
            case "remove" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.accessory-bag-missing");
                    return;
                }
                plugin.accessories().withdraw(player, args[2]);
            }
            case "summary" -> plugin.accessories().sendSummary(player);
            default -> plugin.text().send(player, "errors.unknown-command");
        }
    }

    private void tuning(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("open")) {
            plugin.menus().openTuningMenu(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.tuning-unknown-stat", List.of(TextService.raw("stat", "")));
                    return;
                }
                plugin.tuning().addPoint(player, args[2]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.tuning-unknown-stat", List.of(TextService.raw("stat", "")));
                    return;
                }
                plugin.tuning().removePoint(player, args[2]);
            }
            case "reset" -> plugin.tuning().reset(player);
            case "summary" -> plugin.tuning().sendSummary(player);
            default -> plugin.text().send(player, "errors.unknown-command");
        }
    }

    private void pets(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.menus().openPetMenu(player);
    }

    private void autopet(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            plugin.pets().sendAutoPetRules(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 4) {
                    plugin.text().send(player, "commands.autopet-usage");
                    return;
                }
                plugin.pets().addAutoPetRule(player, args[2], args[3]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    plugin.text().send(player, "commands.autopet-usage");
                    return;
                }
                parsePositiveInt(player, args[2]).ifPresent(slot -> plugin.pets().removeAutoPetRule(player, slot));
            }
            case "clear" -> plugin.pets().clearAutoPetRules(player);
            default -> plugin.text().send(player, "commands.autopet-usage");
        }
    }

    private void pet(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("open")) {
            pets(sender);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    plugin.pets().sendList(player);
                }
            }
            case "score" -> petScore(sender);
            case "activate" -> petActivate(sender, args);
            case "give" -> petGive(sender, args);
            case "xp" -> petXp(sender, args);
            default -> plugin.text().send(sender, "errors.unknown-command");
        }
    }

    private void petScore(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.pets().sendScore(player);
    }

    private void petActivate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            plugin.text().send(player, "commands.pet-usage");
            return;
        }
        try {
            int slot = Integer.parseInt(args[2]) - 1;
            plugin.pets().activate(player, slot);
        } catch (NumberFormatException ignored) {
            plugin.text().send(player, "errors.invalid-number");
        }
    }

    private void petGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.text().send(sender, "errors.unknown-command");
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                plugin.text().send(sender, "errors.unknown-player");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.text().send(sender, "errors.players-only");
            return;
        }
        PetDefinition definition = plugin.pets().definition(args[2]).orElse(null);
        if (definition == null) {
            plugin.text().send(sender, "errors.unknown-pet", List.of(TextService.raw("pet", args[2])));
            return;
        }
        plugin.pets().addPet(plugin.profiles().profile(target), definition);
        plugin.text().send(sender, "commands.pet-given", List.of(
                TextService.parsed("pet", definition.displayName()),
                TextService.raw("player", target.getName())
        ));
    }

    private void petXp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.text().send(sender, "errors.unknown-command");
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                plugin.text().send(sender, "errors.unknown-player");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.text().send(sender, "errors.players-only");
            return;
        }
        parsePositiveAmount(sender, args[2]).ifPresent(amount -> plugin.pets().addXp(target, amount));
    }

    private void shopNpcs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("refresh")) {
            plugin.shopNpcs().reload();
            plugin.text().send(sender, "commands.shop-npc-spawned");
            return;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            plugin.shopNpcs().removeLoadedNpcs();
            plugin.text().send(sender, "commands.shop-npc-removed");
            return;
        }
        plugin.text().send(sender, "errors.unknown-command");
    }

    private void purse(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        SkyBlockProfile profile = plugin.profiles().profile(player);
        plugin.text().send(player, "commands.purse-summary", List.of(
                TextService.raw("purse", plugin.text().formatNumber(profile.purse())),
                TextService.raw("bank", plugin.text().formatNumber(profile.bank()))
        ));
    }

    private void skills(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        SkyBlockProfile profile = plugin.profiles().profile(player);
        plugin.text().send(player, "commands.skills-header");
        for (SkillDefinition definition : plugin.skills().definitions()) {
            double xp = profile.skillXp(definition.type());
            plugin.text().send(player, "commands.skill-line", List.of(
                    TextService.parsed("skill", definition.displayName()),
                    TextService.raw("level", Integer.toString(plugin.skills().level(definition.type(), xp))),
                    TextService.raw("xp", plugin.text().formatNumber(xp))
            ));
        }
    }

    private void stats(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.stats().sendStats(player);
    }

    private void collections(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.menus().openCollectionBrowser(player, 0);
    }

    private void recipes(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.menus().openRecipeBook(player, 0);
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.text().send(sender, "errors.unknown-command");
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.text().send(sender, "errors.unknown-player");
                return;
            }
        } else {
            target = requirePlayer(sender);
            if (target == null) {
                return;
            }
        }
        CustomItemDefinition definition = plugin.customItems().definition(args[1]).orElse(null);
        if (definition == null) {
            plugin.text().send(sender, "errors.unknown-item", List.of(TextService.raw("item", args[1])));
            return;
        }
        ItemStack itemStack = plugin.customItems().createItem(definition);
        target.getInventory().addItem(itemStack).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        plugin.text().send(sender, "commands.item-given", List.of(
                TextService.parsed("item_name", definition.displayName()),
                TextService.raw("player", target.getName())
        ));
    }

    private void minion(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("give")) {
            minionGive(sender, args);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.text().send(player, "errors.unknown-command");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> minionAdd(player, args);
            case "list" -> minionList(player);
            case "claim" -> minionClaim(player, args);
            default -> plugin.text().send(player, "errors.unknown-command");
        }
    }

    private void minionAdd(Player player, String[] args) {
        if (args.length < 3) {
            plugin.text().send(player, "errors.unknown-command");
            return;
        }
        MinionDefinition definition = plugin.minions().definition(args[2]).orElse(null);
        if (definition == null) {
            plugin.text().send(player, "errors.unknown-minion", List.of(TextService.raw("minion", args[2])));
            return;
        }
        plugin.minions().addMinion(player, definition);
    }

    private void minionGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.text().send(sender, "errors.unknown-command");
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                plugin.text().send(sender, "errors.unknown-player");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.text().send(sender, "errors.players-only");
            return;
        }
        MinionDefinition definition = plugin.minions().definition(args[2]).orElse(null);
        if (definition == null) {
            plugin.text().send(sender, "errors.unknown-minion", List.of(TextService.raw("minion", args[2])));
            return;
        }
        target.getInventory().addItem(plugin.minions().createMinionItem(definition)).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        plugin.text().send(sender, "commands.minion-given", List.of(
                TextService.parsed("minion_name", definition.displayName()),
                TextService.raw("player", target.getName())
        ));
    }

    private void minionList(Player player) {
        SkyBlockProfile profile = plugin.profiles().profile(player);
        plugin.text().send(player, "commands.minion-list-header");
        for (int index = 0; index < profile.minions().size(); index++) {
            PlacedMinion placedMinion = profile.minions().get(index);
            MinionDefinition definition = plugin.minions().definition(placedMinion.id()).orElse(null);
            String displayName = definition == null ? placedMinion.id() : definition.displayName();
            plugin.text().send(player, "commands.minion-list-line", List.of(
                    TextService.raw("slot", Integer.toString(index + 1)),
                    TextService.parsed("minion_name", displayName),
                    TextService.raw("generated", plugin.text().formatNumber(placedMinion.generatedAmount())),
                    TextService.parsed("location", plugin.minions().locationLabel(placedMinion))
            ));
        }
    }

    private void minionClaim(Player player, String[] args) {
        if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
            long claimed = plugin.minions().claimAll(player);
            sendClaimResult(player, claimed);
            return;
        }
        try {
            int slot = Integer.parseInt(args[2]) - 1;
            long claimed = plugin.minions().claim(player, slot);
            sendClaimResult(player, claimed);
        } catch (NumberFormatException ignored) {
            plugin.text().send(player, "errors.invalid-number");
        }
    }

    private void sendClaimResult(Player player, long claimed) {
        if (claimed <= 0L) {
            plugin.text().send(player, "commands.minion-nothing");
            return;
        }
        plugin.text().send(player, "commands.minion-claimed", List.of(TextService.raw("amount", plugin.text().formatNumber(claimed))));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        plugin.reloadOpenSkyBlock();
        plugin.text().send(sender, "startup.reloaded");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.text().send(sender, "errors.players-only");
        return null;
    }

    private Optional<Double> parsePositiveAmount(CommandSender sender, String raw) {
        try {
            double amount = Double.parseDouble(raw);
            if (amount <= 0.0D || Double.isNaN(amount) || Double.isInfinite(amount)) {
                plugin.text().send(sender, "errors.invalid-number");
                return Optional.empty();
            }
            return Optional.of(amount);
        } catch (NumberFormatException ignored) {
            plugin.text().send(sender, "errors.invalid-number");
            return Optional.empty();
        }
    }

    private Optional<Integer> parsePositiveInt(CommandSender sender, String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                plugin.text().send(sender, "errors.invalid-number");
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NumberFormatException ignored) {
            plugin.text().send(sender, "errors.invalid-number");
            return Optional.empty();
        }
    }

    private Optional<Long> parsePositiveLong(CommandSender sender, String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value <= 0L) {
                plugin.text().send(sender, "errors.invalid-number");
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NumberFormatException ignored) {
            plugin.text().send(sender, "errors.invalid-number");
            return Optional.empty();
        }
    }

    private Optional<Long> parseBazaarAmount(CommandSender sender, String raw) {
        if (raw.equalsIgnoreCase("all")) {
            return Optional.of(0L);
        }
        return parsePositiveLong(sender, raw);
    }

    private Optional<Integer> parseNonNegativeInt(CommandSender sender, String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                plugin.text().send(sender, "errors.invalid-number");
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NumberFormatException ignored) {
            plugin.text().send(sender, "errors.invalid-number");
            return Optional.empty();
        }
    }

    private List<String> startsWith(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private boolean isSackCommand(String value) {
        return value.equalsIgnoreCase("sack") || value.equalsIgnoreCase("sacks");
    }

    private boolean isAuctionCommand(String value) {
        return value.equalsIgnoreCase("auction") || value.equalsIgnoreCase("auctions");
    }

    private boolean isBazaarCommand(String value) {
        return value.equalsIgnoreCase("bazaar") || value.equalsIgnoreCase("bz");
    }

    private boolean isStorageCommand(String value) {
        return value.equalsIgnoreCase("storage") || value.equalsIgnoreCase("enderchest") || value.equalsIgnoreCase("ec");
    }

    private boolean isBackpackCommand(String value) {
        return value.equalsIgnoreCase("backpack") || value.equalsIgnoreCase("backpacks");
    }

    private boolean isMobCommand(String value) {
        return value.equalsIgnoreCase("mob") || value.equalsIgnoreCase("mobs");
    }

    private boolean isMobZoneCommand(String value) {
        return value.equalsIgnoreCase("mobzone") || value.equalsIgnoreCase("mobzones");
    }

    private boolean isSlayerCommand(String value) {
        return value.equalsIgnoreCase("slayer") || value.equalsIgnoreCase("slayers");
    }

    private boolean isPotionCommand(String value) {
        return value.equalsIgnoreCase("potion") || value.equalsIgnoreCase("potions") || value.equalsIgnoreCase("godpotion");
    }

    private boolean isCakeCommand(String value) {
        return value.equalsIgnoreCase("cake") || value.equalsIgnoreCase("cakes");
    }

    private boolean isUpgradeCommand(String value) {
        return value.equalsIgnoreCase("upgrade") || value.equalsIgnoreCase("upgrades");
    }

    private boolean isEssenceCommand(String value) {
        return value.equalsIgnoreCase("essence") || value.equalsIgnoreCase("essences");
    }

    private boolean numeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private List<String> numberRange(int max) {
        List<String> values = new ArrayList<>();
        for (int value = 1; value <= max; value++) {
            values.add(Integer.toString(value));
        }
        return values;
    }
}

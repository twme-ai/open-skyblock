package io.github.openskyblock.command;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.enchant.SkyBlockEnchantmentDefinition;
import io.github.openskyblock.pet.PetDefinition;
import io.github.openskyblock.profile.PlacedMinion;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.reforge.ReforgeDefinition;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.MinionDefinition;
import io.github.openskyblock.service.SkillDefinition;
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
            "shopnpcs",
            "sell",
            "reforges",
            "reforge",
            "enchants",
            "enchantments",
            "enchant",
            "accessorybag",
            "tuning",
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
            case "shopnpcs" -> shopNpcs(sender, args);
            case "sell" -> sell(sender, args);
            case "reforges" -> reforges(sender);
            case "reforge" -> reforge(sender, args);
            case "enchants", "enchantments" -> enchants(sender);
            case "enchant" -> enchant(sender, args);
            case "accessorybag" -> accessoryBag(sender, args);
            case "tuning" -> tuning(sender, args);
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
        if (args.length == 2 && args[0].equalsIgnoreCase("shopnpcs")) {
            return startsWith(List.of("refresh", "remove"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return startsWith(List.of("hand", "all"), args[1]);
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
            values.addAll(plugin.enchantments().definitions().stream().map(SkyBlockEnchantmentDefinition::id).toList());
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("enchant") && args[1].equalsIgnoreCase("remove")) {
            return startsWith(plugin.enchantments().definitions().stream().map(SkyBlockEnchantmentDefinition::id).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("enchant") && !args[1].equalsIgnoreCase("remove")) {
            return startsWith(plugin.enchantments().levelSuggestions(args[1]), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accessorybag")) {
            return startsWith(List.of("add", "remove", "summary", "open"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tuning")) {
            return startsWith(List.of("add", "remove", "reset", "summary", "open"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pet")) {
            return startsWith(List.of("open", "list", "activate", "give", "xp"), args[1]);
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
        helpLine(sender, label + " sell <hand|all>", "commands.help.sell");
        helpLine(sender, label + " reforges", "commands.help.reforges");
        helpLine(sender, label + " reforge <id|remove>", "commands.help.reforge");
        helpLine(sender, label + " enchants", "commands.help.enchants");
        helpLine(sender, label + " enchant <id> <level>", "commands.help.enchant");
        helpLine(sender, label + " accessorybag [add|remove|summary]", "commands.help.accessory-bag");
        helpLine(sender, label + " tuning [add|remove|reset|summary]", "commands.help.tuning");
        helpLine(sender, label + " pets", "commands.help.pets");
        helpLine(sender, label + " pet activate <slot>", "commands.help.pet-activate");
        helpLine(sender, label + " profile", "commands.help.profile");
        helpLine(sender, label + " purse", "commands.help.purse");
        helpLine(sender, label + " skills", "commands.help.skills");
        helpLine(sender, label + " stats", "commands.help.stats");
        helpLine(sender, label + " collections", "commands.help.collections");
        helpLine(sender, label + " recipes", "commands.help.recipes");
        if (sender.hasPermission("openskyblock.admin")) {
            helpLine(sender, label + " giveitem <id> [player]", "commands.help.giveitem");
            helpLine(sender, label + " pet give <id> [player]", "commands.help.pet-give");
            helpLine(sender, label + " pet xp <amount> [player]", "commands.help.pet-xp");
            helpLine(sender, label + " minion give <id> [player]", "commands.help.minion-give");
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

    private void reforges(CommandSender sender) {
        plugin.reforges().sendList(sender);
    }

    private void reforge(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.text().send(player, "commands.reforge-usage");
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
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.text().send(player, "commands.enchantment-usage");
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
            case "activate" -> petActivate(sender, args);
            case "give" -> petGive(sender, args);
            case "xp" -> petXp(sender, args);
            default -> plugin.text().send(sender, "errors.unknown-command");
        }
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
}

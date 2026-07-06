package io.github.openskyblock;

import io.github.openskyblock.command.SkyBlockCommand;
import io.github.openskyblock.accessory.AccessoryService;
import io.github.openskyblock.accessory.TuningService;
import io.github.openskyblock.ability.ItemAbilityService;
import io.github.openskyblock.auction.AuctionService;
import io.github.openskyblock.backpack.BackpackService;
import io.github.openskyblock.bestiary.BestiaryService;
import io.github.openskyblock.bazaar.BazaarService;
import io.github.openskyblock.cake.CakeService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.enchant.EnchantmentService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.equipment.EquipmentService;
import io.github.openskyblock.gemstone.GemstoneService;
import io.github.openskyblock.island.IslandService;
import io.github.openskyblock.listener.AutoPetListener;
import io.github.openskyblock.listener.IslandProtectionListener;
import io.github.openskyblock.listener.CakeListener;
import io.github.openskyblock.listener.ItemAbilityListener;
import io.github.openskyblock.listener.MenuListener;
import io.github.openskyblock.listener.MinionListener;
import io.github.openskyblock.listener.MobListener;
import io.github.openskyblock.listener.PlayerLifecycleListener;
import io.github.openskyblock.listener.ProgressionListener;
import io.github.openskyblock.listener.QuiverListener;
import io.github.openskyblock.listener.RecipeListener;
import io.github.openskyblock.listener.SackListener;
import io.github.openskyblock.listener.ShopNpcListener;
import io.github.openskyblock.menu.MenuService;
import io.github.openskyblock.mob.MobService;
import io.github.openskyblock.mobspawn.MobSpawnService;
import io.github.openskyblock.pet.PetService;
import io.github.openskyblock.potion.PotionService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.quiver.QuiverService;
import io.github.openskyblock.reforge.ReforgeService;
import io.github.openskyblock.recipe.RecipeService;
import io.github.openskyblock.sack.SackService;
import io.github.openskyblock.service.CollectionService;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.MinionService;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.shop.ShopService;
import io.github.openskyblock.shop.ShopNpcService;
import io.github.openskyblock.slayer.SlayerService;
import io.github.openskyblock.stats.StatService;
import io.github.openskyblock.stats.ArmorSetService;
import io.github.openskyblock.star.StarService;
import io.github.openskyblock.storage.StorageService;
import io.github.openskyblock.trade.TradeService;
import io.github.openskyblock.upgrade.UpgradeService;
import io.github.openskyblock.wardrobe.WardrobeService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class OpenSkyBlockPlugin extends JavaPlugin {
    private ConfigService configService;
    private TextService textService;
    private ProfileManager profileManager;
    private SkillService skillService;
    private CollectionService collectionService;
    private CustomItemService customItemService;
    private ItemAbilityService itemAbilityService;
    private SackService sackService;
    private QuiverService quiverService;
    private AccessoryService accessoryService;
    private TuningService tuningService;
    private EquipmentService equipmentService;
    private WardrobeService wardrobeService;
    private ArmorSetService armorSetService;
    private CakeService cakeService;
    private PotionService potionService;
    private ReforgeService reforgeService;
    private EnchantmentService enchantmentService;
    private StarService starService;
    private GemstoneService gemstoneService;
    private PetService petService;
    private MinionService minionService;
    private IslandService islandService;
    private MenuService menuService;
    private RecipeService recipeService;
    private EconomyService economyService;
    private ShopService shopService;
    private ShopNpcService shopNpcService;
    private AuctionService auctionService;
    private BazaarService bazaarService;
    private TradeService tradeService;
    private StorageService storageService;
    private BackpackService backpackService;
    private MobService mobService;
    private MobSpawnService mobSpawnService;
    private BestiaryService bestiaryService;
    private SlayerService slayerService;
    private StatService statService;
    private UpgradeService upgradeService;
    private BukkitTask autosaveTask;
    private BukkitTask minionTask;
    private BukkitTask shopNpcTask;
    private BukkitTask potionTask;
    private BukkitTask mobSpawnTask;
    private BukkitTask slayerTask;
    private BukkitTask itemAbilityTask;
    private BukkitTask petCosmeticTask;

    @Override
    public void onEnable() {
        this.configService = new ConfigService(this);
        this.configService.load();
        this.textService = new TextService(configService);
        this.profileManager = new ProfileManager(this, configService);
        this.profileManager.loadAll();
        this.economyService = new EconomyService(configService, textService, profileManager);
        this.upgradeService = new UpgradeService(configService, textService, profileManager, economyService);
        this.economyService.upgradeService(upgradeService);
        this.collectionService = new CollectionService(configService, textService, profileManager);
        this.skillService = new SkillService(configService, textService, profileManager, collectionService, economyService);
        this.customItemService = new CustomItemService(this, configService, textService);
        this.armorSetService = new ArmorSetService(configService);
        this.reforgeService = new ReforgeService(this, configService, textService, economyService, customItemService);
        this.enchantmentService = new EnchantmentService(this, configService, textService, economyService, customItemService);
        this.starService = new StarService(this, configService, textService, economyService, customItemService);
        this.gemstoneService = new GemstoneService(this, configService, textService, economyService, customItemService);
        this.customItemService.reforgeService(reforgeService);
        this.customItemService.enchantmentService(enchantmentService);
        this.customItemService.starService(starService);
        this.customItemService.gemstoneService(gemstoneService);
        this.sackService = new SackService(configService, textService, profileManager, customItemService, skillService);
        this.quiverService = new QuiverService(this, configService, textService, profileManager, customItemService, skillService);
        this.equipmentService = new EquipmentService(configService, textService, profileManager, customItemService);
        this.wardrobeService = new WardrobeService(configService, textService, profileManager);
        this.accessoryService = new AccessoryService(configService, textService, profileManager, customItemService, upgradeService);
        this.tuningService = new TuningService(configService, textService, profileManager, accessoryService);
        this.cakeService = new CakeService(configService, textService, profileManager, customItemService);
        this.potionService = new PotionService(this, configService, textService, profileManager);
        this.petService = new PetService(this, configService, textService, profileManager, customItemService);
        this.bestiaryService = new BestiaryService(configService, textService, profileManager, skillService, economyService);
        this.statService = new StatService(configService, textService, profileManager, customItemService, accessoryService, tuningService, equipmentService, armorSetService, cakeService, potionService, upgradeService, petService, bestiaryService, reforgeService, enchantmentService, starService, gemstoneService);
        this.mobService = new MobService(this, configService, textService, customItemService, skillService, statService, bestiaryService);
        this.slayerService = new SlayerService(this, configService, textService, profileManager, economyService, skillService, mobService);
        this.statService.slayerService(slayerService);
        this.itemAbilityService = new ItemAbilityService(this, configService, textService, customItemService, statService);
        this.mobSpawnService = new MobSpawnService(this, configService, textService, mobService);
        this.minionService = new MinionService(this, configService, textService, profileManager, collectionService, upgradeService);
        this.islandService = new IslandService(configService, textService, profileManager);
        this.menuService = new MenuService(this, configService, textService, profileManager);
        this.recipeService = new RecipeService(this, configService, textService, profileManager, collectionService, customItemService, minionService, slayerService);
        this.shopService = new ShopService(configService, textService, profileManager, economyService);
        this.shopNpcService = new ShopNpcService(this, configService, textService, shopService);
        this.auctionService = new AuctionService(this, configService, textService, economyService, customItemService);
        this.auctionService.load();
        this.bazaarService = new BazaarService(this, configService, textService, economyService, customItemService);
        this.bazaarService.load();
        this.tradeService = new TradeService(configService, textService, economyService, customItemService);
        this.storageService = new StorageService(configService, textService, profileManager, customItemService);
        this.backpackService = new BackpackService(this, configService, textService, profileManager);

        reloadServices();
        registerCommands();
        registerListeners();
        startTasks();

        getServer().getConsoleSender().sendMessage(textService.message("startup.enabled"));
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (minionTask != null) {
            minionTask.cancel();
        }
        if (shopNpcTask != null) {
            shopNpcTask.cancel();
        }
        if (potionTask != null) {
            potionTask.cancel();
        }
        if (mobSpawnTask != null) {
            mobSpawnTask.cancel();
        }
        if (slayerTask != null) {
            slayerTask.cancel();
        }
        if (itemAbilityTask != null) {
            itemAbilityTask.cancel();
        }
        if (petCosmeticTask != null) {
            petCosmeticTask.cancel();
        }
        if (petService != null) {
            petService.removeAllCosmeticPets();
        }
        if (slayerService != null) {
            slayerService.shutdown();
        }
        if (shopNpcService != null) {
            shopNpcService.removeLoadedNpcs();
        }
        if (auctionService != null) {
            auctionService.save();
        }
        if (bazaarService != null) {
            bazaarService.save();
        }
        if (tradeService != null) {
            tradeService.cancelAll();
        }
        if (profileManager != null) {
            profileManager.saveAll();
        }
        if (recipeService != null) {
            recipeService.unregister();
        }
        if (textService != null) {
            getServer().getConsoleSender().sendMessage(textService.message("startup.disabled"));
        }
    }

    public void reloadOpenSkyBlock() {
        configService.load();
        reloadServices();
    }

    private void reloadServices() {
        textService.reload();
        collectionService.reload();
        skillService.reload();
        customItemService.reload();
        itemAbilityService.reload();
        upgradeService.reload();
        sackService.reload();
        quiverService.reload();
        equipmentService.reload();
        wardrobeService.reload();
        armorSetService.reload();
        cakeService.reload();
        potionService.reload();
        reforgeService.reload();
        enchantmentService.reload();
        starService.reload();
        gemstoneService.reload();
        petService.reload();
        minionService.reload();
        menuService.reload();
        recipeService.reload();
        shopService.reload();
        shopNpcService.reload();
        auctionService.reload();
        bazaarService.reload();
        tradeService.reload();
        storageService.reload();
        backpackService.reload();
        bestiaryService.reload();
        mobService.reload();
        slayerService.reload();
        mobSpawnService.reload();
    }

    private void registerCommands() {
        SkyBlockCommand skyBlockCommand = new SkyBlockCommand(this);
        PluginCommand command = getCommand("skyblock");
        if (command == null) {
            throw new IllegalStateException("Command 'skyblock' is missing from paper-plugin.yml");
        }
        command.setExecutor(skyBlockCommand);
        command.setTabCompleter(skyBlockCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new ProgressionListener(this), this);
        getServer().getPluginManager().registerEvents(new AutoPetListener(this), this);
        getServer().getPluginManager().registerEvents(new SackListener(this), this);
        getServer().getPluginManager().registerEvents(new QuiverListener(this), this);
        getServer().getPluginManager().registerEvents(new CakeListener(this), this);
        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new MinionListener(this), this);
        getServer().getPluginManager().registerEvents(new RecipeListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopNpcListener(this), this);
    }

    private void startTasks() {
        long autosaveTicks = Math.max(1200L, getConfig().getLong("settings.auto-save-ticks", 6000L));
        this.autosaveTask = getServer().getScheduler().runTaskTimer(this, profileManager::saveAll, autosaveTicks, autosaveTicks);
        this.minionTask = getServer().getScheduler().runTaskTimer(this, minionService::tickLoadedMinions, 200L, 200L);
        long shopNpcTicks = Math.max(100L, getConfig().getLong("shop-npcs.respawn-ticks", 200L));
        this.shopNpcTask = getServer().getScheduler().runTaskTimer(this, shopNpcService::spawnConfiguredNpcs, shopNpcTicks, shopNpcTicks);
        this.potionTask = getServer().getScheduler().runTaskTimer(this, potionService::tickOnlinePlayers, 20L, potionService.refreshTicks());
        this.mobSpawnTask = getServer().getScheduler().runTaskTimer(this, mobSpawnService::tick, 20L, mobSpawnService.tickIntervalTicks());
        this.slayerTask = getServer().getScheduler().runTaskTimer(this, slayerService::tickBosses, 20L, 20L);
        this.itemAbilityTask = getServer().getScheduler().runTaskTimer(this, itemAbilityService::tickMana, 20L, 20L);
        this.petCosmeticTask = getServer().getScheduler().runTaskTimer(this, petService::tickCosmeticPets, 20L, petService.cosmeticUpdateTicks());
    }

    public ConfigService configService() {
        return configService;
    }

    public TextService text() {
        return textService;
    }

    public ProfileManager profiles() {
        return profileManager;
    }

    public SkillService skills() {
        return skillService;
    }

    public CollectionService collections() {
        return collectionService;
    }

    public CustomItemService customItems() {
        return customItemService;
    }

    public ItemAbilityService itemAbilities() {
        return itemAbilityService;
    }

    public SackService sacks() {
        return sackService;
    }

    public QuiverService quiver() {
        return quiverService;
    }

    public AccessoryService accessories() {
        return accessoryService;
    }

    public TuningService tuning() {
        return tuningService;
    }

    public EquipmentService equipment() {
        return equipmentService;
    }

    public WardrobeService wardrobe() {
        return wardrobeService;
    }

    public ArmorSetService armorSets() {
        return armorSetService;
    }

    public CakeService cakes() {
        return cakeService;
    }

    public PotionService potions() {
        return potionService;
    }

    public ReforgeService reforges() {
        return reforgeService;
    }

    public EnchantmentService enchantments() {
        return enchantmentService;
    }

    public StarService stars() {
        return starService;
    }

    public GemstoneService gemstones() {
        return gemstoneService;
    }

    public PetService pets() {
        return petService;
    }

    public MinionService minions() {
        return minionService;
    }

    public IslandService islands() {
        return islandService;
    }

    public MenuService menus() {
        return menuService;
    }

    public RecipeService recipes() {
        return recipeService;
    }

    public EconomyService economy() {
        return economyService;
    }

    public ShopService shops() {
        return shopService;
    }

    public ShopNpcService shopNpcs() {
        return shopNpcService;
    }

    public AuctionService auctions() {
        return auctionService;
    }

    public BazaarService bazaar() {
        return bazaarService;
    }

    public TradeService trades() {
        return tradeService;
    }

    public StorageService storage() {
        return storageService;
    }

    public BackpackService backpacks() {
        return backpackService;
    }

    public MobService mobs() {
        return mobService;
    }

    public MobSpawnService mobSpawns() {
        return mobSpawnService;
    }

    public BestiaryService bestiary() {
        return bestiaryService;
    }

    public SlayerService slayers() {
        return slayerService;
    }

    public StatService stats() {
        return statService;
    }

    public UpgradeService upgrades() {
        return upgradeService;
    }
}

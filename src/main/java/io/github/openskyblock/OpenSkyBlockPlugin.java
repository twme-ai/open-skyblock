package io.github.openskyblock;

import io.github.openskyblock.command.SkyBlockCommand;
import io.github.openskyblock.accessory.AccessoryService;
import io.github.openskyblock.accessory.TuningService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.island.IslandService;
import io.github.openskyblock.listener.IslandProtectionListener;
import io.github.openskyblock.listener.MenuListener;
import io.github.openskyblock.listener.MinionListener;
import io.github.openskyblock.listener.PlayerLifecycleListener;
import io.github.openskyblock.listener.ProgressionListener;
import io.github.openskyblock.listener.RecipeListener;
import io.github.openskyblock.listener.ShopNpcListener;
import io.github.openskyblock.menu.MenuService;
import io.github.openskyblock.pet.PetService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.recipe.RecipeService;
import io.github.openskyblock.service.CollectionService;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.MinionService;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.shop.ShopService;
import io.github.openskyblock.shop.ShopNpcService;
import io.github.openskyblock.stats.StatService;
import io.github.openskyblock.stats.ArmorSetService;
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
    private AccessoryService accessoryService;
    private TuningService tuningService;
    private ArmorSetService armorSetService;
    private PetService petService;
    private MinionService minionService;
    private IslandService islandService;
    private MenuService menuService;
    private RecipeService recipeService;
    private EconomyService economyService;
    private ShopService shopService;
    private ShopNpcService shopNpcService;
    private StatService statService;
    private BukkitTask autosaveTask;
    private BukkitTask minionTask;
    private BukkitTask shopNpcTask;

    @Override
    public void onEnable() {
        this.configService = new ConfigService(this);
        this.configService.load();
        this.textService = new TextService(configService);
        this.profileManager = new ProfileManager(this, configService);
        this.profileManager.loadAll();
        this.economyService = new EconomyService(configService, textService, profileManager);
        this.collectionService = new CollectionService(configService, textService, profileManager);
        this.skillService = new SkillService(configService, textService, profileManager, collectionService, economyService);
        this.customItemService = new CustomItemService(this, configService, textService);
        this.armorSetService = new ArmorSetService(configService);
        this.accessoryService = new AccessoryService(configService, textService, profileManager, customItemService);
        this.tuningService = new TuningService(configService, textService, profileManager, accessoryService);
        this.petService = new PetService(configService, textService, profileManager);
        this.statService = new StatService(configService, textService, profileManager, customItemService, accessoryService, tuningService, armorSetService, petService);
        this.minionService = new MinionService(this, configService, textService, profileManager, collectionService);
        this.islandService = new IslandService(configService, textService, profileManager);
        this.menuService = new MenuService(this, configService, textService, profileManager);
        this.recipeService = new RecipeService(this, configService, textService, profileManager, collectionService, customItemService, minionService);
        this.shopService = new ShopService(configService, textService, profileManager, economyService);
        this.shopNpcService = new ShopNpcService(this, configService, textService, shopService);

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
        if (shopNpcService != null) {
            shopNpcService.removeLoadedNpcs();
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
        armorSetService.reload();
        petService.reload();
        minionService.reload();
        menuService.reload();
        recipeService.reload();
        shopService.reload();
        shopNpcService.reload();
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
        getServer().getPluginManager().registerEvents(new ProgressionListener(this), this);
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

    public AccessoryService accessories() {
        return accessoryService;
    }

    public TuningService tuning() {
        return tuningService;
    }

    public ArmorSetService armorSets() {
        return armorSetService;
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

    public StatService stats() {
        return statService;
    }
}

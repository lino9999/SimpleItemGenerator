package com.Lino.SimpleItemGenerator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class SimpleItemGenerator extends JavaPlugin {

    // Usa ConcurrentHashMap per thread safety
    private final Map<Location, GeneratorData> activeGenerators = new ConcurrentHashMap<>();
    private final Map<String, GeneratorConfig> generatorConfigs = new HashMap<>();
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    // Cache per ottimizzare i check sui chunk
    private final Set<Chunk> loadedChunksCache = ConcurrentHashMap.newKeySet();
    private long lastChunkCacheUpdate = 0;

    private File dataFile;
    private YamlConfiguration dataConfig;
    private BukkitTask globalGeneratorTask;
    private BukkitTask saveTask;

    // Flag per batch saving
    private volatile boolean needsSave = false;
    private long lastSaveTime = System.currentTimeMillis();

    @Override
    public void onEnable() {
        // Inizializzazione asincrona per non bloccare il server
        CompletableFuture.runAsync(() -> {
            saveDefaultConfig();
            setupDataFile();
            loadConfigurations();
        }).thenRun(() -> {
            // Return to main thread for Bukkit operations
            Bukkit.getScheduler().runTask(this, () -> {
                loadGenerators();
                startGlobalTask();
                startAutoSaveTask();

                // Register commands and events
                getCommand("itemgenerator").setExecutor(new ItemGeneratorCommand(this));
                getServer().getPluginManager().registerEvents(new GeneratorListener(this), this);

                // Load player data
                loadPlayerData();

                getLogger().info("SimpleItemGenerator v" + getDescription().getVersion() + " enabled!");
            });
        });
    }

    @Override
    public void onDisable() {
        if (globalGeneratorTask != null) {
            globalGeneratorTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }

        // Save everything asynchronously
        CompletableFuture.runAsync(() -> {
            saveGenerators();
            savePlayerData();
        }).join(); // Wait for completion before disabling

        activeGenerators.clear();
        playerData.clear();
        loadedChunksCache.clear();
    }

    private void startGlobalTask() {
        // Ottimizzazione: Processa i generatori in batch e solo quelli in chunk caricati
        globalGeneratorTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();

            // Update chunk cache ogni 5 secondi
            if (currentTime - lastChunkCacheUpdate > 5000) {
                updateChunkCache();
                lastChunkCacheUpdate = currentTime;
            }

            // Processa solo un subset di generatori per tick per distribuire il carico
            List<Map.Entry<Location, GeneratorData>> entries = activeGenerators.entrySet().stream()
                    .filter(entry -> isChunkLoadedCached(entry.getKey()))
                    .collect(Collectors.toList());

            int batchSize = Math.max(1, entries.size() / 5); // Processa 20% dei generatori per tick
            int startIndex = ThreadLocalRandom.current().nextInt(Math.max(1, entries.size()));

            for (int i = 0; i < batchSize && i < entries.size(); i++) {
                Map.Entry<Location, GeneratorData> entry = entries.get((startIndex + i) % entries.size());
                Location location = entry.getKey();
                GeneratorData data = entry.getValue();

                if (data.canGenerate(currentTime)) {
                    // Esegui la generazione in modo asincrono per non bloccare il main thread
                    Bukkit.getScheduler().runTask(this, () -> generateItem(location, data));
                    data.setLastGeneration(currentTime);
                }
            }
        }, 20L, 4L); // Esegui ogni 4 tick invece di 20 per processare meno generatori per volta
    }

    private void updateChunkCache() {
        loadedChunksCache.clear();
        activeGenerators.keySet().stream()
                .map(loc -> loc.getChunk())
                .filter(Chunk::isLoaded)
                .forEach(loadedChunksCache::add);
    }

    private boolean isChunkLoadedCached(Location location) {
        return loadedChunksCache.contains(location.getChunk());
    }

    private void startAutoSaveTask() {
        // Save task che salva solo quando necessario
        int saveInterval = getConfig().getInt("general.auto-save-interval", 5) * 60 * 20; // Convert to ticks
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (needsSave || System.currentTimeMillis() - lastSaveTime > 300000) { // Force save ogni 5 minuti
                saveGenerators();
                savePlayerData();
                needsSave = false;
                lastSaveTime = System.currentTimeMillis();
            }
        }, saveInterval, saveInterval);
    }

    private void generateItem(Location location, GeneratorData data) {
        // Check if chunk is loaded (double check necessario)
        if (!location.getChunk().isLoaded()) return;

        // Check if block is still present
        if (location.getBlock().getType() != data.getConfig().getBlockType()) {
            removeGenerator(location);
            return;
        }

        GeneratorConfig config = data.getConfig();
        ItemStack item = config.getRandomItem();

        if (item != null) {
            // Generate the item
            Location dropLocation = location.clone().add(0.5, 1.2, 0.5);

            // Ottimizzazione: Drop item senza fisica naturale se ci sono troppi items
            if (location.getWorld().getNearbyEntities(dropLocation, 5, 5, 5).size() < 50) {
                location.getWorld().dropItemNaturally(dropLocation, item);
            } else {
                location.getWorld().dropItem(dropLocation, item).setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            }

            // Particle effects - Ridotti per performance
            if (config.hasParticles() && ThreadLocalRandom.current().nextInt(3) == 0) { // Solo 33% delle volte
                playGenerationEffect(dropLocation);
            }

            // Update statistics
            data.incrementItemsGenerated();

            // Update player statistics
            PlayerData pData = playerData.get(data.getPlacer());
            if (pData != null) {
                pData.incrementTotalItemsGenerated();
            }
        }
    }

    private void playGenerationEffect(Location location) {
        // Ottimizzazione: Ridotto numero di particelle
        location.getWorld().spawnParticle(
                org.bukkit.Particle.HAPPY_VILLAGER,
                location,
                3, // Ridotto da 10 a 3
                0.3, 0.3, 0.3, // Ridotto spread
                0
        );
    }

    public void startGenerator(Location loc, String generatorName, UUID placer) {
        GeneratorConfig config = generatorConfigs.get(generatorName);
        if (config == null) return;

        GeneratorData data = new GeneratorData(generatorName, config, placer);
        activeGenerators.put(loc, data);

        // Update player statistics
        PlayerData pData = getPlayerData(placer);
        pData.incrementGeneratorsPlaced();

        // Mark for save invece di salvare immediatamente
        needsSave = true;
    }

    public void removeGenerator(Location loc) {
        GeneratorData data = activeGenerators.remove(loc);
        if (data == null) return;

        needsSave = true;
    }

    private void loadConfigurations() {
        generatorConfigs.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("generators");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    GeneratorConfig config = new GeneratorConfig(section.getConfigurationSection(key));
                    generatorConfigs.put(key, config);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to load generator config: " + key, e);
                }
            }
        }

        getLogger().info("Loaded " + generatorConfigs.size() + " generator configurations");
    }

    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create data file: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadGenerators() {
        if (!dataConfig.contains("generators")) return;

        ConfigurationSection generatorsSection = dataConfig.getConfigurationSection("generators");
        if (generatorsSection == null) return;

        int loaded = 0;
        for (String key : generatorsSection.getKeys(false)) {
            try {
                ConfigurationSection genSection = generatorsSection.getConfigurationSection(key);
                if (genSection == null) continue;

                ConfigurationSection locSection = genSection.getConfigurationSection("location");
                String generatorName = genSection.getString("type");
                UUID placer = UUID.fromString(genSection.getString("placer", "00000000-0000-0000-0000-000000000000"));
                long itemsGenerated = genSection.getLong("items-generated", 0);

                if (locSection == null || generatorName == null) continue;

                Location loc = Location.deserialize(locSection.getValues(false));
                GeneratorConfig config = generatorConfigs.get(generatorName);

                if (config != null) {
                    GeneratorData data = new GeneratorData(generatorName, config, placer);
                    data.setItemsGenerated(itemsGenerated);
                    activeGenerators.put(loc, data);
                    loaded++;
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to load generator: " + key, e);
            }
        }

        getLogger().info("Loaded " + loaded + " active generators");
    }

    public void saveGenerators() {
        try {
            dataConfig.set("generators", null);
            int i = 0;

            for (Map.Entry<Location, GeneratorData> entry : activeGenerators.entrySet()) {
                ConfigurationSection section = dataConfig.createSection("generators." + i++);
                section.set("location", entry.getKey().serialize());
                section.set("type", entry.getValue().getGeneratorName());
                section.set("placer", entry.getValue().getPlacer().toString());
                section.set("items-generated", entry.getValue().getItemsGenerated());
            }

            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Error saving data: " + e.getMessage());
        }
    }

    public void saveGeneratorsAsync() {
        needsSave = true; // Mark for batch save invece di salvare immediatamente
    }

    private void loadPlayerData() {
        File playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) return;

        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        ConfigurationSection section = playerConfig.getConfigurationSection("players");

        if (section != null) {
            for (String uuid : section.getKeys(false)) {
                try {
                    UUID playerUUID = UUID.fromString(uuid);
                    PlayerData data = new PlayerData(playerUUID);

                    ConfigurationSection playerSection = section.getConfigurationSection(uuid);
                    data.setGeneratorsPlaced(playerSection.getInt("generators-placed", 0));
                    data.setTotalItemsGenerated(playerSection.getLong("total-items-generated", 0));

                    playerData.put(playerUUID, data);
                } catch (Exception e) {
                    getLogger().warning("Failed to load player data for: " + uuid);
                }
            }
        }
    }

    private void savePlayerData() {
        File playerDataFile = new File(getDataFolder(), "playerdata.yml");
        YamlConfiguration playerConfig = new YamlConfiguration();

        for (Map.Entry<UUID, PlayerData> entry : playerData.entrySet()) {
            ConfigurationSection section = playerConfig.createSection("players." + entry.getKey().toString());
            section.set("generators-placed", entry.getValue().getGeneratorsPlaced());
            section.set("total-items-generated", entry.getValue().getTotalItemsGenerated());
        }

        try {
            playerConfig.save(playerDataFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save player data: " + e.getMessage());
        }
    }

    public ItemStack createGeneratorItem(String generatorName) {
        GeneratorConfig config = generatorConfigs.get(generatorName);
        if (config == null) return new ItemStack(Material.AIR);

        ItemStack item = new ItemStack(config.getBlockType());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add("§7Cooldown: §e" + config.getCooldown() + "s");
        lore.add("§7Block Type: §e" + config.getBlockType().name());

        lore.add("");
        lore.add("§7Possible Items:");
        config.getPossibleItems().forEach(possibleItem -> {
            lore.add("§8• §f" + possibleItem.getType().name() + " §7x" + possibleItem.getAmount());
        });

        lore.add("");
        lore.add("§8ID: " + generatorName);
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    public void reloadPlugin() {
        // Save current state
        saveGenerators();
        savePlayerData();

        // Reload configurations
        reloadConfig();
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Reload generators
        loadConfigurations();

        // Update existing generators with new configurations
        activeGenerators.forEach((loc, data) -> {
            GeneratorConfig newConfig = generatorConfigs.get(data.getGeneratorName());
            if (newConfig != null) {
                data.setConfig(newConfig);
            }
        });

        // Clear cache
        loadedChunksCache.clear();
        lastChunkCacheUpdate = 0;
    }

    // Getters
    public Map<Location, GeneratorData> getActiveGenerators() {
        return activeGenerators;
    }

    public Map<String, GeneratorConfig> getGeneratorConfigs() {
        return generatorConfigs;
    }

    public GeneratorConfig getGeneratorConfig(String name) {
        return generatorConfigs.get(name);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerData.computeIfAbsent(uuid, PlayerData::new);
    }

    public int getGeneratorLimit(Player player) {
        // Check permission-based limits
        for (int i = 100; i >= 0; i--) {
            if (player.hasPermission("itemgenerator.limit." + i)) {
                return i;
            }
        }
        return getConfig().getInt("general.default-generator-limit", 5);
    }
}
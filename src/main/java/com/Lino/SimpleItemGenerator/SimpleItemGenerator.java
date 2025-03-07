package com.Lino.SimpleItemGenerator;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class SimpleItemGenerator extends JavaPlugin {

    private Map<Location, GeneratorTask> activeGenerators = new HashMap<>();
    private File dataFile;
    private YamlConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDataFile();
        loadGenerators();

        getCommand("itemgenerator").setExecutor(new ItemGeneratorCommand(this));
        getServer().getPluginManager().registerEvents(new GeneratorListener(this), this);
    }

    @Override
    public void onDisable() {
        saveGenerators();
        activeGenerators.values().forEach(GeneratorTask::cancel);
        activeGenerators.clear();
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
        if (dataConfig.contains("generators")) {
            ConfigurationSection generatorsSection = dataConfig.getConfigurationSection("generators");
            if (generatorsSection == null) return;

            for (String key : generatorsSection.getKeys(false)) {
                ConfigurationSection genSection = generatorsSection.getConfigurationSection(key);
                if (genSection == null) continue;

                ConfigurationSection locSection = genSection.getConfigurationSection("location");
                String generatorName = genSection.getString("type");

                if (locSection == null || generatorName == null) {
                    getLogger().warning("Invalid generator entry: " + key);
                    continue;
                }

                try {
                    Location loc = Location.deserialize(locSection.getValues(false));
                    startGeneratorTask(loc, generatorName);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Failed to load generator: " + e.getMessage());
                }
            }
        }
    }

    public void saveGenerators() {
        try {
            dataConfig.set("generators", null);
            int i = 0;
            for (Map.Entry<Location, GeneratorTask> entry : activeGenerators.entrySet()) {
                ConfigurationSection section = dataConfig.createSection("generators." + i++);
                section.set("location", entry.getKey().serialize());
                section.set("type", entry.getValue().getGeneratorName());
            }
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Error saving data: " + e.getMessage());
        }
    }

    public void startGeneratorTask(Location loc, String generatorName) {
        GeneratorConfig config = getGeneratorConfig(generatorName);
        if (config == null) return;

        GeneratorTask task = new GeneratorTask(loc, config, generatorName);
        task.runTaskTimer(this, 0, config.getCooldown() * 20L);
        activeGenerators.put(loc, task);
    }

    public void stopGeneratorTask(Location loc) {
        GeneratorTask task = activeGenerators.remove(loc);
        if (task != null) task.cancel();
    }

    public GeneratorConfig getGeneratorConfig(String name) {
        ConfigurationSection config = getConfig().getConfigurationSection("generators." + name);
        return config != null ? new GeneratorConfig(config) : null;
    }

    public ItemStack createGeneratorItem(String generatorName) {
        GeneratorConfig config = getGeneratorConfig(generatorName);
        if (config == null) return new ItemStack(Material.AIR);

        ItemStack item = new ItemStack(config.getBlockType());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                config.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add("§7Cooldown: " + config.getCooldown() + "s");
        lore.add("§8ID: " + generatorName);
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    public void reloadPlugin() {
        reloadConfig();
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        activeGenerators.values().forEach(GeneratorTask::cancel);
        activeGenerators.clear();
        loadGenerators();
    }

    public Map<Location, GeneratorTask> getActiveGenerators() {
        return activeGenerators;
    }
}
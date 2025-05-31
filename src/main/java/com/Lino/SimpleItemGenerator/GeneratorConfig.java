package com.Lino.SimpleItemGenerator;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GeneratorConfig {
    private final int cooldown;
    private final Material blockType;
    private final String displayName;
    private final boolean particles;
    private final List<ItemStack> items = new ArrayList<>();
    private final List<ItemStack> possibleItems = new ArrayList<>();
    private final Random random = new Random();

    // New configurations
    private final String permission;
    private final boolean dropNaturally;

    public GeneratorConfig(ConfigurationSection config) {
        this.cooldown = config.getInt("cooldown", 30);
        this.blockType = Material.valueOf(config.getString("block-type", "LODESTONE"));
        this.displayName = config.getString("display-name", "§6Item Generator");
        this.particles = config.getBoolean("particles", false);

        // New options
        this.permission = config.getString("permission", "");
        this.dropNaturally = config.getBoolean("drop-naturally", true);

        // Load items
        loadItems(config.getConfigurationSection("items"));
    }

    private void loadItems(ConfigurationSection itemsSection) {
        if (itemsSection == null) return;

        for (String key : itemsSection.getKeys(false)) {
            try {
                ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                ItemStack item = createItemFromConfig(itemSec);

                if (item != null) {
                    possibleItems.add(item.clone());

                    int weight = itemSec.getInt("weight", 1);
                    for (int i = 0; i < weight; i++) {
                        items.add(item);
                    }
                }
            } catch (Exception e) {
                Logger.getLogger("SimpleItemGenerator").log(Level.WARNING,
                        "Failed to load item: " + key, e);
            }
        }
    }

    private ItemStack createItemFromConfig(ConfigurationSection itemSec) {
        Material material = Material.valueOf(itemSec.getString("material"));
        int amount = itemSec.getInt("amount", 1);

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        // Custom name
        if (itemSec.contains("name")) {
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', itemSec.getString("name")));
        }

        // Custom lore
        if (itemSec.contains("lore")) {
            List<String> lore = new ArrayList<>();
            for (String line : itemSec.getStringList("lore")) {
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
        }

        // Durability
        if (itemSec.contains("durability") && meta instanceof org.bukkit.inventory.meta.Damageable) {
            ((org.bukkit.inventory.meta.Damageable) meta).setDamage(itemSec.getInt("durability"));
        }

        // Unbreakable
        if (itemSec.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }

        // Custom model data
        if (itemSec.contains("custom-model-data")) {
            meta.setCustomModelData(itemSec.getInt("custom-model-data"));
        }

        item.setItemMeta(meta);

        // Enchantments
        if (itemSec.contains("enchants")) {
            for (String enchant : itemSec.getStringList("enchants")) {
                String[] parts = enchant.split(":");
                if (parts.length == 2) {
                    try {
                        Enchantment ench = null;

                        // Direct mapping for common enchantments
                        switch (parts[0].toUpperCase()) {
                            case "DURABILITY":
                            case "UNBREAKING":
                                ench = Enchantment.UNBREAKING;
                                break;
                            case "PROTECTION_ENVIRONMENTAL":
                            case "PROTECTION":
                                ench = Enchantment.PROTECTION;
                                break;
                            case "SHARPNESS":
                            case "DAMAGE_ALL":
                                ench = Enchantment.SHARPNESS;
                                break;
                            case "EFFICIENCY":
                            case "DIG_SPEED":
                                ench = Enchantment.EFFICIENCY;
                                break;
                            case "FORTUNE":
                            case "LOOT_BONUS_BLOCKS":
                                ench = Enchantment.FORTUNE;
                                break;
                            case "SILK_TOUCH":
                                ench = Enchantment.SILK_TOUCH;
                                break;
                            case "LOOTING":
                                ench = Enchantment.LOOTING;
                                break;
                            case "FIRE_ASPECT":
                                ench = Enchantment.FIRE_ASPECT;
                                break;
                            case "MENDING":
                                ench = Enchantment.MENDING;
                                break;
                            default:
                                // Try to get by registry
                                try {
                                    ench = org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft(parts[0].toLowerCase()));
                                } catch (Exception ignored) {
                                    Logger.getLogger("SimpleItemGenerator").warning("Unknown enchantment: " + parts[0]);
                                }
                        }

                        if (ench != null) {
                            item.addUnsafeEnchantment(ench, Integer.parseInt(parts[1]));
                        }
                    } catch (Exception ex) {
                        Logger.getLogger("SimpleItemGenerator").warning("Failed to apply enchantment: " + enchant);
                    }
                }
            }
        }

        return item;
    }

    public ItemStack getRandomItem() {
        if (items.isEmpty()) return null;

        ItemStack item = items.get(random.nextInt(items.size())).clone();

        return item;
    }

    // Getters
    public int getCooldown() { return cooldown; }
    public Material getBlockType() { return blockType; }
    public String getDisplayName() { return displayName; }
    public boolean hasParticles() { return particles; }
    public String getPermission() { return permission; }
    public boolean shouldDropNaturally() { return dropNaturally; }
    public List<ItemStack> getPossibleItems() { return new ArrayList<>(possibleItems); }
}
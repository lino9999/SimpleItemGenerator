package com.Lino.SimpleItemGenerator;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GeneratorConfig {
    private final int cooldown;
    private final Material blockType;
    private final String displayName;
    private final boolean particles;
    private final List<ItemStack> items = new ArrayList<>();
    private final Random random = new Random();

    public GeneratorConfig(ConfigurationSection config) {
        this.cooldown = config.getInt("cooldown");
        this.blockType = Material.valueOf(config.getString("block-type", "LODESTONE"));
        this.displayName = config.getString("display-name", "§6Item Generator");
        this.particles = config.getBoolean("particles", false);

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                ItemStack item = new ItemStack(
                        Material.valueOf(itemSec.getString("material")),
                        itemSec.getInt("amount", 1)
                );

                // Gestione incantesimi
                if (itemSec.contains("enchants")) {
                    for (String enchant : itemSec.getStringList("enchants")) {
                        String[] parts = enchant.split(":");
                        item.addUnsafeEnchantment(
                                Enchantment.getByName(parts[0].toUpperCase()),
                                Integer.parseInt(parts[1])
                        );
                    }
                }

                int weight = itemSec.getInt("weight", 1);
                for (int i = 0; i < weight; i++) {
                    items.add(item);
                }
            }
        }
    }

    public int getCooldown() { return cooldown; }
    public Material getBlockType() { return blockType; }
    public String getDisplayName() { return displayName; }
    public boolean hasParticles() { return particles; }

    public ItemStack getRandomItem() {
        return items.isEmpty() ? null : items.get(random.nextInt(items.size())).clone();
    }
}
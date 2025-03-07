package com.Lino.SimpleItemGenerator;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class GeneratorTask extends BukkitRunnable {
    private final Location location;
    private final GeneratorConfig config;
    private final String generatorName;

    public GeneratorTask(Location location, GeneratorConfig config, String generatorName) {
        this.location = location;
        this.config = config;
        this.generatorName = generatorName;
    }

    @Override
    public void run() {
        if (location.getBlock().getType() == config.getBlockType()) {
            ItemStack item = config.getRandomItem();
            if (item != null) {
                location.getWorld().dropItemNaturally(location.clone().add(0.5, 1, 0.5), item);
            }
        } else {
            this.cancel();
        }
    }

    public String getGeneratorName() {
        return generatorName;
    }
}
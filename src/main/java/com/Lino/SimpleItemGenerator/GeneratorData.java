package com.Lino.SimpleItemGenerator;

import java.util.UUID;

public class GeneratorData {
    private final String generatorName;
    private GeneratorConfig config;
    private final UUID placer;
    private long lastGeneration;
    private long itemsGenerated;

    public GeneratorData(String generatorName, GeneratorConfig config, UUID placer) {
        this.generatorName = generatorName;
        this.config = config;
        this.placer = placer;
        this.lastGeneration = System.currentTimeMillis();
        this.itemsGenerated = 0;
    }

    public boolean canGenerate(long currentTime) {
        // Check cooldown
        long cooldownMillis = config.getCooldown() * 1000L;
        return currentTime - lastGeneration >= cooldownMillis;
    }

    public void incrementItemsGenerated() {
        itemsGenerated++;
    }

    // Getters and Setters
    public String getGeneratorName() {
        return generatorName;
    }

    public GeneratorConfig getConfig() {
        return config;
    }

    public void setConfig(GeneratorConfig config) {
        this.config = config;
    }

    public UUID getPlacer() {
        return placer;
    }

    public long getLastGeneration() {
        return lastGeneration;
    }

    public void setLastGeneration(long lastGeneration) {
        this.lastGeneration = lastGeneration;
    }

    public long getItemsGenerated() {
        return itemsGenerated;
    }

    public void setItemsGenerated(long itemsGenerated) {
        this.itemsGenerated = itemsGenerated;
    }
}
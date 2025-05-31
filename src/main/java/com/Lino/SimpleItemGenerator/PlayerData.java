package com.Lino.SimpleItemGenerator;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private int generatorsPlaced;
    private long totalItemsGenerated;
    private long firstPlaced;
    private long lastActive;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.generatorsPlaced = 0;
        this.totalItemsGenerated = 0;
        this.firstPlaced = System.currentTimeMillis();
        this.lastActive = System.currentTimeMillis();
    }

    public void incrementGeneratorsPlaced() {
        generatorsPlaced++;
        updateLastActive();
    }

    public void incrementTotalItemsGenerated() {
        totalItemsGenerated++;
        updateLastActive();
    }

    private void updateLastActive() {
        lastActive = System.currentTimeMillis();
    }

    // Getters and Setters
    public UUID getUuid() {
        return uuid;
    }

    public int getGeneratorsPlaced() {
        return generatorsPlaced;
    }

    public void setGeneratorsPlaced(int generatorsPlaced) {
        this.generatorsPlaced = generatorsPlaced;
    }

    public long getTotalItemsGenerated() {
        return totalItemsGenerated;
    }

    public void setTotalItemsGenerated(long totalItemsGenerated) {
        this.totalItemsGenerated = totalItemsGenerated;
    }

    public long getFirstPlaced() {
        return firstPlaced;
    }

    public void setFirstPlaced(long firstPlaced) {
        this.firstPlaced = firstPlaced;
    }

    public long getLastActive() {
        return lastActive;
    }

    public void setLastActive(long lastActive) {
        this.lastActive = lastActive;
    }
}
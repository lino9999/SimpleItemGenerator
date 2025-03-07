package com.Lino.SimpleItemGenerator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;

public class GeneratorListener implements Listener {
    private final SimpleItemGenerator plugin;

    public GeneratorListener(SimpleItemGenerator plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            for (String line : item.getItemMeta().getLore()) {
                if (line.startsWith("§8ID: ")) {
                    String generatorName = line.substring(6);
                    plugin.startGeneratorTask(e.getBlock().getLocation(), generatorName);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        Location loc = block.getLocation();

        if (plugin.getActiveGenerators().containsKey(loc)) {
            e.setDropItems(false);
            Player player = e.getPlayer();
            GeneratorTask task = plugin.getActiveGenerators().get(loc);

            ItemStack generatorItem = plugin.createGeneratorItem(task.getGeneratorName());
            HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(generatorItem);

            if (!remaining.isEmpty()) {
                player.getWorld().dropItemNaturally(loc, remaining.get(0));
            }

            plugin.stopGeneratorTask(loc);
            plugin.saveGenerators();
        }
    }
}
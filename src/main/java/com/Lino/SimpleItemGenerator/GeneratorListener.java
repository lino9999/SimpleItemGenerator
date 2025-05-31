package com.Lino.SimpleItemGenerator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class GeneratorListener implements Listener {
    private final SimpleItemGenerator plugin;

    public GeneratorListener(SimpleItemGenerator plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (e.isCancelled()) return;

        ItemStack item = e.getItemInHand();
        if (!isGenerator(item)) return;

        Player player = e.getPlayer();
        String generatorName = getGeneratorType(item);

        if (generatorName == null) return;

        GeneratorConfig config = plugin.getGeneratorConfig(generatorName);
        if (config == null) return;

        // Check permission
        String permission = config.getPermission();
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            player.sendMessage("§cYou don't have permission to place this generator!");
            e.setCancelled(true);
            return;
        }

        // Check limit
        int currentCount = countPlayerGenerators(player);
        int limit = plugin.getGeneratorLimit(player);

        if (currentCount >= limit) {
            player.sendMessage("§cYou have reached your generator limit of §e" + limit + "§c!");
            e.setCancelled(true);
            return;
        }

        // Place generator
        Location loc = e.getBlock().getLocation();
        plugin.startGenerator(loc, generatorName, player.getUniqueId());

        player.sendMessage("§aGenerator placed successfully! §7(" + (currentCount + 1) + "/" + limit + ")");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;

        Block block = e.getBlock();
        Location loc = block.getLocation();

        if (!plugin.getActiveGenerators().containsKey(loc)) return;

        e.setDropItems(false);
        Player player = e.getPlayer();
        GeneratorData data = plugin.getActiveGenerators().get(loc);

        // Check if player can break
        boolean isOwner = data.getPlacer().equals(player.getUniqueId());
        boolean canBreakOthers = player.hasPermission("itemgenerator.break.others");

        if (!isOwner && !canBreakOthers) {
            player.sendMessage("§cYou can only break your own generators!");
            e.setCancelled(true);
            return;
        }

        // Drop generator item
        ItemStack generatorItem = plugin.createGeneratorItem(data.getGeneratorName());
        HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(generatorItem);

        if (!remaining.isEmpty()) {
            player.getWorld().dropItemNaturally(loc, remaining.get(0));
        }

        plugin.removeGenerator(loc);

        player.sendMessage("§eGenerator removed! §7(Generated " + data.getItemsGenerated() + " items)");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = e.getClickedBlock();
        if (block == null) return;

        Location loc = block.getLocation();
        GeneratorData data = plugin.getActiveGenerators().get(loc);

        if (data == null) return;

        Player player = e.getPlayer();

        // Show generator info
        if (player.isSneaking()) {
            e.setCancelled(true);

            // Check if player can view info
            boolean isOwner = data.getPlacer().equals(player.getUniqueId());
            boolean canViewOthers = player.hasPermission("itemgenerator.info.others");

            if (!isOwner && !canViewOthers) {
                player.sendMessage("§cYou can only view your own generators!");
                return;
            }

            // Show generator info
            showGeneratorInfo(player, data);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!plugin.getConfig().getBoolean("protection.prevent-explosions", true)) return;

        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (plugin.getActiveGenerators().containsKey(block.getLocation())) {
                it.remove();
            }
        }
    }

    private boolean isGenerator(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;

        List<String> lore = meta.getLore();
        return lore.stream().anyMatch(line -> line.startsWith("§8ID: "));
    }

    private String getGeneratorType(ItemStack item) {
        if (!isGenerator(item)) return null;

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.startsWith("§8ID: ")) {
                return line.substring(6);
            }
        }
        return null;
    }

    private int countPlayerGenerators(Player player) {
        return (int) plugin.getActiveGenerators().values().stream()
                .filter(data -> data.getPlacer().equals(player.getUniqueId()))
                .count();
    }

    private void showGeneratorInfo(Player player, GeneratorData data) {
        GeneratorConfig config = data.getConfig();

        player.sendMessage("§8§m                                     ");
        player.sendMessage("§b§l" + config.getDisplayName());
        player.sendMessage("§8§m                                     ");
        player.sendMessage("§7Items Generated: §e" + data.getItemsGenerated());
        player.sendMessage("§7Cooldown: §e" + config.getCooldown() + " seconds");
        player.sendMessage("§7Owner: §e" + plugin.getServer().getOfflinePlayer(data.getPlacer()).getName());
        player.sendMessage("§8§m                                     ");
    }
}
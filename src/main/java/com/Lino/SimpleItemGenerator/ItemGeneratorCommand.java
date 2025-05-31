package com.Lino.SimpleItemGenerator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ItemGeneratorCommand implements CommandExecutor {
    private final SimpleItemGenerator plugin;

    public ItemGeneratorCommand(SimpleItemGenerator plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§b/itemgenerator give <player> <name> §7- Give a generator!");
            sender.sendMessage("§b/itemgenerator reload §7- Reload config");
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "give":
                if (!sender.hasPermission("itemgenerator.give")) {
                    sender.sendMessage("§cNo access!");
                    return false;
                }

                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /itemgenerator give <player> <generator>");
                    return false;
                }

                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return false;
                }

                String generatorName = args[2];
                if (plugin.getGeneratorConfig(generatorName) == null) {
                    sender.sendMessage("§cGenerator not found!");
                    return false;
                }

                target.getInventory().addItem(plugin.createGeneratorItem(generatorName));
                sender.sendMessage("§aGenerator §e" + generatorName + " §asent to §b" + target.getName());
                return true;

            case "reload":
                if (!sender.hasPermission("itemgenerator.reload")) {
                    sender.sendMessage("§cNo access!");
                    return false;
                }

                plugin.reloadPlugin();
                sender.sendMessage("§aConfiguration successfully reloaded!");
                return true;

            default:
                sender.sendMessage("§cUnknown command!");
                return false;
        }
    }
}
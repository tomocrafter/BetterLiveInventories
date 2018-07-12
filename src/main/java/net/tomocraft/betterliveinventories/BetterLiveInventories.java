package net.tomocraft.betterliveinventories;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;
import java.util.UUID;

public class BetterLiveInventories extends JavaPlugin {

	private final Set<UUID> lockPlayer = new HashSet<>();
	private FileConfiguration settings;

	@Override
	public void onLoad() {
		final File settingsFile = new File(getDataFolder(), "settings.yml");
		if (!settingsFile.exists()) {
			if (!settingsFile.getParentFile().mkdirs()) {
				this.getLogger().severe("Could not make directory. Check the permissions of folder.");
				this.setEnabled(false);
				return;
			}
			saveResource("settings.yml", false);
		}

		settings = new YamlConfiguration();
		try {
			settings.load(settingsFile);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
			this.setEnabled(false);
			return;
		}

		this.getLogger().info(settings.getString("reload_message"));
	}

	@Override
	public void onEnable() {
		final BetterLiveInventories self = this;
		Bukkit.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onOpen(InventoryOpenEvent e) {
				final HumanEntity p = e.getPlayer();
				final Inventory inventory = e.getInventory();
				if (!(settings.getBoolean("load_chest_inventories", true) && (inventory.getType() != InventoryType.CHEST && inventory.getType() != InventoryType.ENDER_CHEST)) && settings.getBoolean("load_custom_inventories", true))
					return;
				System.out.println(inventory.getTitle());
				if (settings.getBoolean("load_custom_inventories", true) && (!inventory.getTitle().equals("Chest") && !inventory.getTitle().equals("Double Chest") && !inventory.getTitle().equals("Ender Chest")))
					return;
				if (settings.getStringList("disabled_inventories").contains(inventory.getTitle()) || settings.getStringList("disabled_players").contains(p.getName()))
					return;
				final ListIterator<ItemStack> iterator = inventory.iterator();
				if (!iterator.hasNext()) {//if inventory is empty
					return;
				}
				e.setCancelled(true);

				lockPlayer.add(p.getUniqueId());
				final int size = inventory.getSize();
				final Inventory animationInventory = Bukkit.createInventory(p, inventory.getSize(), settings.getString("loading_inventory_title")
						.replaceAll("\\{progress}", Integer.toString(iterator.nextIndex()))
						.replaceAll("\\{slots}", Integer.toString(size))
				);
				animationInventory.setItem(iterator.nextIndex(), iterator.next());
				p.openInventory(animationInventory);
				new BukkitRunnable() {
					private Inventory lastInventory = animationInventory;

					@Override
					public void run() {
						if (iterator.hasNext()) {
							final int nextIndex = iterator.nextIndex();
							final ItemStack itemStack = iterator.next();
							final Inventory newInventory = Bukkit.createInventory(p, size, ChatColor.GRAY + "Loading... (" + nextIndex + "/" + size + ")");
							final ListIterator<ItemStack> lastInventoryIterator = this.lastInventory.iterator();
							while (lastInventoryIterator.hasNext()) {
								newInventory.setItem(lastInventoryIterator.nextIndex(), lastInventoryIterator.next());
							}
							newInventory.setItem(nextIndex, itemStack);
							this.lastInventory = newInventory;
							p.openInventory(newInventory);
						} else {
							this.cancel();
						}
					}
				}.runTaskTimer(self, settings.getInt("inventory_update_ticks"), settings.getInt("inventory_update_ticks"));
			}

			@EventHandler
			public void onDrag(final InventoryDragEvent e) {
				if (lockPlayer.contains(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
			}

			@EventHandler
			public void onClick(final InventoryClickEvent e) {
				if (lockPlayer.contains(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
			}

			@EventHandler
			public void onDamage(final EntityDamageEvent e) {
				if (lockPlayer.contains(e.getEntity().getUniqueId()) && settings.getBoolean("cancel_damage_on_inventory_load")) e.setCancelled(true);
			}
		}, this);

		getCommand("betterliveinventories").setExecutor((sender, command, label, args) -> {
			if (args.length == 0) {
				sender.sendMessage(command.getUsage());
				return false;
			}

			switch (args[0]) {
				case "reload":
				case "rl":
					if (sender.hasPermission("betterliveinventories.reload"))
						reloadConfig();
					else
						sender.sendMessage(settings.getString("no_permission"));
					break;

				case "toggle-animation":
					if (args.length < 2) {
						if (!(sender instanceof Player)) {
							sender.sendMessage(ChatColor.RED + "Execute in game!");
							return false;
						}
						settings.set("disabled_players", settings.getStringList("disabled_players").add(sender.getName()));
						sender.sendMessage(settings.getString("toggle_animation_on")
								.replaceAll("\\{player}", sender.getName()));
					} else if (sender.hasPermission("betterliveinventories.toggle_animation")) {
						if (Bukkit.getPlayer(args[1]) != null) {
							sender.sendMessage(settings.getString("player_not_found"));
							return false;
						}
						settings.set("disabled_players", settings.getStringList("disabled_players").add(args[1]));
						sender.sendMessage(settings.getString("toggle_animation_off")
								.replaceAll("\\{player}", args[1]));
					} else {
						sender.sendMessage(settings.getString("no_permission"));
					}
					break;

				default:
					sender.sendMessage(command.getUsage());
					return false;
			}
			return true;
		});
	}

	@Override
	public void onDisable() {
		try {
			this.settings.save(new File(getDataFolder(), "settings.yml"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

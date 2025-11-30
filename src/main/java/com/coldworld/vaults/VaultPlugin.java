package com.coldworld.vaults;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class VaultPlugin extends JavaPlugin implements Listener {

    // ------------------ Fields ------------------
    private static Economy econ = null;
    private final Map<UUID, Set<Integer>> unlockedVaults = new HashMap<>();
    private final Map<UUID, Integer> currentPage = new HashMap<>();

    // ------------------ Lifecycle ------------------
    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault API not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        ensureDataFolder();
        loadVaults();
        getLogger().info("VaultPlugin enabled!");
    }

    @Override
    public void onDisable() {
        saveVaults();
        getLogger().info("VaultPlugin disabled!");
    }

    // ------------------ Economy ------------------
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    // ------------------ Commands ------------------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("vault")) {

            // Reload Subcommand
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("vault.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to reload VaultPlugin!");
                    return true;
                }
                reloadVaultPlugin(sender);
                return true;
            }

            // Open Vault Selector
            if (sender instanceof Player) {
                Player player = (Player) sender;
                currentPage.put(player.getUniqueId(), 1);
                openVaultSelector(player);
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "Only players can open vaults.");
                return true;
            }
        }
        return false;
    }

    // ------------------ Reload ------------------
    private void reloadVaultPlugin(CommandSender sender) {
        reloadConfig();
        loadVaults();
        sender.sendMessage(ChatColor.GREEN + "VaultPlugin config and vaults reloaded!");
    }

    // ------------------ Vault Selector ------------------
    private void openVaultSelector(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "My Vaults");

        ItemStack whiteGlass = createGlass(Material.WHITE_STAINED_GLASS_PANE);
        ItemStack blackGlass = createGlass(Material.BLACK_STAINED_GLASS_PANE);

        // Outline with white glass
        for (int i = 0; i < 54; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) inv.setItem(i, whiteGlass);
        }

        // Vault slots (rows 1-4, cols 1-7) → 28 slots
        int vaultIndex = 0;
        UUID uuid = player.getUniqueId();
        Set<Integer> unlocked = unlockedVaults.getOrDefault(uuid, new HashSet<>());
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int slot = row * 9 + col;
                if (getConfig().contains("vault-costs." + (vaultIndex + 1))) {
                    if (unlocked.contains(vaultIndex)) {
                        inv.setItem(slot, createNamedItem(Material.PAPER, ChatColor.GREEN + "#Vault " + (vaultIndex + 1), null));
                    } else {
                        double cost = getVaultCost(vaultIndex + 1);
                        List<String> lore = Collections.singletonList(ChatColor.GREEN + "Cost: $" + cost);
                        inv.setItem(slot, createNamedItem(Material.BARRIER, ChatColor.RED + "Locked Vault " + (vaultIndex + 1), lore));
                    }
                } else {
                    inv.setItem(slot, blackGlass);
                }
                vaultIndex++;
            }
        }

        // Fill remaining empty slots with black glass
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, blackGlass);
        }

        // Exit button at slot 49
        inv.setItem(49, createNamedItem(Material.BARRIER, ChatColor.RED + "Exit", null));

        player.openInventory(inv);
    }

    // ------------------ Vault Storage ------------------
    private void openVaultStorage(Player player, int vaultNumber) {
        Inventory inv = loadVaultInventory(player.getUniqueId(), vaultNumber);

        Inventory titled = Bukkit.createInventory(null, 27, ChatColor.BLUE + "#Vault " + vaultNumber);
        titled.setContents(inv.getContents());
        inv = titled;

        // Navigation buttons
        inv.setItem(18, createNamedItem(Material.PAPER, ChatColor.YELLOW + "Previous Page", null));
        inv.setItem(22, createNamedItem(Material.BARRIER, ChatColor.RED + "Exit", null));
        inv.setItem(26, createNamedItem(Material.PAPER, ChatColor.YELLOW + "Next Page", null));

        currentPage.put(player.getUniqueId(), vaultNumber);
        player.openInventory(inv);
    }

    // ------------------ Events ------------------
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player)) return;
        Player player = (Player) who;

        String title = ChatColor.stripColor(event.getView().getTitle());
        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Vault Selector
        if (title.equals("My Vaults")) {
            event.setCancelled(true);
            if (clicked == null || !clicked.hasItemMeta()) return;

            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            if (name.equalsIgnoreCase("Exit")) {
                player.closeInventory();
            } else if (name.startsWith("#Vault")) {
                int vaultNumber = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                openVaultStorage(player, vaultNumber);
            } else if (name.startsWith("Locked Vault")) {
                int vaultNumber = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                double cost = getVaultCost(vaultNumber);
                if (econ.getBalance(player) >= cost) {
                    econ.withdrawPlayer(player, cost);
                    unlockedVaults.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(vaultNumber - 1);
                    saveVaults();
                    player.sendMessage(ChatColor.GREEN + "Unlocked Vault " + vaultNumber + " for $" + cost);
                    openVaultSelector(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Not enough money to unlock Vault " + vaultNumber + " (Cost: $" + cost + ")");
                }
            }
            return;
        }

        // Vault Storage
        if (title.startsWith("#Vault")) {
            if (clicked != null && clicked.hasItemMeta()) {
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                if (name.equalsIgnoreCase("Exit")) {
                    event.setCancelled(true);
                    new BukkitRunnable() {
                        public void run() {
                            player.closeInventory();
                        }
                    }.runTaskLater(this, 1L);

                    new BukkitRunnable() {
                        public void run() {
                            openVaultSelector(player);
                        }
                    }.runTaskLater(this, 2L);
                    return;
                } else if (name.equalsIgnoreCase("Next Page")) {
                    event.setCancelled(true);
                    int current = currentPage.getOrDefault(player.getUniqueId(), 1);
                    int next = findNextUnlocked(player.getUniqueId(), current);
                    if (next != -1) openVaultStorage(player, next);
                    else player.sendMessage(ChatColor.RED + "No next unlocked vault.");
                    return;
                } else if (name.equalsIgnoreCase("Previous Page")) {
                    event.setCancelled(true);
                    int current = currentPage.getOrDefault(player.getUniqueId(), 1);
                    int prev = findPreviousUnlocked(player.getUniqueId(), current);
                    if (prev != -1) openVaultStorage(player, prev);
                    else player.sendMessage(ChatColor.RED + "No previous unlocked vault.");
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title.startsWith("#Vault")) {
            HumanEntity who = event.getPlayer();
            if (!(who instanceof Player)) return;
            Player player = (Player) who;

            int vaultNumber;
            try {
                vaultNumber = Integer.parseInt(title.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ex) {
                return;
            }
            saveVaultInventory(player.getUniqueId(), vaultNumber, event.getInventory());
        }
    }

    // ------------------ Helpers ------------------
    private ItemStack createGlass(Material type) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNamedItem(Material type, String name, List<String> lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------ I/O ------------------
    private void ensureDataFolder() {
        File dir = new File(getDataFolder(), "data");
        if (!dir.exists() && !dir.mkdirs()) getLogger().warning("Could not create data directory: " + dir.getAbsolutePath());
    }

    private Inventory loadVaultInventory(UUID uuid, int vaultNumber) {
        ensureDataFolder();
        File file = new File(getDataFolder(), "data/" + uuid.toString() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.BLUE + "#Vault " + vaultNumber);

        List<?> list = config.getList("vault" + vaultNumber);
        if (list != null) {
            try {
                ItemStack[] items = list.toArray(new ItemStack[0]);
                inv.setContents(Arrays.copyOf(items, 27));
            } catch (ClassCastException ignored) {}
        }

        return inv;
    }

    private void saveVaultInventory(UUID uuid, int vaultNumber, Inventory inv) {
        ensureDataFolder();
        File file = new File(getDataFolder(), "data/" + uuid.toString() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ItemStack[] items = new ItemStack[inv.getSize()];
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            items[i] = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }

        config.set("vault" + vaultNumber, Arrays.asList(items));
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ------------------ Navigation ------------------
    private int findNextUnlocked(UUID uuid, int current) {
        Set<Integer> unlocked = unlockedVaults.getOrDefault(uuid, Collections.emptySet());
        int best = Integer.MAX_VALUE;
        for (int idx : unlocked) {
            int num = idx + 1;
            if (num > current && num < best) best = num;
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private int findPreviousUnlocked(UUID uuid, int current) {
        Set<Integer> unlocked = unlockedVaults.getOrDefault(uuid, Collections.emptySet());
        int best = -1;
        for (int idx : unlocked) {
            int num = idx + 1;
            if (num < current && (best == -1 || num > best)) best = num;
        }
        return best;
    }

    // ------------------ Config ------------------
    private double getVaultCost(int vaultNumber) {
        return getConfig().getDouble("vault-costs." + vaultNumber, 0.0);
    }

    // ------------------ Persistence ------------------
    private void loadVaults() {
        if (!getConfig().isConfigurationSection("unlocked")) return;
        for (String uuidStr : getConfig().getConfigurationSection("unlocked").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<Integer> list = getConfig().getIntegerList("unlocked." + uuidStr);
                unlockedVaults.put(uuid, new HashSet<>(list));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveVaults() {
        for (Map.Entry<UUID, Set<Integer>> e : unlockedVaults.entrySet()) {
            getConfig().set("unlocked." + e.getKey().toString(), new ArrayList<>(e.getValue()));
        }
        saveConfig();
    }
}

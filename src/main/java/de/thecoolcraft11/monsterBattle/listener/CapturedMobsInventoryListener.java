package de.thecoolcraft11.monsterBattle.listener;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;


public class CapturedMobsInventoryListener implements Listener {

    private final MonsterBattle plugin;
    private final Map<UUID, AnimationData> activeAnimations = new HashMap<>();

    public CapturedMobsInventoryListener(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    public static class AnimationData {
        public final Inventory inventory;
        public final List<SlotData> slots;
        public BukkitTask task;
        public int currentTick = 0;
        public int currentSlotIndex = 0;

        public AnimationData(Inventory inventory, List<SlotData> slots) {
            this.inventory = inventory;
            this.slots = slots;
        }
    }

    public static class SlotData {
        public final int slot;
        public final EntityType type;
        public final Material material;
        public final int targetCount;
        public final int total;
        public final double percentage;

        public SlotData(int slot, EntityType type, Material material, int targetCount, int total, double percentage) {
            this.slot = slot;
            this.type = type;
            this.material = material;
            this.targetCount = targetCount;
            this.total = total;
            this.percentage = percentage;
        }
    }

    public void startAnimation(Player player, Inventory inventory, List<SlotData> slotData) {
        UUID playerId = player.getUniqueId();


        stopAnimation(playerId);

        AnimationData data = new AnimationData(inventory, slotData);
        activeAnimations.put(playerId, data);


        data.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            animateTick(playerId, data);
        }, 1L, 2L);
    }

    private void animateTick(UUID playerId, AnimationData data) {

        if (data.currentSlotIndex >= data.slots.size()) {
            stopAnimation(playerId);
            return;
        }

        data.currentTick++;


        SlotData currentSlot = data.slots.get(data.currentSlotIndex);
        int currentCount = calculateCurrentCount(data.currentTick, currentSlot.targetCount);


        ItemStack stack = new ItemStack(currentSlot.material, Math.max(1, currentCount));
        ItemMeta meta = stack.getItemMeta();
        meta.setMaxStackSize(99);
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + currentSlot.type.name());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Total: " + ChatColor.AQUA + currentSlot.total);
            if (currentSlot.total > 99) {
                lore.add(ChatColor.GRAY + "Showing " + Math.min(99, currentSlot.total) + "/" + currentSlot.total + " (capped)");
            }
            lore.add(ChatColor.DARK_GRAY + String.format(Locale.US, "%.1f%% of team", currentSlot.percentage));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        data.inventory.setItem(currentSlot.slot, stack);


        if (currentCount >= currentSlot.targetCount) {

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                } catch (Exception ignored) {
                }
            }


            data.currentSlotIndex++;
            data.currentTick = 0;
        }
    }

    private int calculateCurrentCount(int tick, int targetCount) {

        int animationDuration = 20;

        if (tick >= animationDuration) {
            return targetCount;
        }


        double progress = (double) tick / animationDuration;
        progress = 1 - Math.pow(1 - progress, 3);

        return (int) Math.ceil(targetCount * progress);
    }

    private void stopAnimation(UUID playerId) {
        AnimationData data = activeAnimations.remove(playerId);
        if (data != null && data.task != null) {
            data.task.cancel();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();


        if (title.contains("Captured - ")) {

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();


        if (title.contains("Captured - ")) {

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            String title = event.getView().getTitle();
            if (title.contains("Captured - ")) {
                stopAnimation(player.getUniqueId());
            }
        }
    }
}

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
        public int lastDisplayedCount = 0;
        
        public float previousPitch = 1.0f;

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
        }, 1L, 1L);
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
        if (meta != null) {
            meta.setMaxStackSize(99);
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

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            try {
                
                if (currentCount > data.lastDisplayedCount) {
                    
                    int cappedTarget = Math.min(currentSlot.targetCount, 99);
                    float ratio = cappedTarget <= 0 ? 0f : ((float) currentCount / (float) cappedTarget);
                    float desiredPitch = 0.9f + Math.min(1.0f, ratio) * 0.6f; 

                    
                    float maxDelta = 0.15f;
                    float delta = desiredPitch - data.previousPitch;
                    if (delta > maxDelta) delta = maxDelta;
                    if (delta < -maxDelta) delta = -maxDelta;
                    float playPitch = data.previousPitch + delta;

                    
                    playPitch = Math.max(0.5f, Math.min(2.0f, playPitch));

                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, playPitch);
                    data.previousPitch = playPitch;
                    data.lastDisplayedCount = currentCount;
                }
            } catch (Exception ignored) {
            }
        }

        if (currentCount >= Math.min(currentSlot.targetCount, 99)) {
            data.currentSlotIndex++;
            data.currentTick = 0;
            data.lastDisplayedCount = 0;
            
            data.previousPitch += (1.0f - data.previousPitch) * 0.6f;
        }

        
        if (data.previousPitch != 1.0f) {
            float decay = 0.2f;
            data.previousPitch += (1.0f - data.previousPitch) * decay;
        }
    }

    /**
     * Calculate the displayed current count for the animation.
     * Uses easeInOutQuint easing and caps the displayed value at 99.
     */
    private int calculateCurrentCount(int tick, int targetCount) {
        int animationDuration = 30; 

        int cappedTarget = Math.min(targetCount, 99);

        if (tick >= animationDuration) {
            return cappedTarget;
        }

        double progress = (double) tick / animationDuration; 
        double eased = easeInOutQuint(progress);

        return (int) Math.ceil(cappedTarget * eased);
    }

    /**
     * EaseInOutQuint easing function
     *
     * @param x progress value between 0 and 1
     * @return eased value
     */
    private double easeInOutQuint(double x) {
        return x < 0.5 ? 16.0 * Math.pow(x, 5) : 1.0 - Math.pow(-2.0 * x + 2.0, 5) / 2.0;
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
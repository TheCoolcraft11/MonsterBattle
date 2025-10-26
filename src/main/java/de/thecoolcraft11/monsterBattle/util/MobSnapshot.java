package de.thecoolcraft11.monsterBattle.util;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class MobSnapshot {
    private final EntityType type;
    private final boolean baby;
    private final Integer slimeSize;
    private final String customName;
    private final boolean customNameVisible;
    private final double health;
    private final ItemStack helmet, chest, legs, boots, mainHand, offHand;
    private final List<PotionEffect> potionEffects;

    private MobSnapshot(EntityType type, boolean baby, Integer slimeSize,
                        String customName, boolean customNameVisible, double health,
                        ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots,
                        ItemStack mainHand, ItemStack offHand,
                        List<PotionEffect> potionEffects) {
        this.type = type;
        this.baby = baby;
        this.slimeSize = slimeSize;
        this.customName = customName;
        this.customNameVisible = customNameVisible;
        this.health = health;
        this.helmet = helmet;
        this.chest = chest;
        this.legs = legs;
        this.boots = boots;
        this.mainHand = mainHand;
        this.offHand = offHand;
        this.potionEffects = potionEffects;
    }

    public EntityType getType() {
        return type;
    }

    public static MobSnapshot fromEntity(LivingEntity e) {
        boolean babyFlag = false;
        if (e instanceof Ageable ageable) {
            babyFlag = !ageable.isAdult();
        }
        Integer size = null;
        if (e instanceof Slime slime) size = slime.getSize();
        Component comp = e.customName();
        String name = comp != null ? PlainTextComponentSerializer.plainText().serialize(comp) : null;
        boolean nameVis = e.isCustomNameVisible();
        double health = Math.max(0, e.getHealth());
        EntityEquipment eq = e.getEquipment();
        ItemStack helmet = eq != null ? cloneOrNull(eq.getHelmet()) : null;
        ItemStack chest = eq != null ? cloneOrNull(eq.getChestplate()) : null;
        ItemStack legs = eq != null ? cloneOrNull(eq.getLeggings()) : null;
        ItemStack boots = eq != null ? cloneOrNull(eq.getBoots()) : null;
        ItemStack main = eq != null ? cloneOrNull(eq.getItemInMainHand()) : null;
        ItemStack off = eq != null ? cloneOrNull(eq.getItemInOffHand()) : null;
        List<PotionEffect> effects = new ArrayList<>(e.getActivePotionEffects());
        return new MobSnapshot(e.getType(), babyFlag, size, name, nameVis, health,
                helmet, chest, legs, boots, main, off, effects);
    }

    public void apply(LivingEntity spawned, MonsterBattle plugin) {
        if (baby) {
            if (spawned instanceof Ageable ageable) ageable.setBaby();
        }
        if (slimeSize != null && spawned instanceof Slime slime) slime.setSize(Math.max(1, slimeSize));
        if (customName != null) {
            try {
                spawned.customName(Component.text(customName));
            } catch (NoSuchMethodError ignored) {
                spawned.customName(Component.text(customName));
            }
            spawned.setCustomNameVisible(customNameVisible);
        }
        EntityEquipment eq = spawned.getEquipment();
        if (eq != null) {
            if (helmet != null) eq.setHelmet(helmet.clone());
            if (chest != null) eq.setChestplate(chest.clone());
            if (legs != null) eq.setLeggings(legs.clone());
            if (boots != null) eq.setBoots(boots.clone());
            if (mainHand != null) eq.setItemInMainHand(mainHand.clone());
            if (offHand != null) eq.setItemInOffHand(offHand.clone());
        }


        boolean infiniteEffects = plugin.getConfig().getBoolean("monster-spawner.infinite-potion-effects", true);
        for (PotionEffect pe : potionEffects) {
            if (infiniteEffects) {

                PotionEffect infiniteEffect;
                try {
                    infiniteEffect = new PotionEffect(
                            pe.getType(),
                            Integer.MAX_VALUE,
                            pe.getAmplifier(),
                            pe.isAmbient(),
                            pe.hasParticles(),
                            pe.hasIcon()
                    );
                } catch (NoSuchMethodError | IllegalArgumentException e) {

                    infiniteEffect = new PotionEffect(
                            pe.getType(),
                            1_000_000,
                            pe.getAmplifier(),
                            pe.isAmbient(),
                            pe.hasParticles()
                    );
                }
                spawned.addPotionEffect(infiniteEffect);
            } else {
                spawned.addPotionEffect(pe);
            }
        }


        double max = spawned.getAttribute(Attribute.MAX_HEALTH) != null ? Objects.requireNonNull(spawned.getAttribute(Attribute.MAX_HEALTH)).getValue() : spawned.getHealth();
        double target;
        if (health <= 0) target = max;
        else if (health < max * 0.25) target = max;
        else target = Math.min(max, health);
        spawned.setHealth(Math.max(1.0, Math.min(max, target)));
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return (stack == null || stack.getType().isAir()) ? null : stack.clone();
    }
}

package de.thecoolcraft11.monsterBattle.util;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;


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

    
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.name());
        data.put("baby", baby);
        if (slimeSize != null) data.put("slimeSize", slimeSize);
        if (customName != null) data.put("customName", customName);
        data.put("customNameVisible", customNameVisible);
        data.put("health", health);
        if (helmet != null) data.put("helmet", helmet);
        if (chest != null) data.put("chest", chest);
        if (legs != null) data.put("legs", legs);
        if (boots != null) data.put("boots", boots);
        if (mainHand != null) data.put("mainHand", mainHand);
        if (offHand != null) data.put("offHand", offHand);

        if (!potionEffects.isEmpty()) {
            List<Map<String, Object>> effectsList = new ArrayList<>();
            for (PotionEffect effect : potionEffects) {
                Map<String, Object> effectData = new HashMap<>();
                effectData.put("type", effect.getType().getKey());
                effectData.put("duration", effect.getDuration());
                effectData.put("amplifier", effect.getAmplifier());
                effectData.put("ambient", effect.isAmbient());
                effectData.put("particles", effect.hasParticles());
                try {
                    effectData.put("icon", effect.hasIcon());
                } catch (NoSuchMethodError ignored) {
                    
                }
                effectsList.add(effectData);
            }
            data.put("potionEffects", effectsList);
        }
        return data;
    }

    public static MobSnapshot deserialize(ConfigurationSection section) {
        try {
            EntityType type = EntityType.valueOf(section.getString("type", "ZOMBIE"));
            boolean baby = section.getBoolean("baby", false);
            Integer slimeSize = section.contains("slimeSize") ? section.getInt("slimeSize") : null;
            String customName = section.getString("customName");
            boolean customNameVisible = section.getBoolean("customNameVisible", false);
            double health = section.getDouble("health", 20.0);

            ItemStack helmet = section.getItemStack("helmet");
            ItemStack chest = section.getItemStack("chest");
            ItemStack legs = section.getItemStack("legs");
            ItemStack boots = section.getItemStack("boots");
            ItemStack mainHand = section.getItemStack("mainHand");
            ItemStack offHand = section.getItemStack("offHand");

            List<PotionEffect> potionEffects = new ArrayList<>();
            if (section.contains("potionEffects")) {
                List<Map<?, ?>> effectsList = section.getMapList("potionEffects");
                for (Map<?, ?> effectData : effectsList) {
                    try {
                        String typeName = (String) effectData.get("type");
                        PotionEffectType effectType = PotionEffectType.getByName(typeName);
                        if (effectType != null) {
                            Object durationObj = effectData.get("duration");
                            Object amplifierObj = effectData.get("amplifier");
                            Object ambientObj = effectData.get("ambient");
                            Object particlesObj = effectData.get("particles");
                            Object iconObj = effectData.get("icon");

                            int duration = durationObj instanceof Number ? ((Number) durationObj).intValue() : 100;
                            int amplifier = amplifierObj instanceof Number ? ((Number) amplifierObj).intValue() : 0;
                            boolean ambient = ambientObj instanceof Boolean ? (Boolean) ambientObj : false;
                            boolean particles = particlesObj instanceof Boolean ? (Boolean) particlesObj : true;

                            PotionEffect effect;
                            try {
                                boolean icon = iconObj instanceof Boolean ? (Boolean) iconObj : true;
                                effect = new PotionEffect(effectType, duration, amplifier, ambient, particles, icon);
                            } catch (NoSuchMethodError | IllegalArgumentException e) {
                                effect = new PotionEffect(effectType, duration, amplifier, ambient, particles);
                            }
                            potionEffects.add(effect);
                        }
                    } catch (Exception ignored) {
                        
                    }
                }
            }

            return new MobSnapshot(type, baby, slimeSize, customName, customNameVisible, health,
                    helmet, chest, legs, boots, mainHand, offHand, potionEffects);
        } catch (Exception e) {
            
            return new MobSnapshot(EntityType.ZOMBIE, false, null, null, false, 20.0,
                    null, null, null, null, null, null, new ArrayList<>());
        }
    }
}

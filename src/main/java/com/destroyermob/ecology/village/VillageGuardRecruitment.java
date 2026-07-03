package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.lang.reflect.Method;

public final class VillageGuardRecruitment {
    public static final String RECRUITED_GUARD_TAG = "EcologyRecruitedGuard";

    private static final ResourceLocation GUARD_TYPE_ID = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");
    private static final TagKey<Item> GUARD_RECRUITMENT_WEAPONS =
            TagKey.create(Registries.ITEM, Ecology.id("guard_recruitment_weapons"));

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager) || !isRecruitableNitwit(villager)) {
            return;
        }

        Player player = event.getEntity();
        ItemStack heldStack = player.getItemInHand(event.getHand());
        if (!isRecruitmentWeapon(heldStack)) {
            return;
        }

        Level level = villager.level();
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (convertToGuard(serverLevel, player, villager, heldStack, event.getHand())) {
            player.displayClientMessage(Component.translatable("message.ecology.village.guard.recruited"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.ecology.village.guard.unavailable"), true);
        }
    }

    private static boolean isRecruitmentWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return stack.is(GUARD_RECRUITMENT_WEAPONS)
                || item instanceof SwordItem
                || item instanceof AxeItem
                || item instanceof TridentItem;
    }

    private static boolean isRecruitableNitwit(Villager villager) {
        return !villager.isBaby()
                && villager.getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }

    private static boolean convertToGuard(ServerLevel level, Player player, Villager villager, ItemStack heldStack, InteractionHand hand) {
        EntityType<?> guardType = BuiltInRegistries.ENTITY_TYPE.getOptional(GUARD_TYPE_ID).orElse(null);
        if (guardType == null) {
            return false;
        }

        Entity entity = guardType.create(level);
        if (!(entity instanceof Mob guard)) {
            return false;
        }

        guard.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        guard.setYHeadRot(villager.getYHeadRot());
        guard.setYBodyRot(villager.yBodyRot);
        guard.setDeltaMovement(villager.getDeltaMovement());
        guard.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), MobSpawnType.CONVERSION, null);
        guard.setPersistenceRequired();
        guard.setCustomName(villager.getCustomName());
        guard.setCustomNameVisible(villager.isCustomNameVisible());
        guard.getPersistentData().putBoolean(RECRUITED_GUARD_TAG, true);
        setGuardVariant(guard, villager);
        setOwner(guard, player);

        ItemStack guardWeapon = player.getAbilities().instabuild ? heldStack.copyWithCount(1) : heldStack.split(1);
        guard.setItemSlot(EquipmentSlot.MAINHAND, guardWeapon);
        guard.setDropChance(EquipmentSlot.MAINHAND, 100.0F);

        if (!level.addFreshEntity(guard)) {
            if (!player.getAbilities().instabuild && !player.getInventory().add(guardWeapon)) {
                player.drop(guardWeapon, false);
            }
            return false;
        }

        villager.discard();
        player.swing(hand);
        return true;
    }

    public static boolean isRecruitedGuard(Entity entity) {
        return entity.getPersistentData().getBoolean(RECRUITED_GUARD_TAG);
    }

    private static void setGuardVariant(Mob guard, Villager villager) {
        try {
            Method method = guard.getClass().getMethod("setVariant", String.class);
            method.invoke(guard, villager.getVariant().toString());
        } catch (ReflectiveOperationException ignored) {
            // Guard Villagers will keep its biome-derived default variant.
        }
    }

    private static void setOwner(Mob guard, Player player) {
        try {
            Method method = guard.getClass().getMethod("setOwnerId", java.util.UUID.class);
            method.invoke(guard, player.getUUID());
        } catch (ReflectiveOperationException ignored) {
            // Ownership is convenience state; the recruited guard still keeps the player's weapon.
        }

        try {
            Method method = guard.getClass().getMethod("getGossips");
            Object gossips = method.invoke(guard);
            Method add = gossips.getClass().getMethod("add", java.util.UUID.class, GossipType.class, int.class);
            add.invoke(gossips, player.getUUID(), GossipType.MINOR_POSITIVE, 100);
        } catch (ReflectiveOperationException ignored) {
            // Gossip support is Guard Villagers internals and may change between versions.
        }
    }
}

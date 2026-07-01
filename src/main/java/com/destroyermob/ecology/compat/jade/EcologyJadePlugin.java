package com.destroyermob.ecology.compat.jade;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeeMemory;
import com.destroyermob.ecology.bee.BeeText;
import com.destroyermob.ecology.bee.ColonyData;
import com.destroyermob.ecology.bee.ColonyHealth;
import com.destroyermob.ecology.bee.ColonyHealthIssue;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin(Ecology.MOD_ID)
public class EcologyJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(BeeProvider.INSTANCE, Bee.class);
        registration.registerBlockDataProvider(HiveProvider.INSTANCE, BeehiveBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(BeeProvider.INSTANCE, Bee.class);
        registration.registerBlockComponent(HiveProvider.INSTANCE, BeehiveBlock.class);
    }

    private enum BeeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = Ecology.id("bee");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (accessor.getEntity() instanceof Bee bee) {
                BeeMemory memory = EcologyBeeSystem.memory(bee);
                data.putString("EcologyRole", memory.role().name().toLowerCase());
                data.putLong("EcologyAge", EcologyBeeSystem.ageDays(bee.level(), memory));
                data.putInt("EcologyLifespan", bee.level() instanceof ServerLevel level
                        ? EcologyBeeSystem.lifespanDays(level, memory)
                        : memory.role().lifespanDays());
                data.putInt("EcologyRouteStops", memory.route().size());
                data.putInt("EcologyRouteIndex", memory.routeIndex());
                data.putBoolean("EcologyCarryingPollen", memory.carryingPollen());
                data.putBoolean("EcologyReturningHome", memory.returningHome());
                data.putString("EcologyAggression", memory.aggressionCause().name().toLowerCase());
                if (memory.homeHive() != null) {
                    data.putLong("EcologyHomeHive", memory.homeHive().asLong());
                }
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("EcologyRole")) {
                return;
            }

            tooltip.add(Component.translatable("jade.ecology.bee.role", title(data.getString("EcologyRole"))).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "jade.ecology.bee.age",
                    data.getLong("EcologyAge"),
                    data.getInt("EcologyLifespan")).withStyle(ChatFormatting.GRAY));

            int routeStops = data.getInt("EcologyRouteStops");
            if (routeStops > 0) {
                tooltip.add(Component.translatable(
                        "jade.ecology.bee.route",
                        Math.min(data.getInt("EcologyRouteIndex") + 1, routeStops),
                        routeStops).withStyle(ChatFormatting.GRAY));
            }
            if (data.getBoolean("EcologyCarryingPollen")) {
                tooltip.add(Component.translatable("jade.ecology.bee.pollen").withStyle(ChatFormatting.GOLD));
            }
            if (data.getBoolean("EcologyReturningHome")) {
                tooltip.add(Component.translatable("jade.ecology.bee.returning").withStyle(ChatFormatting.YELLOW));
            }
            String aggression = data.getString("EcologyAggression");
            if (!aggression.equals("none")) {
                tooltip.add(Component.translatable("jade.ecology.bee.aggression", title(aggression)).withStyle(ChatFormatting.RED));
            }
        }
    }

    private enum HiveProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = Ecology.id("hive");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof BeehiveBlockEntity hive) {
                ColonyData colony = EcologyBeeSystem.colony(hive);
                data.putInt("EcologyOccupants", hive.getOccupantCount());
                data.putInt("EcologyTotal", colony.totalBees());
                data.putInt("EcologyQueens", colony.queenCount());
                data.putInt("EcologyWorkers", colony.workerIds().size());
                data.putInt("EcologyDrones", colony.droneIds().size());
                data.putBoolean("EcologyDoomed", colony.doomed());
                data.putBoolean("EcologyAbandoned", colony.abandoned());
                data.putLong("EcologyLastChildDay", colony.lastChildDay());
                data.put("EcologyTraits", EcologyBeeSystem.traitNames(colony));
                long day = hive.getLevel() == null ? -1 : EcologyBeeSystem.colonyDay(hive.getLevel(), colony);
                data.putBoolean("EcologyCalmed", day >= 0 && colony.isCalmed(day));
                data.putBoolean("EcologySupported", day >= 0 && colony.hasApiarySupport(day));
                data.putBoolean("EcologyQueenExcluder", day >= 0 && colony.hasQueenExcluder(day));
                if (EcologyConfig.hiveHealthEnabled() && hive.getLevel() != null) {
                    ColonyHealth health = EcologyBeeSystem.colonyHealth(hive.getLevel(), hive.getBlockPos(), colony);
                    data.putInt("EcologyHealthScore", health.score());
                    data.putString("EcologyHealthStatus", health.status().name().toLowerCase());
                    ListTag issues = new ListTag();
                    for (ColonyHealthIssue issue : health.issues()) {
                        issues.add(StringTag.valueOf(issue.name().toLowerCase()));
                    }
                    data.put("EcologyHealthIssues", issues);
                }
                if (colony.matingHive() != null) {
                    data.putLong("EcologyMatingHive", colony.matingHive().asLong());
                }
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("EcologyOccupants")) {
                return;
            }

            tooltip.add(Component.translatable(
                    "jade.ecology.hive.inside",
                    data.getInt("EcologyOccupants")).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "jade.ecology.hive.colony",
                    data.getInt("EcologyTotal"),
                    data.getInt("EcologyQueens"),
                    data.getInt("EcologyWorkers"),
                    data.getInt("EcologyDrones")).withStyle(ChatFormatting.GRAY));
            if (data.getInt("EcologyQueens") <= 0) {
                tooltip.add(Component.translatable("jade.ecology.hive.queen.missing").withStyle(ChatFormatting.RED));
            }
            if (data.getInt("EcologyOccupants") != data.getInt("EcologyTotal")) {
                tooltip.add(Component.translatable("jade.ecology.hive.mismatch").withStyle(ChatFormatting.YELLOW));
            }
            ListTag traits = data.getList("EcologyTraits", Tag.TAG_STRING);
            if (!traits.isEmpty()) {
                tooltip.add(Component.translatable("jade.ecology.hive.traits", BeeText.traitList(traits)).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (data.getBoolean("EcologyCalmed") || data.getBoolean("EcologySupported") || data.getBoolean("EcologyQueenExcluder")) {
                tooltip.add(Component.translatable(
                        "jade.ecology.hive.treatments",
                        data.getBoolean("EcologyCalmed"),
                        data.getBoolean("EcologySupported"),
                        data.getBoolean("EcologyQueenExcluder")).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (data.contains("EcologyHealthScore")) {
                String healthStatus = data.getString("EcologyHealthStatus");
                tooltip.add(Component.translatable(
                        "jade.ecology.hive.health",
                        BeeText.healthStatus(healthStatus),
                        data.getInt("EcologyHealthScore")).withStyle(BeeText.healthStyle(healthStatus)));
                ListTag issues = data.getList("EcologyHealthIssues", Tag.TAG_STRING);
                for (int index = 0; index < Math.min(issues.size(), 3); index++) {
                    tooltip.add(Component.translatable(
                            "jade.ecology.hive.issue",
                            Component.translatable("ecology.health.issue." + issues.getString(index))).withStyle(ChatFormatting.YELLOW));
                }
            }
            if (data.getBoolean("EcologyDoomed")) {
                tooltip.add(Component.translatable("jade.ecology.hive.doomed").withStyle(ChatFormatting.RED));
            }
            if (data.getBoolean("EcologyAbandoned")) {
                tooltip.add(Component.translatable("jade.ecology.hive.abandoned").withStyle(ChatFormatting.YELLOW));
            }
            if (data.contains("EcologyMatingHive")) {
                BlockPos matingHive = BlockPos.of(data.getLong("EcologyMatingHive"));
                tooltip.add(Component.translatable(
                        "jade.ecology.hive.mating_target",
                        matingHive.toShortString()).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    private static String title(String value) {
        if (value.isEmpty()) {
            return value;
        }
        String normalized = value.replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}

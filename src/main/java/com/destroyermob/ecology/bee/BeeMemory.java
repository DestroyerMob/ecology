package com.destroyermob.ecology.bee;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

public class BeeMemory implements INBTSerializable<CompoundTag> {
    private UUID ecologyId = UUID.randomUUID();
    private BeeRole role = BeeRole.WORKER;
    private long birthDay = -1;
    @Nullable
    private BlockPos homeHive;
    private final List<BeeRouteStop> route = new ArrayList<>();
    private int routeIndex;
    private long routeDay = -1;
    private boolean dailyComplete;
    private boolean carryingPollen;
    private boolean returningHome;
    private int routeAgitationTicks;
    private BeeAggressionCause aggressionCause = BeeAggressionCause.NONE;
    private boolean ecologyGoalsAdded;
    @Nullable
    private BlockPos mateHive;
    @Nullable
    private BlockPos migrationTarget;
    @Nullable
    private BlockPos flowerSearchOrigin;
    @Nullable
    private BlockPos cropSearchOrigin;
    @Nullable
    private BlockPos hiveSearchOrigin;
    @Nullable
    private BlockPos foreignHiveSearchOrigin;
    @Nullable
    private BlockPos emptyHiveSearchOrigin;
    private int routeSearchMisses;

    public UUID ecologyId() {
        return ecologyId;
    }

    public BeeRole role() {
        return role;
    }

    public void setRole(BeeRole role) {
        this.role = role;
    }

    public long birthDay() {
        return birthDay;
    }

    public void setBirthDay(long birthDay) {
        this.birthDay = birthDay;
    }

    @Nullable
    public BlockPos homeHive() {
        return homeHive;
    }

    public void setHomeHive(@Nullable BlockPos homeHive) {
        this.homeHive = homeHive == null ? null : homeHive.immutable();
    }

    public List<BeeRouteStop> route() {
        return route;
    }

    public List<BlockPos> routePositions() {
        return route.stream().map(BeeRouteStop::pos).toList();
    }

    public int routeIndex() {
        return routeIndex;
    }

    public void setRouteIndex(int routeIndex) {
        this.routeIndex = routeIndex;
    }

    public long routeDay() {
        return routeDay;
    }

    public void setRouteDay(long routeDay) {
        this.routeDay = routeDay;
    }

    public boolean dailyComplete() {
        return dailyComplete;
    }

    public void setDailyComplete(boolean dailyComplete) {
        this.dailyComplete = dailyComplete;
    }

    public boolean carryingPollen() {
        return carryingPollen;
    }

    public void setCarryingPollen(boolean carryingPollen) {
        this.carryingPollen = carryingPollen;
    }

    public boolean returningHome() {
        return returningHome;
    }

    public void setReturningHome(boolean returningHome) {
        this.returningHome = returningHome;
    }

    public int routeAgitationTicks() {
        return routeAgitationTicks;
    }

    public void setRouteAgitationTicks(int routeAgitationTicks) {
        this.routeAgitationTicks = Math.max(0, routeAgitationTicks);
    }

    public BeeAggressionCause aggressionCause() {
        return aggressionCause;
    }

    public void setAggressionCause(BeeAggressionCause aggressionCause) {
        this.aggressionCause = aggressionCause;
    }

    public boolean ecologyGoalsAdded() {
        return ecologyGoalsAdded;
    }

    public void setEcologyGoalsAdded(boolean ecologyGoalsAdded) {
        this.ecologyGoalsAdded = ecologyGoalsAdded;
    }

    @Nullable
    public BlockPos mateHive() {
        return mateHive;
    }

    public void setMateHive(@Nullable BlockPos mateHive) {
        this.mateHive = mateHive == null ? null : mateHive.immutable();
    }

    @Nullable
    public BlockPos migrationTarget() {
        return migrationTarget;
    }

    public void setMigrationTarget(@Nullable BlockPos migrationTarget) {
        this.migrationTarget = migrationTarget == null ? null : migrationTarget.immutable();
    }

    @Nullable
    public BlockPos flowerSearchOrigin() {
        return flowerSearchOrigin;
    }

    public void setFlowerSearchOrigin(@Nullable BlockPos flowerSearchOrigin) {
        this.flowerSearchOrigin = flowerSearchOrigin == null ? null : flowerSearchOrigin.immutable();
    }

    @Nullable
    public BlockPos cropSearchOrigin() {
        return cropSearchOrigin;
    }

    public void setCropSearchOrigin(@Nullable BlockPos cropSearchOrigin) {
        this.cropSearchOrigin = cropSearchOrigin == null ? null : cropSearchOrigin.immutable();
    }

    @Nullable
    public BlockPos hiveSearchOrigin() {
        return hiveSearchOrigin;
    }

    public void setHiveSearchOrigin(@Nullable BlockPos hiveSearchOrigin) {
        this.hiveSearchOrigin = hiveSearchOrigin == null ? null : hiveSearchOrigin.immutable();
    }

    @Nullable
    public BlockPos foreignHiveSearchOrigin() {
        return foreignHiveSearchOrigin;
    }

    public void setForeignHiveSearchOrigin(@Nullable BlockPos foreignHiveSearchOrigin) {
        this.foreignHiveSearchOrigin = foreignHiveSearchOrigin == null ? null : foreignHiveSearchOrigin.immutable();
    }

    @Nullable
    public BlockPos emptyHiveSearchOrigin() {
        return emptyHiveSearchOrigin;
    }

    public void setEmptyHiveSearchOrigin(@Nullable BlockPos emptyHiveSearchOrigin) {
        this.emptyHiveSearchOrigin = emptyHiveSearchOrigin == null ? null : emptyHiveSearchOrigin.immutable();
    }

    public int routeSearchMisses() {
        return routeSearchMisses;
    }

    public void setRouteSearchMisses(int routeSearchMisses) {
        this.routeSearchMisses = Math.max(0, routeSearchMisses);
    }

    public void resetDailyRoute(long day) {
        this.routeDay = day;
        this.routeIndex = 0;
        this.dailyComplete = false;
        this.carryingPollen = false;
        this.returningHome = false;
        this.routeAgitationTicks = 0;
        this.aggressionCause = BeeAggressionCause.NONE;
        clearSearchOrigins();
    }

    public void replaceRoute(List<BeeRouteStop> stops) {
        this.route.clear();
        this.route.addAll(stops);
        this.routeIndex = 0;
    }

    public void clearSearchOrigins() {
        this.flowerSearchOrigin = null;
        this.cropSearchOrigin = null;
        this.hiveSearchOrigin = null;
        this.foreignHiveSearchOrigin = null;
        this.emptyHiveSearchOrigin = null;
        this.routeSearchMisses = 0;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("BeeId", ecologyId.toString());
        tag.putString("Role", role.name());
        tag.putLong("BirthDay", birthDay);
        if (homeHive != null) {
            tag.putLong("HomeHive", homeHive.asLong());
        }
        tag.putInt("RouteIndex", routeIndex);
        tag.putLong("RouteDay", routeDay);
        tag.putBoolean("DailyComplete", dailyComplete);
        tag.putBoolean("CarryingPollen", carryingPollen);
        tag.putBoolean("ReturningHome", returningHome);
        tag.putInt("RouteAgitationTicks", routeAgitationTicks);
        tag.putString("AggressionCause", aggressionCause.name());
        if (mateHive != null) {
            tag.putLong("MateHive", mateHive.asLong());
        }
        if (migrationTarget != null) {
            tag.putLong("MigrationTarget", migrationTarget.asLong());
        }
        putBlockPos(tag, "FlowerSearchOrigin", flowerSearchOrigin);
        putBlockPos(tag, "CropSearchOrigin", cropSearchOrigin);
        putBlockPos(tag, "HiveSearchOrigin", hiveSearchOrigin);
        putBlockPos(tag, "ForeignHiveSearchOrigin", foreignHiveSearchOrigin);
        putBlockPos(tag, "EmptyHiveSearchOrigin", emptyHiveSearchOrigin);
        tag.putInt("RouteSearchMisses", routeSearchMisses);
        ListTag routeTag = new ListTag();
        for (BeeRouteStop stop : route) {
            CompoundTag stopTag = new CompoundTag();
            stopTag.putLong("Pos", stop.pos().asLong());
            stopTag.putString("Type", stop.type().name());
            routeTag.add(stopTag);
        }
        tag.put("Route", routeTag);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("BeeId")) {
            this.ecologyId = parseUuid(tag.getString("BeeId"), UUID.randomUUID());
        }
        this.role = parseEnum(BeeRole.class, tag.getString("Role"), BeeRole.WORKER);
        this.birthDay = tag.getLong("BirthDay");
        this.homeHive = tag.contains("HomeHive") ? BlockPos.of(tag.getLong("HomeHive")) : null;
        this.routeIndex = tag.getInt("RouteIndex");
        this.routeDay = tag.getLong("RouteDay");
        this.dailyComplete = tag.getBoolean("DailyComplete");
        this.carryingPollen = tag.getBoolean("CarryingPollen");
        this.returningHome = tag.getBoolean("ReturningHome");
        this.routeAgitationTicks = tag.getInt("RouteAgitationTicks");
        this.aggressionCause = parseEnum(BeeAggressionCause.class, tag.getString("AggressionCause"), BeeAggressionCause.NONE);
        this.mateHive = tag.contains("MateHive") ? BlockPos.of(tag.getLong("MateHive")) : null;
        this.migrationTarget = tag.contains("MigrationTarget") ? BlockPos.of(tag.getLong("MigrationTarget")) : null;
        this.flowerSearchOrigin = readBlockPos(tag, "FlowerSearchOrigin");
        this.cropSearchOrigin = readBlockPos(tag, "CropSearchOrigin");
        this.hiveSearchOrigin = readBlockPos(tag, "HiveSearchOrigin");
        this.foreignHiveSearchOrigin = readBlockPos(tag, "ForeignHiveSearchOrigin");
        this.emptyHiveSearchOrigin = readBlockPos(tag, "EmptyHiveSearchOrigin");
        this.routeSearchMisses = tag.getInt("RouteSearchMisses");
        this.route.clear();
        ListTag routeTag = tag.getList("Route", Tag.TAG_COMPOUND);
        for (int i = 0; i < routeTag.size(); i++) {
            CompoundTag stopTag = routeTag.getCompound(i);
            BeeRouteStopType type = parseEnum(BeeRouteStopType.class, stopTag.getString("Type"), BeeRouteStopType.FLOWER);
            this.route.add(new BeeRouteStop(BlockPos.of(stopTag.getLong("Pos")), type));
        }
    }

    private static void putBlockPos(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.putLong(key, pos.asLong());
        }
    }

    @Nullable
    private static BlockPos readBlockPos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    private static UUID parseUuid(String value, UUID fallback) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}

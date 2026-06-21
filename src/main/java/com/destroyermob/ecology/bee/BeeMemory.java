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
    private BeeAggressionCause aggressionCause = BeeAggressionCause.NONE;
    private boolean ecologyGoalsAdded;
    @Nullable
    private BlockPos mateHive;
    @Nullable
    private BlockPos migrationTarget;

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

    public void resetDailyRoute(long day) {
        this.routeDay = day;
        this.routeIndex = 0;
        this.dailyComplete = false;
        this.carryingPollen = false;
        this.returningHome = false;
        this.aggressionCause = BeeAggressionCause.NONE;
    }

    public void replaceRoute(List<BeeRouteStop> stops) {
        this.route.clear();
        this.route.addAll(stops);
        this.routeIndex = 0;
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
        tag.putString("AggressionCause", aggressionCause.name());
        if (mateHive != null) {
            tag.putLong("MateHive", mateHive.asLong());
        }
        if (migrationTarget != null) {
            tag.putLong("MigrationTarget", migrationTarget.asLong());
        }
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
        this.aggressionCause = parseEnum(BeeAggressionCause.class, tag.getString("AggressionCause"), BeeAggressionCause.NONE);
        this.mateHive = tag.contains("MateHive") ? BlockPos.of(tag.getLong("MateHive")) : null;
        this.migrationTarget = tag.contains("MigrationTarget") ? BlockPos.of(tag.getLong("MigrationTarget")) : null;
        this.route.clear();
        ListTag routeTag = tag.getList("Route", Tag.TAG_COMPOUND);
        for (int i = 0; i < routeTag.size(); i++) {
            CompoundTag stopTag = routeTag.getCompound(i);
            BeeRouteStopType type = parseEnum(BeeRouteStopType.class, stopTag.getString("Type"), BeeRouteStopType.FLOWER);
            this.route.add(new BeeRouteStop(BlockPos.of(stopTag.getLong("Pos")), type));
        }
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

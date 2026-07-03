package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

public final class VillageZones extends SavedData {
    private static final String DATA_NAME = Ecology.MOD_ID + "_village_zones";
    private static final SavedData.Factory<VillageZones> FACTORY =
            new SavedData.Factory<>(VillageZones::new, VillageZones::load);
    private static final int MIN_ZONE_RADIUS = 48;
    private static final int ZONE_MERGE_RADIUS = 96;
    private static final int VERTICAL_SCAN_RANGE = 8;
    private static final long ZONE_REFRESH_INTERVAL_TICKS = 20L * 30L;
    private static final long RESIDENT_STALE_TICKS = 24000L * 7L;

    private final Map<UUID, VillageZone> zones = new HashMap<>();

    private VillageZones() {
    }

    public static VillageZones get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static void onPlayerChangedChunk(ServerLevel level, ServerPlayer player, ChunkPos chunk) {
        if (!EcologyConfig.villageEcologyEnabled()) {
            return;
        }
        refreshAndResolveCenter(level, player.blockPosition(), chunk, false);
    }

    public static Optional<BlockPos> centerFor(ServerLevel level, BlockPos origin) {
        return centerFor(level, origin, new ChunkPos(origin));
    }

    public static Optional<BlockPos> centerFor(ServerLevel level, BlockPos origin, ChunkPos chunk) {
        return get(level).findOrDiscover(level, origin, chunk).map(VillageZone::anchor);
    }

    public static Optional<BlockPos> refreshAndResolveCenter(ServerLevel level, BlockPos origin, boolean force) {
        return refreshAndResolveCenter(level, origin, new ChunkPos(origin), force);
    }

    public static Optional<BlockPos> refreshAndResolveCenter(ServerLevel level, BlockPos origin, ChunkPos chunk, boolean force) {
        VillageZones zones = get(level);
        Optional<VillageZone> zone = zones.findOrDiscover(level, origin, chunk);
        zone.filter(found -> force || found.needsRefresh(level.getGameTime()))
                .ifPresent(found -> zones.refreshZone(level, found));
        return zone.map(VillageZone::anchor);
    }

    public Collection<VillageZone> zones() {
        return zones.values();
    }

    private Optional<VillageZone> findOrDiscover(ServerLevel level, BlockPos pos, ChunkPos chunk) {
        Optional<VillageZone> existing = findZone(pos, chunk);
        if (existing.isPresent()) {
            return existing;
        }
        Optional<BlockPos> discovered = VillageEcology.discoverVillageCenter(level, pos);
        if (discovered.isEmpty()) {
            return Optional.empty();
        }
        BlockPos anchor = discovered.get();
        int radius = zoneRadius();
        if (!contains(anchor, radius, pos) && !intersects(anchor, radius, chunk)) {
            return Optional.empty();
        }
        return Optional.of(zoneFor(level, anchor));
    }

    private Optional<VillageZone> findZone(BlockPos pos, ChunkPos chunk) {
        VillageZone closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (VillageZone zone : zones.values()) {
            if (!zone.contains(pos) && !zone.intersects(chunk)) {
                continue;
            }
            double distance = zone.anchor().distSqr(pos);
            if (distance < closestDistance) {
                closest = zone;
                closestDistance = distance;
            }
        }
        return Optional.ofNullable(closest);
    }

    private VillageZone zoneFor(ServerLevel level, BlockPos anchor) {
        for (VillageZone zone : zones.values()) {
            if (zone.anchor().distSqr(anchor) <= ZONE_MERGE_RADIUS * ZONE_MERGE_RADIUS) {
                zone.moveAnchor(anchor, zoneRadius());
                setDirty();
                return zone;
            }
        }
        VillageZone zone = new VillageZone(UUID.randomUUID(), anchor.immutable(), zoneRadius(), -1L);
        zones.put(zone.id(), zone);
        setDirty();
        return zone;
    }

    private void refreshZone(ServerLevel level, VillageZone zone) {
        VillageEcology.discoverVillageCenter(level, zone.anchor())
                .filter(anchor -> anchor.distSqr(zone.anchor()) <= ZONE_MERGE_RADIUS * ZONE_MERGE_RADIUS)
                .ifPresent(anchor -> zone.moveAnchor(anchor, zoneRadius()));

        VillageHouseholds.refreshVillageHomes(level, zone.anchor(), zone.radius());
        zone.refreshResidents(level);
        setDirty();
    }

    private static int zoneRadius() {
        return Math.max(MIN_ZONE_RADIUS, EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get());
    }

    @Override
    public CompoundTag save(CompoundTag compound, HolderLookup.Provider registries) {
        ListTag zoneTags = new ListTag();
        zones.values().forEach(zone -> {
            CompoundTag zoneTag = new CompoundTag();
            zone.save(zoneTag);
            zoneTags.add(zoneTag);
        });
        compound.put("Zones", zoneTags);
        return compound;
    }

    private static VillageZones load(CompoundTag compound, HolderLookup.Provider registries) {
        VillageZones data = new VillageZones();
        ListTag zoneTags = compound.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zoneTags.size(); i++) {
            VillageZone.load(zoneTags.getCompound(i)).ifPresent(zone -> data.zones.put(zone.id(), zone));
        }
        return data;
    }

    public static final class VillageZone {
        private final UUID id;
        private final Map<UUID, VillageResident> residents = new HashMap<>();
        private BlockPos anchor;
        private int radius;
        private long lastRefreshGameTime;

        private VillageZone(UUID id, BlockPos anchor, int radius, long lastRefreshGameTime) {
            this.id = id;
            this.anchor = anchor.immutable();
            this.radius = radius;
            this.lastRefreshGameTime = lastRefreshGameTime;
        }

        public UUID id() {
            return id;
        }

        public BlockPos anchor() {
            return anchor;
        }

        public int radius() {
            return radius;
        }

        public Collection<VillageResident> residents() {
            return residents.values();
        }

        private boolean needsRefresh(long gameTime) {
            return lastRefreshGameTime < 0L || gameTime - lastRefreshGameTime >= ZONE_REFRESH_INTERVAL_TICKS;
        }

        private boolean contains(BlockPos pos) {
            return VillageZones.contains(anchor, radius, pos);
        }

        private boolean intersects(ChunkPos chunk) {
            return VillageZones.intersects(anchor, radius, chunk);
        }

        private void moveAnchor(BlockPos anchor, int radius) {
            this.anchor = anchor.immutable();
            this.radius = radius;
        }

        private void refreshResidents(ServerLevel level) {
            long gameTime = level.getGameTime();
            AABB area = AABB.encapsulatingFullBlocks(
                    anchor.offset(-radius, -VERTICAL_SCAN_RANGE, -radius),
                    anchor.offset(radius, VERTICAL_SCAN_RANGE, radius));
            for (Villager villager : level.getEntitiesOfClass(Villager.class, area, Villager::isAlive)) {
                residents.put(villager.getUUID(), VillageResident.from(level, villager));
            }
            residents.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenGameTime() > RESIDENT_STALE_TICKS);
            lastRefreshGameTime = gameTime;
        }

        private void save(CompoundTag compound) {
            compound.putUUID("ZoneId", id);
            compound.putLong("Anchor", anchor.asLong());
            compound.putInt("Radius", radius);
            compound.putLong("LastRefreshGameTime", lastRefreshGameTime);
            ListTag residentTags = new ListTag();
            residents.values().forEach(resident -> {
                CompoundTag residentTag = new CompoundTag();
                resident.save(residentTag);
                residentTags.add(residentTag);
            });
            compound.put("Residents", residentTags);
        }

        private static Optional<VillageZone> load(CompoundTag compound) {
            if (!compound.hasUUID("ZoneId") || !compound.contains("Anchor", Tag.TAG_ANY_NUMERIC)) {
                return Optional.empty();
            }
            VillageZone zone = new VillageZone(
                    compound.getUUID("ZoneId"),
                    BlockPos.of(compound.getLong("Anchor")),
                    compound.contains("Radius", Tag.TAG_ANY_NUMERIC) ? compound.getInt("Radius") : zoneRadius(),
                    compound.contains("LastRefreshGameTime", Tag.TAG_ANY_NUMERIC) ? compound.getLong("LastRefreshGameTime") : -1L);
            ListTag residentTags = compound.getList("Residents", Tag.TAG_COMPOUND);
            for (int i = 0; i < residentTags.size(); i++) {
                VillageResident.load(residentTags.getCompound(i)).ifPresent(resident -> zone.residents.put(resident.villagerId(), resident));
            }
            return Optional.of(zone);
        }
    }

    public record VillageResident(
            UUID villagerId,
            BlockPos lastSeen,
            Optional<BlockPos> home,
            Optional<BlockPos> job,
            Optional<BlockPos> meetingPoint,
            Optional<UUID> householdId,
            long lastSeenGameTime) {

        private static VillageResident from(ServerLevel level, Villager villager) {
            return new VillageResident(
                    villager.getUUID(),
                    villager.blockPosition().immutable(),
                    memoryPos(level, villager, MemoryModuleType.HOME),
                    memoryPos(level, villager, MemoryModuleType.JOB_SITE),
                    memoryPos(level, villager, MemoryModuleType.MEETING_POINT),
                    VillageHouseholds.householdId(villager),
                    level.getGameTime());
        }

        private void save(CompoundTag compound) {
            compound.putUUID("VillagerId", villagerId);
            compound.putLong("LastSeen", lastSeen.asLong());
            home.ifPresent(pos -> compound.putLong("Home", pos.asLong()));
            job.ifPresent(pos -> compound.putLong("Job", pos.asLong()));
            meetingPoint.ifPresent(pos -> compound.putLong("Meeting", pos.asLong()));
            householdId.ifPresent(id -> compound.putUUID("HouseholdId", id));
            compound.putLong("LastSeenGameTime", lastSeenGameTime);
        }

        private static Optional<VillageResident> load(CompoundTag compound) {
            if (!compound.hasUUID("VillagerId") || !compound.contains("LastSeen", Tag.TAG_ANY_NUMERIC)) {
                return Optional.empty();
            }
            return Optional.of(new VillageResident(
                    compound.getUUID("VillagerId"),
                    BlockPos.of(compound.getLong("LastSeen")),
                    optionalPos(compound, "Home"),
                    optionalPos(compound, "Job"),
                    optionalPos(compound, "Meeting"),
                    compound.hasUUID("HouseholdId") ? Optional.of(compound.getUUID("HouseholdId")) : Optional.empty(),
                    compound.contains("LastSeenGameTime", Tag.TAG_ANY_NUMERIC) ? compound.getLong("LastSeenGameTime") : -1L));
        }
    }

    private static Optional<BlockPos> memoryPos(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memoryType) {
        return villager.getBrain().getMemory(memoryType)
                .filter(pos -> pos.dimension() == level.dimension())
                .map(GlobalPos::pos)
                .map(BlockPos::immutable);
    }

    private static Optional<BlockPos> optionalPos(CompoundTag compound, String key) {
        if (!compound.contains(key, Tag.TAG_ANY_NUMERIC)) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(compound.getLong(key)));
    }

    private static boolean contains(BlockPos anchor, int radius, BlockPos pos) {
        long dx = pos.getX() - anchor.getX();
        long dz = pos.getZ() - anchor.getZ();
        return dx * dx + dz * dz <= (long)radius * radius;
    }

    private static boolean intersects(BlockPos anchor, int radius, ChunkPos chunk) {
        int dx = distanceToRange(anchor.getX(), chunk.getMinBlockX(), chunk.getMaxBlockX());
        int dz = distanceToRange(anchor.getZ(), chunk.getMinBlockZ(), chunk.getMaxBlockZ());
        return (long)dx * dx + (long)dz * dz <= (long)radius * radius;
    }

    private static int distanceToRange(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }
}

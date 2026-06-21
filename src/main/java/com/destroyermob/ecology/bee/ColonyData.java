package com.destroyermob.ecology.bee;

import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

public class ColonyData implements INBTSerializable<CompoundTag> {
    @Nullable
    private UUID queenId;
    private long queenBirthDay = -1;
    private final Set<UUID> workerIds = new LinkedHashSet<>();
    private final Set<UUID> droneIds = new LinkedHashSet<>();
    private final Map<UUID, Long> birthDays = new HashMap<>();
    private long lastSimulatedDay = -1;
    private long lastChildDay = -1;
    private long lastMatedDay = -1;
    private long fertileUntilDay = -1;
    private long lastDroneFailureDay = -1;
    private boolean abandoned;
    private boolean doomed;
    private boolean declining;
    @Nullable
    private BlockPos migrationTarget;
    @Nullable
    private BlockPos matingHive;

    @Nullable
    public UUID queenId() {
        return queenId;
    }

    public void setQueenId(@Nullable UUID queenId) {
        this.queenId = queenId;
    }

    public long queenBirthDay() {
        return queenBirthDay;
    }

    public void setQueenBirthDay(long queenBirthDay) {
        this.queenBirthDay = queenBirthDay;
    }

    public Set<UUID> workerIds() {
        return workerIds;
    }

    public Set<UUID> droneIds() {
        return droneIds;
    }

    public int queenCount() {
        return queenId == null ? 0 : 1;
    }

    public int totalBees() {
        return queenCount() + workerIds.size() + droneIds.size();
    }

    public long lastSimulatedDay() {
        return lastSimulatedDay;
    }

    public void setLastSimulatedDay(long lastSimulatedDay) {
        this.lastSimulatedDay = lastSimulatedDay;
    }

    public long lastChildDay() {
        return lastChildDay;
    }

    public void setLastChildDay(long lastChildDay) {
        this.lastChildDay = lastChildDay;
    }

    public long lastMatedDay() {
        return lastMatedDay;
    }

    public void setLastMatedDay(long lastMatedDay) {
        this.lastMatedDay = lastMatedDay;
    }

    public long fertileUntilDay() {
        return fertileUntilDay;
    }

    public void setFertileUntilDay(long fertileUntilDay) {
        this.fertileUntilDay = fertileUntilDay;
    }

    public long lastDroneFailureDay() {
        return lastDroneFailureDay;
    }

    public void setLastDroneFailureDay(long lastDroneFailureDay) {
        this.lastDroneFailureDay = lastDroneFailureDay;
    }

    public boolean abandoned() {
        return abandoned;
    }

    public void setAbandoned(boolean abandoned) {
        this.abandoned = abandoned;
    }

    public boolean doomed() {
        return doomed;
    }

    public void setDoomed(boolean doomed) {
        this.doomed = doomed;
    }

    public boolean declining() {
        return declining;
    }

    public void setDeclining(boolean declining) {
        this.declining = declining;
    }

    @Nullable
    public BlockPos migrationTarget() {
        return migrationTarget;
    }

    public void setMigrationTarget(@Nullable BlockPos migrationTarget) {
        this.migrationTarget = migrationTarget == null ? null : migrationTarget.immutable();
    }

    @Nullable
    public BlockPos matingHive() {
        return matingHive;
    }

    public void setMatingHive(@Nullable BlockPos matingHive) {
        this.matingHive = matingHive == null ? null : matingHive.immutable();
    }

    public boolean remember(BeeMemory memory) {
        boolean changed = false;
        UUID beeId = memory.ecologyId();
        Long previousBirthDay = this.birthDays.put(beeId, memory.birthDay());
        changed |= previousBirthDay == null || previousBirthDay.longValue() != memory.birthDay();

        switch (memory.role()) {
            case QUEEN -> {
                if (!beeId.equals(this.queenId)) {
                    changed |= removeRoleReferences(beeId);
                }
                changed |= !beeId.equals(this.queenId);
                changed |= this.queenBirthDay != memory.birthDay();
                this.queenId = beeId;
                this.queenBirthDay = memory.birthDay();
            }
            case WORKER -> {
                if (!this.workerIds.contains(beeId) || beeId.equals(this.queenId) || this.droneIds.contains(beeId)) {
                    changed |= removeRoleReferences(beeId);
                    changed |= this.workerIds.add(beeId);
                }
            }
            case DRONE -> {
                if (!this.droneIds.contains(beeId) || beeId.equals(this.queenId) || this.workerIds.contains(beeId)) {
                    changed |= removeRoleReferences(beeId);
                    changed |= this.droneIds.add(beeId);
                }
            }
        }
        return changed;
    }

    public void clear() {
        this.queenId = null;
        this.queenBirthDay = -1;
        this.workerIds.clear();
        this.droneIds.clear();
        this.birthDays.clear();
        this.lastSimulatedDay = -1;
        this.lastChildDay = -1;
        this.lastMatedDay = -1;
        this.fertileUntilDay = -1;
        this.lastDroneFailureDay = -1;
        this.abandoned = false;
        this.doomed = false;
        this.declining = false;
        this.migrationTarget = null;
        this.matingHive = null;
    }

    public boolean forget(BeeMemory memory) {
        boolean changed = false;
        if (memory.ecologyId().equals(this.queenId)) {
            this.queenId = null;
            this.queenBirthDay = -1;
            changed = true;
        }
        changed |= this.workerIds.remove(memory.ecologyId());
        changed |= this.droneIds.remove(memory.ecologyId());
        changed |= this.birthDays.remove(memory.ecologyId()) != null;
        return changed;
    }

    public long birthDay(UUID beeId) {
        return this.birthDays.getOrDefault(beeId, -1L);
    }

    public void removeBeeId(UUID beeId) {
        if (beeId.equals(this.queenId)) {
            this.queenId = null;
            this.queenBirthDay = -1;
        }
        this.workerIds.remove(beeId);
        this.droneIds.remove(beeId);
        this.birthDays.remove(beeId);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (queenId != null) {
            tag.putString("QueenId", queenId.toString());
        }
        tag.putLong("QueenBirthDay", queenBirthDay);
        tag.put("Workers", writeIds(workerIds));
        tag.put("Drones", writeIds(droneIds));
        tag.put("BirthDays", writeBirthDays(birthDays));
        tag.putLong("LastSimulatedDay", lastSimulatedDay);
        tag.putLong("LastChildDay", lastChildDay);
        tag.putLong("LastMatedDay", lastMatedDay);
        tag.putLong("FertileUntilDay", fertileUntilDay);
        tag.putLong("LastDroneFailureDay", lastDroneFailureDay);
        tag.putBoolean("Abandoned", abandoned);
        tag.putBoolean("Doomed", doomed);
        tag.putBoolean("Declining", declining);
        if (migrationTarget != null) {
            tag.putLong("MigrationTarget", migrationTarget.asLong());
        }
        if (matingHive != null) {
            tag.putLong("MatingHive", matingHive.asLong());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.queenId = tag.contains("QueenId") ? parseUuid(tag.getString("QueenId")) : null;
        this.queenBirthDay = tag.contains("QueenBirthDay") ? tag.getLong("QueenBirthDay") : -1;
        this.workerIds.clear();
        this.workerIds.addAll(readIds(tag.getList("Workers", Tag.TAG_STRING)));
        this.droneIds.clear();
        this.droneIds.addAll(readIds(tag.getList("Drones", Tag.TAG_STRING)));
        this.birthDays.clear();
        this.birthDays.putAll(readBirthDays(tag.getList("BirthDays", Tag.TAG_COMPOUND)));
        this.lastSimulatedDay = tag.contains("LastSimulatedDay") ? tag.getLong("LastSimulatedDay") : -1;
        this.lastChildDay = tag.getLong("LastChildDay");
        this.lastMatedDay = tag.contains("LastMatedDay") ? tag.getLong("LastMatedDay") : -1;
        this.fertileUntilDay = tag.contains("FertileUntilDay") ? tag.getLong("FertileUntilDay") : -1;
        this.lastDroneFailureDay = tag.getLong("LastDroneFailureDay");
        this.abandoned = tag.getBoolean("Abandoned");
        this.doomed = tag.getBoolean("Doomed");
        this.declining = tag.getBoolean("Declining");
        this.migrationTarget = tag.contains("MigrationTarget") ? BlockPos.of(tag.getLong("MigrationTarget")) : null;
        this.matingHive = tag.contains("MatingHive") ? BlockPos.of(tag.getLong("MatingHive")) : null;
    }

    private static ListTag writeIds(Set<UUID> ids) {
        ListTag tag = new ListTag();
        ids.forEach(id -> tag.add(StringTag.valueOf(id.toString())));
        return tag;
    }

    private static ListTag writeBirthDays(Map<UUID, Long> birthDays) {
        ListTag tag = new ListTag();
        birthDays.forEach((id, birthDay) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id.toString());
            entry.putLong("BirthDay", birthDay);
            tag.add(entry);
        });
        return tag;
    }

    private static Set<UUID> readIds(ListTag tag) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (int i = 0; i < tag.size(); i++) {
            UUID id = parseUuid(tag.getString(i));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Map<UUID, Long> readBirthDays(ListTag tag) {
        Map<UUID, Long> birthDays = new HashMap<>();
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag entry = tag.getCompound(i);
            UUID id = parseUuid(entry.getString("Id"));
            if (id != null) {
                birthDays.put(id, entry.getLong("BirthDay"));
            }
        }
        return birthDays;
    }

    private boolean removeRoleReferences(UUID beeId) {
        boolean changed = false;
        if (beeId.equals(this.queenId)) {
            this.queenId = null;
            this.queenBirthDay = -1;
            changed = true;
        }
        changed |= this.workerIds.remove(beeId);
        changed |= this.droneIds.remove(beeId);
        return changed;
    }

    @Nullable
    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

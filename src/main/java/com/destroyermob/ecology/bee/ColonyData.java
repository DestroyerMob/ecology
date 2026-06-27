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
    private UUID lineageId = UUID.randomUUID();
    private final Set<UUID> relatedLineageIds = new LinkedHashSet<>();
    private double inbreedingCoefficient;
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
    private long lastMatingHiveSearchDay = -1;
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

    public UUID lineageId() {
        return lineageId;
    }

    public Set<UUID> lineageIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(lineageId);
        ids.addAll(relatedLineageIds);
        return ids;
    }

    public boolean isRelatedTo(ColonyData other) {
        return lineageOverlap(other) > 0.0;
    }

    public double lineageOverlap(ColonyData other) {
        Set<UUID> lineages = lineageIds();
        Set<UUID> otherLineages = other.lineageIds();
        if (lineages.isEmpty() || otherLineages.isEmpty()) {
            return 0.0;
        }

        int matches = 0;
        for (UUID lineage : lineages) {
            if (otherLineages.contains(lineage)) {
                matches++;
            }
        }
        return matches / (double) Math.min(lineages.size(), otherLineages.size());
    }

    public boolean rememberMateLineages(ColonyData mate) {
        boolean changed = false;
        for (UUID lineage : mate.lineageIds()) {
            if (!lineage.equals(this.lineageId)) {
                changed |= this.relatedLineageIds.add(lineage);
            }
        }
        return changed;
    }

    public void copyGeneticsFrom(ColonyData source) {
        this.lineageId = source.lineageId;
        this.relatedLineageIds.clear();
        this.relatedLineageIds.addAll(source.relatedLineageIds);
        this.relatedLineageIds.remove(this.lineageId);
        this.inbreedingCoefficient = source.inbreedingCoefficient;
    }

    public double inbreedingCoefficient() {
        return inbreedingCoefficient;
    }

    public void setInbreedingCoefficient(double inbreedingCoefficient) {
        this.inbreedingCoefficient = clamp01(inbreedingCoefficient);
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

    public long lastMatingHiveSearchDay() {
        return lastMatingHiveSearchDay;
    }

    public void setLastMatingHiveSearchDay(long lastMatingHiveSearchDay) {
        this.lastMatingHiveSearchDay = lastMatingHiveSearchDay;
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
        this.lineageId = UUID.randomUUID();
        this.relatedLineageIds.clear();
        this.inbreedingCoefficient = 0.0;
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
        this.lastMatingHiveSearchDay = -1;
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
        tag.putString("LineageId", lineageId.toString());
        tag.put("RelatedLineages", writeIds(relatedLineageIds));
        tag.putDouble("InbreedingCoefficient", inbreedingCoefficient);
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
        tag.putLong("LastMatingHiveSearchDay", lastMatingHiveSearchDay);
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
        this.lineageId = tag.contains("LineageId") ? parseUuid(tag.getString("LineageId"), UUID.randomUUID()) : UUID.randomUUID();
        this.relatedLineageIds.clear();
        this.relatedLineageIds.addAll(readIds(tag.getList("RelatedLineages", Tag.TAG_STRING)));
        this.relatedLineageIds.remove(this.lineageId);
        this.inbreedingCoefficient = tag.contains("InbreedingCoefficient") ? clamp01(tag.getDouble("InbreedingCoefficient")) : 0.0;
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
        this.lastMatingHiveSearchDay = tag.contains("LastMatingHiveSearchDay") ? tag.getLong("LastMatingHiveSearchDay") : -1;
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

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
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
        return parseUuid(value, null);
    }

    @Nullable
    private static UUID parseUuid(String value, @Nullable UUID fallback) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}

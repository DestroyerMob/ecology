package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

public final class VillageWelfare {
    private static final int ACCESS_RADIUS = 48;
    private static final int MAX_PRESSURE = 12;

    private VillageWelfare() {
    }

    public static void tickVillager(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageWelfareEnabled()
                || villager.isBaby()
                || VillageRelocation.isRelocating(villager)
                || !(villager instanceof VillageWelfareHolder holder)) {
            return;
        }
        int interval = EcologyConfig.VILLAGE_WELFARE_CHECK_INTERVAL_TICKS.get();
        if (villager.tickCount < 200 || Math.floorMod(villager.tickCount + villager.getId() * 17, interval) != 0) {
            return;
        }

        int pressure = holder.ecology$getConfinementPressure();
        if (appearsConfined(level, villager)) {
            pressure = Math.min(MAX_PRESSURE, pressure + 1);
        } else {
            pressure = Math.max(0, pressure - 2);
        }
        holder.ecology$setConfinementPressure(pressure);
    }

    public static boolean isConfined(Villager villager) {
        if (!EcologyConfig.villageWelfareEnabled() || !(villager instanceof VillageWelfareHolder holder)) {
            return false;
        }
        return holder.ecology$getConfinementPressure() >= EcologyConfig.VILLAGE_WELFARE_GRACE_CHECKS.get();
    }

    public static int pricePenalty(Villager villager) {
        if (!isConfined(villager) || !(villager instanceof VillageWelfareHolder holder)) {
            return 0;
        }
        int pressureOverGrace = Math.max(0, holder.ecology$getConfinementPressure() - EcologyConfig.VILLAGE_WELFARE_GRACE_CHECKS.get() + 1);
        return Math.min(EcologyConfig.VILLAGE_WELFARE_MAX_PRICE_PENALTY.get(), pressureOverGrace * 2);
    }

    public static int confinedVillagerCount(ServerLevel level, BlockPos anchor) {
        if (!EcologyConfig.villageWelfareEnabled()) {
            return 0;
        }
        int radius = EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get();
        AABB area = AABB.encapsulatingFullBlocks(anchor.offset(-radius, -8, -radius), anchor.offset(radius, 8, radius));
        return level.getEntitiesOfClass(Villager.class, area, villager -> villager.isAlive() && !villager.isBaby() && isConfined(villager)).size();
    }

    public static Component diagnosticReason(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageWelfareEnabled()) {
            return Component.translatable("message.ecology.village.inspect.welfare.reason.disabled");
        }
        WelfareSignals signals = welfareSignals(level, villager);
        if (signals.cramped() && signals.noHomeAccess() && signals.noMeetingAccess()) {
            return Component.translatable("message.ecology.village.inspect.welfare.reason.cramped");
        }
        if (signals.noJobAccess() && signals.noHomeAccess() && signals.noMeetingAccess()) {
            return Component.translatable("message.ecology.village.inspect.welfare.reason.no_access");
        }
        if (signals.noHomeAccess()) {
            return Component.translatable("message.ecology.village.inspect.welfare.reason.no_home");
        }
        if (signals.noMeetingAccess()) {
            return Component.translatable("message.ecology.village.inspect.welfare.reason.no_meeting");
        }
        if (signals.noJobAccess()) {
            return Component.translatable("message.ecology.village.inspect.welfare.reason.no_work");
        }
        return Component.translatable("message.ecology.village.inspect.welfare.reason.ok");
    }

    public static String diagnosticReasonText(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageWelfareEnabled()) {
            return "welfare checks disabled";
        }
        WelfareSignals signals = welfareSignals(level, villager);
        if (signals.cramped() && signals.noHomeAccess() && signals.noMeetingAccess()) {
            return "cramped with no home or meeting access";
        }
        if (signals.noJobAccess() && signals.noHomeAccess() && signals.noMeetingAccess()) {
            return "cannot reach home, meeting space, or work";
        }
        if (signals.noHomeAccess()) {
            return "cannot reach a home bed";
        }
        if (signals.noMeetingAccess()) {
            return "cannot reach a village meeting point";
        }
        if (signals.noJobAccess()) {
            return "cannot reach assigned work";
        }
        return "has enough access to village life";
    }

    private static boolean appearsConfined(ServerLevel level, Villager villager) {
        WelfareSignals signals = welfareSignals(level, villager);

        if (signals.cramped() && signals.noHomeAccess() && signals.noMeetingAccess()) {
            return true;
        }
        return signals.noJobAccess() && signals.noHomeAccess() && signals.noMeetingAccess();
    }

    private static WelfareSignals welfareSignals(ServerLevel level, Villager villager) {
        boolean cramped = freeStandingTiles(level, villager.blockPosition()) < 4;
        boolean noHomeAccess = memoryPos(level, villager, MemoryModuleType.HOME)
                .map(home -> !canReach(villager, home))
                .orElse(true);
        boolean noMeetingAccess = memoryPos(level, villager, MemoryModuleType.MEETING_POINT)
                .map(meeting -> !canReach(villager, meeting))
                .orElse(true);
        boolean noJobAccess = VillageMarketStalls.assignedStall(level, villager)
                .map(stall -> !VillageMarketStalls.canReachAssignedStall(level, villager))
                .or(() -> memoryPos(level, villager, MemoryModuleType.JOB_SITE)
                        .map(job -> !canReach(villager, job))
                        .or(() -> Optional.of(false)))
                .orElse(false);
        return new WelfareSignals(cramped, noHomeAccess, noMeetingAccess, noJobAccess);
    }

    private static Optional<BlockPos> memoryPos(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memoryType) {
        return villager.getBrain().getMemory(memoryType)
                .filter(pos -> pos.dimension().equals(level.dimension()))
                .map(GlobalPos::pos);
    }

    private static boolean canReach(Villager villager, BlockPos target) {
        if (villager.blockPosition().distSqr(target) <= 9.0D) {
            return true;
        }
        if (villager.blockPosition().distSqr(target) > ACCESS_RADIUS * ACCESS_RADIUS) {
            return false;
        }
        Path path = villager.getNavigation().createPath(target, 1);
        return path != null && path.canReach();
    }

    private static int freeStandingTiles(ServerLevel level, BlockPos center) {
        int free = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
            if (canStandAt(level, pos)) {
                free++;
                if (free >= 4) {
                    return free;
                }
            }
        }
        return free;
    }

    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private record WelfareSignals(boolean cramped, boolean noHomeAccess, boolean noMeetingAccess, boolean noJobAccess) {
    }
}

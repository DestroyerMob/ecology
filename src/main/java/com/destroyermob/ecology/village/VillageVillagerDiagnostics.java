package com.destroyermob.ecology.village;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;

public final class VillageVillagerDiagnostics {
    private VillageVillagerDiagnostics() {
    }

    public static void sendReport(ServerLevel level, Player player, Villager villager) {
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.inspect.header",
                villager.getDisplayName(),
                formatPos(villager.blockPosition())).withStyle(ChatFormatting.GOLD));

        VillagerProfession profession = villager.getVillagerData().getProfession();
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.inspect.identity",
                professionLabel(profession),
                villager.getVillagerData().getLevel(),
                villager.getVillagerXp(),
                VillageCurrencySystem.currency(villager).serializedName()).withStyle(ChatFormatting.GRAY));

        desiredProfession(villager).ifPresent(desired -> player.sendSystemMessage(Component.translatable(
                "message.ecology.village.inspect.desired_profession",
                professionLabel(desired)).withStyle(ChatFormatting.GRAY)));

        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.inspect.brain",
                memoryPos(level, villager, MemoryModuleType.HOME).map(VillageVillagerDiagnostics::formatPos).orElse("-"),
                memoryPos(level, villager, MemoryModuleType.JOB_SITE).map(VillageVillagerDiagnostics::formatPos).orElse("-"),
                memoryPos(level, villager, MemoryModuleType.MEETING_POINT).map(VillageVillagerDiagnostics::formatPos).orElse("-")).withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.inspect.welfare",
                VillageWelfare.isConfined(villager)
                        ? Component.translatable("message.ecology.village.inspect.welfare.confined")
                        : Component.translatable("message.ecology.village.inspect.welfare.settled"),
                confinementPressure(villager),
                VillageWelfare.diagnosticReason(level, villager)).withStyle(VillageWelfare.isConfined(villager) ? ChatFormatting.YELLOW : ChatFormatting.GRAY));

        sendHousehold(level, player, villager);
        sendWorkplace(level, player, villager);
        sendRelocation(player, villager);
        sendAdvice(level, player, villager);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.65F, 1.0F);
    }

    private static void sendHousehold(ServerLevel level, Player player, Villager villager) {
        if (!(villager instanceof VillageHouseholdHolder holder) || holder.ecology$getHouseholdId().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ecology.village.inspect.household.none").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        UUID householdId = holder.ecology$getHouseholdId().get();
        VillageHouseholdLedger.HouseholdAccount account = VillageHouseholdLedger.get(level).accountFor(householdId);
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.inspect.household",
                shortUuid(householdId),
                account.home().map(VillageVillagerDiagnostics::formatPos).orElse("-"),
                account.savingsLevel()).withStyle(ChatFormatting.GRAY));
        holder.ecology$getPartnerId().ifPresent(partner -> player.sendSystemMessage(Component.translatable(
                "message.ecology.village.inspect.partner",
                shortUuid(partner)).withStyle(ChatFormatting.DARK_GRAY)));
        String parents = formatIds(holder.ecology$getFirstParentId(), holder.ecology$getSecondParentId());
        if (!parents.isBlank()) {
            player.sendSystemMessage(Component.translatable("message.ecology.village.inspect.parents", parents).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void sendWorkplace(ServerLevel level, Player player, Villager villager) {
        Optional<BlockPos> stall = VillageMarketStalls.assignedStall(level, villager);
        if (stall.isPresent()) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.inspect.stall",
                    formatPos(stall.get()),
                    VillageMarketStalls.canReachAssignedStall(level, villager)
                            ? Component.translatable("message.ecology.village.inspect.reachable")
                            : Component.translatable("message.ecology.village.inspect.unreachable")).withStyle(ChatFormatting.GRAY));
        }
        if (villager instanceof VillageTradeboardHolder holder) {
            Optional<GlobalPos> tradeboard = holder.ecology$getTradeboard();
            Optional<GlobalPos> input = holder.ecology$getTradeInput();
            Optional<GlobalPos> output = holder.ecology$getTradeOutput();
            if (tradeboard.isPresent() || input.isPresent() || output.isPresent()) {
                player.sendSystemMessage(Component.translatable(
                        "message.ecology.village.inspect.tradeboard",
                        formatGlobal(level, tradeboard),
                        formatGlobal(level, input),
                        formatGlobal(level, output)).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void sendRelocation(Player player, Villager villager) {
        if (!(villager instanceof VillageRelocationHolder holder) || holder.ecology$getRelocationTarget().isEmpty()) {
            return;
        }
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.inspect.relocation",
                holder.ecology$getRelocationTarget().map(GlobalPos::pos).map(VillageVillagerDiagnostics::formatPos).orElse("-")).withStyle(ChatFormatting.AQUA));
    }

    private static void sendAdvice(ServerLevel level, Player player, Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE && !villager.isBaby()) {
            Optional<VillagerProfession> desired = desiredProfession(villager);
            if (desired.isPresent()) {
                player.sendSystemMessage(Component.translatable(
                        "message.ecology.village.inspect.advice.desired_job",
                        professionLabel(desired.get())).withStyle(ChatFormatting.AQUA));
            } else {
                player.sendSystemMessage(Component.translatable("message.ecology.village.inspect.advice.no_job").withStyle(ChatFormatting.AQUA));
            }
        }
        if (VillageWelfare.isConfined(villager)) {
            player.sendSystemMessage(Component.translatable("message.ecology.village.inspect.advice.confined").withStyle(ChatFormatting.AQUA));
        }
        if (VillageMarketStalls.assignedStall(level, villager).isPresent()
                && !VillageMarketStalls.canReachAssignedStall(level, villager)) {
            player.sendSystemMessage(Component.translatable("message.ecology.village.inspect.advice.stall").withStyle(ChatFormatting.AQUA));
        }
    }

    private static Optional<VillagerProfession> desiredProfession(Villager villager) {
        return villager instanceof VillageVocationHolder holder ? holder.ecology$getDesiredProfession() : Optional.empty();
    }

    private static int confinementPressure(Villager villager) {
        return villager instanceof VillageWelfareHolder holder ? holder.ecology$getConfinementPressure() : 0;
    }

    private static Optional<BlockPos> memoryPos(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memoryType) {
        return villager.getBrain().getMemory(memoryType)
                .filter(pos -> pos.dimension().equals(level.dimension()))
                .map(GlobalPos::pos);
    }

    private static String formatGlobal(ServerLevel level, Optional<GlobalPos> pos) {
        if (pos.isEmpty()) {
            return "-";
        }
        if (!pos.get().dimension().equals(level.dimension())) {
            return pos.get().dimension().location().toString();
        }
        return formatPos(pos.get().pos());
    }

    private static String professionLabel(VillagerProfession profession) {
        String name = VillageVocations.professionName(profession);
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }

    private static String formatIds(Optional<UUID> first, Optional<UUID> second) {
        if (first.isEmpty() && second.isEmpty()) {
            return "";
        }
        if (first.isPresent() && second.isPresent()) {
            return shortUuid(first.get()) + ", " + shortUuid(second.get());
        }
        return first.map(VillageVillagerDiagnostics::shortUuid)
                .orElseGet(() -> second.map(VillageVillagerDiagnostics::shortUuid).orElse(""));
    }

    private static String shortUuid(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}

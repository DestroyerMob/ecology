package com.destroyermob.ecology.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class EcologyCommands {
    private static final int MAX_BLOCKS = 262_144;
    private static final int MAX_TIMES = 64;
    private static final SimpleCommandExceptionType AREA_TOO_LARGE = new SimpleCommandExceptionType(
            Component.literal("Bonemeal area is too large."));
    private static final DynamicCommandExceptionType UNKNOWN_BLOCK = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown block: " + value));

    private EcologyCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bonemeal")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(context -> run(context, 1, null))
                                .then(Commands.argument("times", IntegerArgumentType.integer(1, MAX_TIMES))
                                        .executes(context -> run(context, IntegerArgumentType.getInteger(context, "times"), null))
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder))
                                                .executes(context -> run(
                                                        context,
                                                        IntegerArgumentType.getInteger(context, "times"),
                                                        StringArgumentType.getString(context, "target")))))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder))
                                        .executes(context -> run(context, 1, StringArgumentType.getString(context, "target")))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, int times, String targetBlockId) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "to");
        Block targetBlock = targetBlockId == null ? null : resolveBlock(targetBlockId);

        Bounds bounds = Bounds.between(from, to);
        if (bounds.volume() > MAX_BLOCKS) {
            throw AREA_TOO_LARGE.create();
        }

        int matchingBlocks = 0;
        int successfulApplications = 0;
        for (int pass = 0; pass < times; pass++) {
            for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
                BlockState state = level.getBlockState(pos);
                if (targetBlock != null && !state.is(targetBlock)) {
                    continue;
                }
                if (pass == 0) {
                    matchingBlocks++;
                }
                if (applyBonemeal(level, pos, state)) {
                    successfulApplications++;
                }
            }
        }

        int finalMatchingBlocks = matchingBlocks;
        int finalSuccessfulApplications = successfulApplications;
        context.getSource().sendSuccess(
                () -> Component.literal("Applied bonemeal " + times + " time(s) across "
                        + finalMatchingBlocks + " matching block(s); "
                        + finalSuccessfulApplications + " application(s) succeeded."),
                true);
        return successfulApplications;
    }

    private static Block resolveBlock(String rawId) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            throw UNKNOWN_BLOCK.create(rawId);
        }
        return BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(() -> UNKNOWN_BLOCK.create(rawId));
    }

    private static boolean applyBonemeal(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BonemealableBlock bonemealable)) {
            return false;
        }
        if (!bonemealable.isValidBonemealTarget(level, pos, state)) {
            return false;
        }
        if (!bonemealable.isBonemealSuccess(level, level.random, pos, state)) {
            return false;
        }

        bonemealable.performBonemeal(level, level.random, pos, state);
        level.levelEvent(1505, pos, 0);
        return true;
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Bounds between(BlockPos first, BlockPos second) {
            return new Bounds(
                    Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()),
                    Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()),
                    Math.max(first.getZ(), second.getZ()));
        }

        long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }
}

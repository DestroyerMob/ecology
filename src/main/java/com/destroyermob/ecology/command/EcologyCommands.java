package com.destroyermob.ecology.command;

import com.destroyermob.ecology.bee.BeeMemory;
import com.destroyermob.ecology.bee.BeeRole;
import com.destroyermob.ecology.bee.ColonyData;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import com.destroyermob.ecology.EcologyConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class EcologyCommands {
    private static final int MAX_COMMAND_BEES = 32;
    private static final DynamicCommandExceptionType TOO_MANY_BEES = new DynamicCommandExceptionType(
            value -> Component.literal("An Ecology bee nest can only hold " + EcologyConfig.hiveCapacity()
                    + " bees, but this setup requested " + value + "."));
    private static final SimpleCommandExceptionType CREATE_BEE_FAILED = new SimpleCommandExceptionType(
            Component.literal("Could not create a test bee."));
    private static final DynamicCommandExceptionType NOT_A_BEEHIVE = new DynamicCommandExceptionType(
            value -> Component.literal("No bee nest or hive exists at " + value + "."));

    private EcologyCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("beenest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("workers", IntegerArgumentType.integer(0, MAX_COMMAND_BEES))
                                        .then(Commands.argument("drones", IntegerArgumentType.integer(0, MAX_COMMAND_BEES))
                                                .then(Commands.argument("queen", BoolArgumentType.bool())
                                                        .executes(EcologyCommands::setBeeNest))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(EcologyCommands::clearBeeNest)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(EcologyCommands::inspectBeeNest))));
    }

    private static int setBeeNest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        int workers = IntegerArgumentType.getInteger(context, "workers");
        int drones = IntegerArgumentType.getInteger(context, "drones");
        boolean queen = BoolArgumentType.getBool(context, "queen");
        int total = workers + drones + (queen ? 1 : 0);
        if (total > EcologyConfig.hiveCapacity()) {
            throw TOO_MANY_BEES.create(total);
        }

        BeehiveBlockEntity hive = resetHiveBlock(level, pos);
        ColonyData colony = EcologyBeeSystem.colony(hive);
        colony.clear();

        long day = EcologyBeeSystem.day(level);
        if (queen) {
            addTestBee(level, pos, BeeRole.QUEEN, day, colony);
        }
        for (int i = 0; i < workers; i++) {
            addTestBee(level, pos, BeeRole.WORKER, day, colony);
        }
        for (int i = 0; i < drones; i++) {
            addTestBee(level, pos, BeeRole.DRONE, day, colony);
        }

        colony.setLastSimulatedDay(day);
        colony.setDoomed(!queen || workers == 0);
        colony.setDeclining(!queen || workers == 0);
        hive.setChanged();

        context.getSource().sendSuccess(
                () -> Component.literal("Created bee nest at " + pos.toShortString()
                        + " with " + workers + " worker(s), " + drones + " drone(s), queen=" + queen + "."),
                true);
        return total;
    }

    private static int clearBeeNest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        BeehiveBlockEntity hive = resetHiveBlock(level, pos);
        EcologyBeeSystem.colony(hive).clear();
        hive.setChanged();

        context.getSource().sendSuccess(
                () -> Component.literal("Cleared bee nest at " + pos.toShortString() + "."),
                true);
        return 0;
    }

    private static int inspectBeeNest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive)) {
            throw NOT_A_BEEHIVE.create(pos.toShortString());
        }

        ColonyData colony = EcologyBeeSystem.colony(hive);
        context.getSource().sendSuccess(
                () -> Component.literal("Bee nest at " + pos.toShortString()
                        + ": inside=" + hive.getOccupantCount()
                        + ", colonyTotal=" + colony.totalBees()
                        + ", queen=" + colony.queenCount()
                        + ", workers=" + colony.workerIds().size()
                        + ", drones=" + colony.droneIds().size()
                        + ", doomed=" + colony.doomed()
                        + ", abandoned=" + colony.abandoned() + "."),
                false);
        return colony.totalBees();
    }

    private static BeehiveBlockEntity resetHiveBlock(ServerLevel level, BlockPos pos) throws CommandSyntaxException {
        BlockState existingState = level.getBlockState(pos);
        BlockState hiveState = existingState.is(BlockTags.BEEHIVES)
                ? existingState
                : Blocks.BEE_NEST.defaultBlockState();
        if (hiveState.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            hiveState = hiveState.setValue(BeehiveBlock.HONEY_LEVEL, 0);
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, hiveState, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
            return hive;
        }
        throw NOT_A_BEEHIVE.create(pos.toShortString());
    }

    private static void addTestBee(ServerLevel level, BlockPos hivePos, BeeRole role, long day, ColonyData colony)
            throws CommandSyntaxException {
        Bee bee = EntityType.BEE.create(level);
        if (bee == null) {
            throw CREATE_BEE_FAILED.create();
        }

        bee.moveTo(hivePos.getX() + 0.5, hivePos.getY() + 0.5, hivePos.getZ() + 0.5, 0.0F, 0.0F);
        bee.setHivePos(hivePos);
        bee.setPersistenceRequired();

        BeeMemory memory = EcologyBeeSystem.memory(bee);
        memory.setRole(role);
        memory.setBirthDay(day);
        memory.setHomeHive(hivePos);
        memory.resetDailyRoute(day);
        colony.remember(memory);
        EcologyBeeSystem.enterFreshHive(bee, hivePos);
    }
}

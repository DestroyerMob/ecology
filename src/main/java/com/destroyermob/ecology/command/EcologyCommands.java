package com.destroyermob.ecology.command;

import com.destroyermob.ecology.bee.BeeMemory;
import com.destroyermob.ecology.bee.BeeRole;
import com.destroyermob.ecology.bee.ColonyData;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.village.VillageGolemConstruction;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Optional;
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
    private static final DynamicCommandExceptionType GOLEM_CONSTRUCTION_RUNNING = new DynamicCommandExceptionType(
            value -> Component.literal("A village golem construction is already running near " + value + "."));
    private static final DynamicCommandExceptionType GOLEM_CONSTRUCTION_SITE_INVALID = new DynamicCommandExceptionType(
            value -> Component.literal("No safe village golem construction site at " + value
                    + ". Use a clear air block with solid support below, empty space around it, and at least one nearby villager."));
    private static final DynamicCommandExceptionType NO_VILLAGERS_TO_PRIME = new DynamicCommandExceptionType(
            value -> Component.literal("No villagers found near " + value + "."));

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

        dispatcher.register(Commands.literal("villagegolem")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("build")
                        .then(Commands.argument("base", BlockPosArgument.blockPos())
                                .executes(EcologyCommands::buildVillageGolem)))
                .then(Commands.literal("spawnbuilders")
                        .then(Commands.argument("base", BlockPosArgument.blockPos())
                                .executes(EcologyCommands::spawnVillageGolemBuilders)))
                .then(Commands.literal("prime")
                        .then(Commands.argument("center", BlockPosArgument.blockPos())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                        .executes(EcologyCommands::primeVillageGolemVillagers)))));
    }

    private static int buildVillageGolem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startVillageGolemConstruction(context, false);
    }

    private static int spawnVillageGolemBuilders(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startVillageGolemConstruction(context, true);
    }

    private static int startVillageGolemConstruction(CommandContext<CommandSourceStack> context, boolean spawnBuilders) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos base = BlockPosArgument.getLoadedBlockPos(context, "base");
        if (VillageGolemConstruction.hasActiveConstructionNear(level, base)) {
            throw GOLEM_CONSTRUCTION_RUNNING.create(base.toShortString());
        }

        Optional<VillageGolemConstruction.DebugConstructionStart> result = spawnBuilders
                ? VillageGolemConstruction.spawnDebugBuildersAndStart(level, base)
                : VillageGolemConstruction.startDebugConstruction(level, base);
        if (result.isEmpty()) {
            throw GOLEM_CONSTRUCTION_SITE_INVALID.create(base.toShortString());
        }

        VillageGolemConstruction.DebugConstructionStart start = result.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Started village golem construction at " + start.base().toShortString()
                        + " with " + start.participantCount()
                        + (spawnBuilders ? " spawned builder villager(s)." : " nearby villager(s).")),
                true);
        return start.participantCount();
    }

    private static int primeVillageGolemVillagers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos center = BlockPosArgument.getLoadedBlockPos(context, "center");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int primed = VillageGolemConstruction.primeNearbyVillagers(level, center, radius);
        if (primed == 0) {
            throw NO_VILLAGERS_TO_PRIME.create(center.toShortString());
        }

        context.getSource().sendSuccess(
                () -> Component.literal("Primed " + primed + " villager(s) near " + center.toShortString()
                        + " for vanilla golem-request testing."),
                true);
        return primed;
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

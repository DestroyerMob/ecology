package com.destroyermob.ecology.block;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.registry.EcologyItems;
import com.destroyermob.ecology.village.VillageCurrencySystem;
import com.destroyermob.ecology.village.VillagePlayerTrades;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TradeboardBlock extends BaseEntityBlock {
    public static final MapCodec<TradeboardBlock> CODEC = simpleCodec(TradeboardBlock::new);
    public static final DirectionProperty FACING = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<TradeboardPiece> PIECE = EnumProperty.create("piece", TradeboardPiece.class);

    private static final VoxelShape NORTH_SHAPE = Block.box(0.0D, 0.0D, 9.8D, 16.0D, 16.0D, 15.8D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0D, 0.0D, 0.2D, 16.0D, 16.0D, 6.2D);
    private static final VoxelShape EAST_SHAPE = Block.box(0.2D, 0.0D, 0.0D, 6.2D, 16.0D, 16.0D);
    private static final VoxelShape WEST_SHAPE = Block.box(9.8D, 0.0D, 0.0D, 15.8D, 16.0D, 16.0D);

    public TradeboardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PIECE, TradeboardPiece.SINGLE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradeboardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isHorizontal()) {
            state = state.setValue(FACING, clickedFace);
            return state.canSurvive(context.getLevel(), context.getClickedPos())
                    ? state.setValue(PIECE, pieceFor(state, context.getLevel(), context.getClickedPos()))
                    : null;
        }
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                state = state.setValue(FACING, direction.getOpposite());
                if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return state.setValue(PIECE, pieceFor(state, context.getLevel(), context.getClickedPos()));
                }
            }
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).isSolid();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state.setValue(PIECE, pieceFor(state, level, pos));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(EcologyItems.VILLAGE_LEDGER.get())) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel serverLevel && player.isShiftKeyDown()) {
                VillagePlayerTrades.recordLedgerTarget(stack, serverLevel, pos, player);
                return ItemInteractionResult.CONSUME;
            }
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!EcologyConfig.villagePlayerTradesEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.disabled"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!(level.getBlockEntity(pos) instanceof TradeboardBlockEntity tradeboard)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (VillageCurrencySystem.isCurrencyItem(stack)) {
            tradeboard.setPrice(stack.getCount());
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.65F, 1.2F);
            player.displayClientMessage(Component.translatable("message.ecology.tradeboard.price_set", tradeboard.price()), true);
            return ItemInteractionResult.CONSUME;
        }

        ItemStack sale = stack.copy();
        tradeboard.setSaleStack(sale);
        level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.65F, 1.0F);
        player.displayClientMessage(Component.translatable(
                "message.ecology.tradeboard.sale_set",
                tradeboard.saleStack().getCount(),
                tradeboard.saleStack().getHoverName()), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof TradeboardBlockEntity tradeboard)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            tradeboard.clearTrade();
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.1F);
            player.displayClientMessage(Component.translatable("message.ecology.tradeboard.cleared"), true);
            return InteractionResult.CONSUME;
        }
        if (!tradeboard.hasTrade()) {
            player.displayClientMessage(Component.translatable("message.ecology.tradeboard.empty"), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.ecology.tradeboard.summary",
                    tradeboard.saleStack().getCount(),
                    tradeboard.saleStack().getHoverName(),
                    tradeboard.price()), true);
        }
        VillagePlayerTrades.describeBoard(serverLevel, pos, player);
        return InteractionResult.CONSUME;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PIECE);
    }

    public static boolean isSameBoard(BlockState state, BlockState other) {
        return other.getBlock() == state.getBlock() && other.getValue(FACING) == state.getValue(FACING);
    }

    private static TradeboardPiece pieceFor(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction leftDirection = facing.getCounterClockWise();
        Direction rightDirection = facing.getClockWise();
        boolean up = isSameBoard(state, level.getBlockState(pos.above()));
        boolean down = isSameBoard(state, level.getBlockState(pos.below()));
        boolean left = isSameBoard(state, level.getBlockState(pos.relative(leftDirection)));
        boolean right = isSameBoard(state, level.getBlockState(pos.relative(rightDirection)));

        if (!up && !down) {
            if (!left && !right) {
                return TradeboardPiece.SINGLE;
            }
            if (!left) {
                return TradeboardPiece.LEFT_SLIM;
            }
            if (!right) {
                return TradeboardPiece.RIGHT_SLIM;
            }
            return TradeboardPiece.MIDDLE_SLIM;
        }
        if (!left && !right) {
            if (!up) {
                return TradeboardPiece.TOP;
            }
            if (!down) {
                return TradeboardPiece.BOTTOM;
            }
            return TradeboardPiece.VERTICAL;
        }
        if (!up && !left) {
            return TradeboardPiece.LEFT_TOP;
        }
        if (!up && !right) {
            return TradeboardPiece.RIGHT_TOP;
        }
        if (!down && !left) {
            return TradeboardPiece.LEFT_BOTTOM;
        }
        if (!down && !right) {
            return TradeboardPiece.RIGHT_BOTTOM;
        }
        if (!up) {
            return TradeboardPiece.MIDDLE_TOP;
        }
        if (!down) {
            return TradeboardPiece.MIDDLE_BOTTOM;
        }
        if (!left) {
            return TradeboardPiece.LEFT_MIDDLE;
        }
        if (!right) {
            return TradeboardPiece.RIGHT_MIDDLE;
        }
        return TradeboardPiece.MIDDLE;
    }
}

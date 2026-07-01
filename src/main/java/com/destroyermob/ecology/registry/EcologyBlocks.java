package com.destroyermob.ecology.registry;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.block.TradeboardBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EcologyBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Ecology.MOD_ID);

    public static final DeferredBlock<TradeboardBlock> TRADEBOARD = BLOCKS.registerBlock(
            "tradeboard",
            TradeboardBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD).noOcclusion());

    private EcologyBlocks() {
    }
}

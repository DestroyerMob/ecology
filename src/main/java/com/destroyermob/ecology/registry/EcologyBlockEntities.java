package com.destroyermob.ecology.registry;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.block.TradeboardBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EcologyBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ecology.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TradeboardBlockEntity>> TRADEBOARD =
            BLOCK_ENTITY_TYPES.register("tradeboard", () -> BlockEntityType.Builder
                    .of(TradeboardBlockEntity::new, EcologyBlocks.TRADEBOARD.get())
                    .build(null));

    private EcologyBlockEntities() {
    }
}

package com.destroyermob.ecology.block;

import com.destroyermob.ecology.registry.EcologyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TradeboardBlockEntity extends BlockEntity {
    private static final String SALE_STACK_TAG = "SaleStack";
    private static final String PRICE_TAG = "Price";
    private static final int DEFAULT_PRICE = 1;

    private ItemStack saleStack = ItemStack.EMPTY;
    private int price = DEFAULT_PRICE;

    public TradeboardBlockEntity(BlockPos pos, BlockState blockState) {
        super(EcologyBlockEntities.TRADEBOARD.get(), pos, blockState);
    }

    public boolean hasTrade() {
        return !saleStack.isEmpty() && price > 0;
    }

    public ItemStack saleStack() {
        return saleStack.copy();
    }

    public int price() {
        return price;
    }

    public void setSaleStack(ItemStack stack) {
        if (stack.isEmpty()) {
            saleStack = ItemStack.EMPTY;
        } else {
            saleStack = stack.copy();
            saleStack.setCount(Math.max(1, Math.min(stack.getCount(), stack.getMaxStackSize())));
        }
        setChanged();
    }

    public void setPrice(int price) {
        this.price = Math.max(1, Math.min(64, price));
        setChanged();
    }

    public void clearTrade() {
        saleStack = ItemStack.EMPTY;
        price = DEFAULT_PRICE;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!saleStack.isEmpty()) {
            tag.put(SALE_STACK_TAG, saleStack.saveOptional(registries));
        }
        tag.putInt(PRICE_TAG, price);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        saleStack = tag.contains(SALE_STACK_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound(SALE_STACK_TAG))
                : ItemStack.EMPTY;
        price = tag.contains(PRICE_TAG, Tag.TAG_ANY_NUMERIC) ? Math.max(1, Math.min(64, tag.getInt(PRICE_TAG))) : DEFAULT_PRICE;
    }
}

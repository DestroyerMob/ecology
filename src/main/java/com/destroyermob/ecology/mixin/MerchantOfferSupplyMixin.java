package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.village.VillageSupplyCategory;
import com.destroyermob.ecology.village.VillageSupplyOfferHolder;
import com.destroyermob.ecology.village.VillagePlayerTrades;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantOffer.class)
public abstract class MerchantOfferSupplyMixin implements VillageSupplyOfferHolder {
    @Unique
    private int ecology$baseMaxUses = -1;
    @Unique
    private int ecology$baseSpecialPriceDiff = Integer.MIN_VALUE;
    @Unique
    private String ecology$supplyCategory = "";
    @Unique
    private boolean ecology$supplyBuyingOffer;

    @Override
    public int ecology$getBaseMaxUses() {
        return ecology$baseMaxUses;
    }

    @Override
    public void ecology$setBaseMaxUses(int baseMaxUses) {
        ecology$baseMaxUses = baseMaxUses;
    }

    @Override
    public int ecology$getBaseSpecialPriceDiff() {
        return ecology$baseSpecialPriceDiff;
    }

    @Override
    public void ecology$setBaseSpecialPriceDiff(int baseSpecialPriceDiff) {
        ecology$baseSpecialPriceDiff = baseSpecialPriceDiff;
    }

    @Override
    public Optional<VillageSupplyCategory> ecology$getSupplyCategory() {
        return VillageSupplyCategory.byName(ecology$supplyCategory);
    }

    @Override
    public void ecology$setSupplyCategory(Optional<VillageSupplyCategory> category) {
        ecology$supplyCategory = category.map(VillageSupplyCategory::serializedName).orElse("");
    }

    @Override
    public boolean ecology$isSupplyBuyingOffer() {
        return ecology$supplyBuyingOffer;
    }

    @Override
    public void ecology$setSupplyBuyingOffer(boolean buyingOffer) {
        ecology$supplyBuyingOffer = buyingOffer;
    }

    @Inject(method = "assemble", at = @At("RETURN"), cancellable = true)
    private void ecology$stripPlayerStockedTradeMarker(CallbackInfoReturnable<ItemStack> callback) {
        callback.setReturnValue(VillagePlayerTrades.stripTradeMarker(callback.getReturnValue()));
    }

    @Inject(method = "resetUses", at = @At("HEAD"), cancellable = true)
    private void ecology$preventPlayerStockedTradeRestock(CallbackInfo callback) {
        MerchantOffer offer = (MerchantOffer)(Object)this;
        if (VillagePlayerTrades.isPlayerStocked(offer)) {
            offer.setToOutOfStock();
            callback.cancel();
        }
    }
}

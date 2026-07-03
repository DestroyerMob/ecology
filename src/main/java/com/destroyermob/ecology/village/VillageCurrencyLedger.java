package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillageCurrencyLedger extends SavedData {
    private static final String DATA_NAME = Ecology.MOD_ID + "_village_currencies";
    private static final SavedData.Factory<VillageCurrencyLedger> FACTORY =
            new SavedData.Factory<>(VillageCurrencyLedger::new, VillageCurrencyLedger::load);
    private static final int VILLAGE_KEY_SIZE = 96;

    private final Map<VillageKey, VillageCurrencyAccount> accounts = new HashMap<>();

    public static VillageCurrencyLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public VillageCurrency currencyFor(ServerLevel level, BlockPos anchor) {
        VillageCurrencyAccount account = accountFor(anchor);
        if (account.currency().isEmpty() || !VillageCurrencySystem.isEligibleCurrency(account.currency().get())) {
            account.setCurrency(VillageCurrencySystem.naturalCurrencyFor(level, anchor), false);
            setDirty();
        }
        return account.currency().orElse(VillageCurrency.EMERALD);
    }

    public void setCurrency(BlockPos anchor, VillageCurrency currency, boolean playerSet) {
        accountFor(anchor).setCurrency(currency, playerSet);
        setDirty();
    }

    public Optional<VillageCurrency> storedCurrency(BlockPos anchor) {
        return accountFor(anchor).currency();
    }

    public boolean isPlayerSet(BlockPos anchor) {
        return accountFor(anchor).playerSet();
    }

    @Override
    public CompoundTag save(CompoundTag compound, HolderLookup.Provider registries) {
        ListTag accountTags = new ListTag();
        accounts.forEach((key, account) -> {
            if (account.currency().isEmpty()) {
                return;
            }
            CompoundTag accountTag = new CompoundTag();
            accountTag.putInt("CellX", key.cellX());
            accountTag.putInt("CellZ", key.cellZ());
            account.save(accountTag);
            accountTags.add(accountTag);
        });
        compound.put("Accounts", accountTags);
        return compound;
    }

    private VillageCurrencyAccount accountFor(BlockPos anchor) {
        return accounts.computeIfAbsent(VillageKey.from(anchor), ignored -> new VillageCurrencyAccount());
    }

    private static VillageCurrencyLedger load(CompoundTag compound, HolderLookup.Provider registries) {
        VillageCurrencyLedger ledger = new VillageCurrencyLedger();
        ListTag accountTags = compound.getList("Accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accountTags.size(); i++) {
            CompoundTag accountTag = accountTags.getCompound(i);
            VillageKey key = new VillageKey(accountTag.getInt("CellX"), accountTag.getInt("CellZ"));
            ledger.accounts.put(key, VillageCurrencyAccount.load(accountTag));
        }
        return ledger;
    }

    private record VillageKey(int cellX, int cellZ) {
        private static VillageKey from(BlockPos anchor) {
            return new VillageKey(Math.floorDiv(anchor.getX(), VILLAGE_KEY_SIZE), Math.floorDiv(anchor.getZ(), VILLAGE_KEY_SIZE));
        }
    }

    private static final class VillageCurrencyAccount {
        private VillageCurrency currency;
        private boolean playerSet;

        Optional<VillageCurrency> currency() {
            return Optional.ofNullable(currency);
        }

        boolean playerSet() {
            return playerSet;
        }

        void setCurrency(VillageCurrency currency, boolean playerSet) {
            this.currency = currency;
            this.playerSet = playerSet;
        }

        void save(CompoundTag compound) {
            compound.putString("Currency", currency.serializedName());
            compound.putBoolean("PlayerSet", playerSet);
        }

        static VillageCurrencyAccount load(CompoundTag compound) {
            VillageCurrencyAccount account = new VillageCurrencyAccount();
            if (compound.contains("Currency", Tag.TAG_STRING)) {
                VillageCurrency.optionalByName(compound.getString("Currency")).ifPresent(currency -> account.currency = currency);
            }
            account.playerSet = compound.getBoolean("PlayerSet");
            return account;
        }
    }
}

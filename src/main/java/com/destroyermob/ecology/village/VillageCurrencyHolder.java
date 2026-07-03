package com.destroyermob.ecology.village;

public interface VillageCurrencyHolder {
    VillageCurrency ecology$getVillageCurrency();

    void ecology$setVillageCurrency(VillageCurrency currency);

    default VillageCurrency ecology$getLeftEyeCurrency() {
        return ecology$getVillageCurrency();
    }

    default VillageCurrency ecology$getRightEyeCurrency() {
        return ecology$getVillageCurrency();
    }

    default void ecology$setEyeCurrencies(VillageCurrency left, VillageCurrency right) {
        ecology$setVillageCurrency(left);
    }
}

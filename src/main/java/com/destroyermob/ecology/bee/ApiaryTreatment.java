package com.destroyermob.ecology.bee;

public enum ApiaryTreatment {
    SMOKE("smoke"),
    HIVE_STAND("hive_stand"),
    QUEEN_EXCLUDER("queen_excluder"),
    BROOD_FRAME("brood_frame");

    private final String serializedName;

    ApiaryTreatment(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}

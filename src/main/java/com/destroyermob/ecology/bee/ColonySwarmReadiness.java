package com.destroyermob.ecology.bee;

enum ColonySwarmReadiness {
    FEATURE_DISABLED,
    NO_COLONY,
    QUEEN_EXCLUDED,
    MISSING_QUEEN,
    NEEDS_WORKERS,
    UNHEALTHY,
    COOLDOWN,
    NOT_CROWDED,
    INBRED,
    LOW_FORAGE,
    READY;

    boolean ready() {
        return this == READY;
    }
}

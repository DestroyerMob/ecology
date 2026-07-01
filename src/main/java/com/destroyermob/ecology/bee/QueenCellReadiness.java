package com.destroyermob.ecology.bee;

enum QueenCellReadiness {
    NO_COLONY,
    MISSING_QUEEN,
    NEEDS_WORKERS,
    UNHEALTHY,
    LOW_HEALTH,
    NO_BROOD_NEED,
    READY;

    boolean ready() {
        return this == READY;
    }
}

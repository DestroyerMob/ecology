package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.EcologyConfig;

public enum BeeRole {
    WORKER,
    DRONE,
    QUEEN;

    public int lifespanDays() {
        return switch (this) {
            case WORKER -> EcologyConfig.WORKER_LIFESPAN_DAYS.get();
            case DRONE -> EcologyConfig.DRONE_LIFESPAN_DAYS.get();
            case QUEEN -> EcologyConfig.QUEEN_LIFESPAN_DAYS.get();
        };
    }
}

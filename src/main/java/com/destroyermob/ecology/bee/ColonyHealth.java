package com.destroyermob.ecology.bee;

import java.util.List;

public record ColonyHealth(int score, ColonyHealthStatus status, List<ColonyHealthIssue> issues) {
    public boolean supportsPollinationBonus() {
        return score >= 75 && (status == ColonyHealthStatus.STABLE || status == ColonyHealthStatus.THRIVING);
    }

    public static ColonyHealth of(int score, List<ColonyHealthIssue> issues) {
        int clampedScore = Math.max(0, Math.min(100, score));
        ColonyHealthStatus status;
        if (issues.contains(ColonyHealthIssue.NO_COLONY)) {
            status = ColonyHealthStatus.EMPTY;
        } else if (clampedScore >= 90 && issues.isEmpty()) {
            status = ColonyHealthStatus.THRIVING;
        } else if (clampedScore >= 70) {
            status = ColonyHealthStatus.STABLE;
        } else if (clampedScore >= 40) {
            status = ColonyHealthStatus.STRUGGLING;
        } else {
            status = ColonyHealthStatus.FAILING;
        }
        return new ColonyHealth(clampedScore, status, List.copyOf(issues));
    }
}

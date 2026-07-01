package com.destroyermob.ecology.bee;

import java.util.Arrays;
import java.util.List;
import net.minecraft.network.chat.Component;

public record BeekeeperActionCheck(boolean allowed, String translationKey, List<Object> args) {
    public static BeekeeperActionCheck allow(String translationKey, Object... args) {
        return new BeekeeperActionCheck(true, translationKey, List.copyOf(Arrays.asList(args)));
    }

    public static BeekeeperActionCheck deny(String translationKey, Object... args) {
        return new BeekeeperActionCheck(false, translationKey, List.copyOf(Arrays.asList(args)));
    }

    public Component message() {
        return Component.translatable(translationKey, args.toArray());
    }
}

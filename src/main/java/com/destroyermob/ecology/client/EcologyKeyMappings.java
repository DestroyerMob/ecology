package com.destroyermob.ecology.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class EcologyKeyMappings {
    public static final String CATEGORY = "key.categories.ecology";
    public static final KeyMapping LOCK_BEE_ROUTE = new KeyMapping(
            "key.ecology.lock_bee_route",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY);

    private EcologyKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(LOCK_BEE_ROUTE);
    }
}

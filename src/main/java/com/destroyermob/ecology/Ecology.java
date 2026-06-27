package com.destroyermob.ecology;

import com.destroyermob.ecology.command.EcologyCommands;
import com.destroyermob.ecology.bee.BeeNestSealingEvents;
import com.destroyermob.ecology.network.EcologyNetworking;
import com.destroyermob.ecology.registry.EcologyAttachments;
import com.destroyermob.ecology.registry.EcologyItems;
import com.destroyermob.ecology.village.VillageGolemEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Ecology.MOD_ID)
public class Ecology {
    public static final String MOD_ID = "ecology";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ecology(IEventBus modEventBus, ModContainer modContainer) {
        EcologyItems.ITEMS.register(modEventBus);
        EcologyItems.CREATIVE_MODE_TABS.register(modEventBus);
        EcologyAttachments.ATTACHMENT_TYPES.register(modEventBus);

        modEventBus.addListener(EcologyNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(EcologyCommands::register);
        NeoForge.EVENT_BUS.register(new BeeNestSealingEvents());
        NeoForge.EVENT_BUS.register(new com.destroyermob.ecology.bee.EcologyBeeEvents());
        NeoForge.EVENT_BUS.register(new VillageGolemEvents());

        modContainer.registerConfig(ModConfig.Type.COMMON, EcologyConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}

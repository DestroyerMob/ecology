package com.destroyermob.ecology;

import com.destroyermob.ecology.client.WaxGogglesClientEvents;
import com.destroyermob.ecology.client.EcologyKeyMappings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Ecology.MOD_ID, dist = Dist.CLIENT)
public class EcologyClient {
    public EcologyClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(EcologyKeyMappings::register);
        NeoForge.EVENT_BUS.register(new WaxGogglesClientEvents());
    }
}

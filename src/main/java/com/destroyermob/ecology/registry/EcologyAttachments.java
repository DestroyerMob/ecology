package com.destroyermob.ecology.registry;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.bee.BeeMemory;
import com.destroyermob.ecology.bee.ColonyData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class EcologyAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Ecology.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BeeMemory>> BEE_MEMORY =
            ATTACHMENT_TYPES.register("bee_memory", () -> AttachmentType.serializable(BeeMemory::new).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ColonyData>> COLONY =
            ATTACHMENT_TYPES.register("colony", () -> AttachmentType.serializable(ColonyData::new).build());

    private EcologyAttachments() {
    }
}

package com.destroyermob.ecology.network;

import com.destroyermob.ecology.Ecology;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillageLedgerPayload(List<String> pages) implements CustomPacketPayload {
    private static final int MAX_PAGES = 32;
    private static final int MAX_PAGE_LENGTH = 2048;
    public static final Type<VillageLedgerPayload> TYPE = new Type<>(Ecology.id("village_ledger"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageLedgerPayload> STREAM_CODEC = StreamCodec.of(
            VillageLedgerPayload::encode,
            VillageLedgerPayload::decode);

    public VillageLedgerPayload {
        pages = pages.stream()
                .limit(MAX_PAGES)
                .map(VillageLedgerPayload::trimPage)
                .toList();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillageLedgerPayload payload) {
        int count = Math.min(payload.pages.size(), MAX_PAGES);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buffer.writeUtf(trimPage(payload.pages.get(i)), MAX_PAGE_LENGTH);
        }
    }

    private static VillageLedgerPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = Math.max(0, Math.min(buffer.readVarInt(), MAX_PAGES));
        java.util.ArrayList<String> pages = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pages.add(buffer.readUtf(MAX_PAGE_LENGTH));
        }
        return new VillageLedgerPayload(List.copyOf(pages));
    }

    private static String trimPage(String page) {
        if (page == null || page.isBlank()) {
            return "";
        }
        return page.length() <= MAX_PAGE_LENGTH ? page : page.substring(0, MAX_PAGE_LENGTH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

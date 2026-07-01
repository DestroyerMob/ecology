package com.destroyermob.ecology.client;

import com.destroyermob.ecology.network.VillageLedgerPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VillageLedgerClient {
    private VillageLedgerClient() {
    }

    public static void open(VillageLedgerPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> pages = payload.pages().isEmpty()
                ? List.of(Component.literal("Village Ledger\n\nNo report was available."))
                : payload.pages().stream().map(page -> (Component)Component.literal(page)).toList();
        minecraft.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
    }
}

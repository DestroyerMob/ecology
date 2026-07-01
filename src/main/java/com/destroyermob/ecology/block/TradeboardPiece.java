package com.destroyermob.ecology.block;

import net.minecraft.util.StringRepresentable;

public enum TradeboardPiece implements StringRepresentable {
    SINGLE("single", "tradeboard"),
    SLIM("slim", "tradeboard_slim"),
    LEFT_SLIM("left_slim", "tradeboard_left_slim"),
    MIDDLE_SLIM("middle_slim", "tradeboard_middle_slim"),
    RIGHT_SLIM("right_slim", "tradeboard_right_slim"),
    TOP("top", "tradeboard_top"),
    BOTTOM("bottom", "tradeboard_bottom"),
    LEFT("left", "tradeboard_left"),
    RIGHT("right", "tradeboard_right"),
    HORIZONTAL("horizontal", "tradeboard_horizontal"),
    VERTICAL("vertical", "tradeboard_vertical"),
    LEFT_TOP("left_top", "tradeboard_left_top"),
    MIDDLE_TOP("middle_top", "tradeboard_middle_top"),
    RIGHT_TOP("right_top", "tradeboard_right_top"),
    LEFT_MIDDLE("left_middle", "tradeboard_left_middle"),
    MIDDLE("middle", "tradeboard_middle"),
    RIGHT_MIDDLE("right_middle", "tradeboard_right_middle"),
    LEFT_BOTTOM("left_bottom", "tradeboard_left_bottom"),
    MIDDLE_BOTTOM("middle_bottom", "tradeboard_middle_bottom"),
    RIGHT_BOTTOM("right_bottom", "tradeboard_right_bottom");

    private final String serializedName;
    private final String textureName;

    TradeboardPiece(String serializedName, String textureName) {
        this.serializedName = serializedName;
        this.textureName = textureName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String textureName() {
        return textureName;
    }
}

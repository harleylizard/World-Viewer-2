package dev.corgitaco.worldviewer.client.tile;

import org.jetbrains.annotations.Nullable;

public interface ScreenTileLayer {


    int getMinTileWorldX();

    int getMinTileWorldZ();

    int getMaxTileWorldX();

    int getMaxTileWorldZ();

    @Nullable
    int[] image();

    int size();
}

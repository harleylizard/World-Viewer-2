package dev.corgitaco.worldviewer.client.tile;

import org.jetbrains.annotations.Nullable;

public interface ScreenTileLayer extends Tile {

    @Nullable
    int[] image();

    int size();
}

package dev.corgitaco.worldviewer.client.tile;

import dev.corgitaco.worldviewer.client.IconLookup;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public interface RenderTileContext {


    CoordinateShiftManager coordinateShiftManager();

    BoundingBox tileArea();

    BoundingBox worldViewArea();

    IconLookup iconLookup();

    int xTileRange();

    int zTileRange();
}

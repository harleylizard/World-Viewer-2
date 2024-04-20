package dev.corgitaco.worldviewer.client.tile;

import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

public interface RenderTileContext {


    CoordinateShiftManager coordinateShiftManager();

    @Nullable
    BoundingBox worldViewArea();

    int xTileRange();

    int zTileRange();
}

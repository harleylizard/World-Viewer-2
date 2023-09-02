package dev.corgitaco.worldviewer.client.tile;

import dev.corgitaco.worldviewer.client.StructureIconRenderer;
import dev.corgitaco.worldviewer.util.LongPackingUtil;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Map;

public interface RenderTileContext {


    StructureIconRenderer structureIconRenderer();

    float scale();

    TileCoordinateShiftingManager currentShiftingManager();

    Object2FloatMap<String> opacities();

    BlockPos origin();

    int renderWidth();

    int renderHeight();

    BoundingBox worldViewArea();

    Map<String, ?> data();


    default int xTileRange() {
        return this.currentShiftingManager().blockToTile(getScreenCenterX()) + 2;
    }

    default int zTileRange() {
        return this.currentShiftingManager().blockToTile(getScreenCenterZ()) + 2;
    }

    default double localXFromWorldX(double worldX) {
        return origin().getX() - worldX;
    }

    default double localZFromWorldZ(double worldZ) {
        return origin().getZ() - worldZ;
    }

    default int getScreenCenterX() {
        return (int) ((renderWidth() / 2) / scale());
    }

    default int getScreenCenterZ() {
        return (int) ((renderHeight() / 2) / scale());
    }

    default long getOriginTile() {
        return this.currentShiftingManager().tileKey(origin());
    }

    default int getTileLocalXFromWorldX(int worldX) {
        return LongPackingUtil.getTileX(getOriginTile()) - this.currentShiftingManager().blockToTile(worldX);
    }

    default int getTileLocalZFromWorldZ(int worldZ) {
        return LongPackingUtil.getTileZ(getOriginTile()) - this.currentShiftingManager().blockToTile(worldZ);
    }

}

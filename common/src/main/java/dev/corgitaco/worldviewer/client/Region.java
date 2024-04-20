package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import net.minecraft.client.renderer.MultiBufferSource;

public interface Region extends AutoCloseable {


    void render(MultiBufferSource.BufferSource bufferSource, PoseStack stack);


    default int getRenderX() {
        return getRegionBlockX() >> coordinateShiftManager().scaleShift();
    }

    default int getRenderY() {
        return getRegionBlockZ() >> coordinateShiftManager().scaleShift();
    }

    default int getRegionBlockX() {
        return coordinateShiftManager().getRegionWorldX(regionPos());
    }

    default int getRegionBlockZ() {
        return coordinateShiftManager().getRegionWorldZ(regionPos());
    }

    default int getRegionX() {
        return coordinateShiftManager().getRegionX(regionPos());
    }

    default int getRegionZ() {
        return coordinateShiftManager().getRegionZ(regionPos());
    }

    long regionPos();

    CoordinateShiftManager coordinateShiftManager();
}

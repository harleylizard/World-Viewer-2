package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public interface Region extends AutoCloseable {


    void render(MultiBufferSource.BufferSource bufferSource, PoseStack stack, BoundingBox worldViewArea);

    default void renderRegionImage(VertexConsumer consumer, PoseStack stack, BoundingBox worldViewArea, float opacity) {
        if (worldViewArea.intersects(getRegionBlockX(), getRegionBlockZ(), getRegionBlockX() + this.coordinateShiftManager().getRegionBlockSize(), getRegionBlockZ() + this.coordinateShiftManager().getRegionBlockSize())) {
            int imageSize = this.coordinateShiftManager().getRegionImageSize();
            ClientUtil.blit(consumer, stack, opacity, getRenderX(), getRenderY(), 0, 0, imageSize, imageSize, imageSize, imageSize);
        }
    }


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

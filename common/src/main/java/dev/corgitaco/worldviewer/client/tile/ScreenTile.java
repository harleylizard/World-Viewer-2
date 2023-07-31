package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Map;

public interface ScreenTile {


    int getTileWorldX();

    int getTileWorldZ();

    void renderTile(GuiGraphics guiGraphics);

    Map<String, NativeImage> layers();

    int size();
}

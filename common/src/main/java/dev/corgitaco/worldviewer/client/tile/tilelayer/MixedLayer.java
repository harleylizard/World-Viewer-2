package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;
import org.joml.Vector4f;

import java.util.Map;

public class MixedLayer extends TileLayer {
    public MixedLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet loadedChunks) {
        super(dataTileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen);
    }


    @Override
    public void render(GuiGraphics stack, float opacity, Map<String, TileLayer> tileLayerMap) {
        RenderSystem.enableBlend();

        RenderSystem.setShader(() -> this.screen.renderTileManager.shaderInstance);
        ShaderInstance shaderInstance = this.screen.renderTileManager.shaderInstance;
        shaderInstance.getUniform("Opacity").set(opacity);
        Vector4f color;
        if (this.screen.highlightedBiome != null) {
            int argb = BiomeLayer.FAST_COLORS.getInt(this.screen.highlightedBiome);
            color = new Vector4f(FastColor.ARGB32.red(argb) / 255.0F, FastColor.ARGB32.green(argb) / 255.0F, FastColor.ARGB32.blue(argb) / 255.0F, 1);
        } else {
            color = new Vector4f(0, 0, 0, 0);
        }

        shaderInstance.getUniform("BiomeColor").set(color);

        TileLayer heights = tileLayerMap.get("heights");
        TileLayer biomes = tileLayerMap.get("biomes");
        if (heights != null && biomes != null) {
            renderAndMixImage(stack.pose(), heights.getImage(), biomes.getImage(), this.size, opacity, 1);
        }
    }

    private void renderAndMixImage(PoseStack stack, DynamicTexture texture, DynamicTexture texture2, int size, float opacity, float brightness) {
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.setShaderTexture(1, texture2.getId());

        ClientUtil.blit(stack, 0, 0, 0.0F, 0.0F, size, size, size, size);
    }
}

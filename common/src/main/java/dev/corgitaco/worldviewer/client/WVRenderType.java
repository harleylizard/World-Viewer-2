package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.function.BiFunction;

public class WVRenderType {


    public static final RenderStateShard.TransparencyStateShard DST_COLOR_SRC_ALPHA_TRANSPARENCY = new RenderStateShard.TransparencyStateShard(
            "world_viewer_transparency",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.SRC_ALPHA);

                },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            }
    );

    public static final BiFunction<Integer, RenderStateShard.TransparencyStateShard, RenderType> WORLD_VIEWER_GUI = (id, transparencyStateShard) -> RenderType.create("gui_texture", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_TEX_SHADER).setTextureState(new TextureIDShard(id)).setTransparencyState(transparencyStateShard).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).createCompositeState(false));

    public static class TextureIDShard extends RenderStateShard.EmptyTextureStateShard {

        public TextureIDShard(int id) {
            super(() -> {
                RenderSystem.setShaderTexture(0, id);
            }, () -> {
            });
        }
    }
}

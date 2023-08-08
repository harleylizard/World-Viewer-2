package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.function.IntFunction;

public class WVRenderType {


    public static final IntFunction<RenderType> GUI_TEXTURE = id -> RenderType.create("gui_texture", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_TEX_SHADER).setTextureState(new TextureIDShard(id)).setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).createCompositeState(false));

    public static class TextureIDShard extends RenderStateShard.EmptyTextureStateShard {

        public TextureIDShard(int id) {
            super(() -> {
                RenderSystem.setShaderTexture(0, id);
            }, () -> {
            });
        }
    }
}

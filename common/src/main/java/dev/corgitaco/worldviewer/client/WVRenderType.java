package dev.corgitaco.worldviewer.client;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

public class WVRenderType {

    @Nullable
    public static ShaderInstance rendertypeColorFilter;

    protected static final RenderStateShard.ShaderStateShard COLOR_FILTER_SHADER = new RenderStateShard.ShaderStateShard(() -> rendertypeColorFilter);


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


    public static final VertexFormat POSITION_COLOR_TEX_FILTER = new VertexFormat(
            ImmutableMap.<String, VertexFormatElement>builder()
                    .put("Position", DefaultVertexFormat.ELEMENT_POSITION)
                    .put("Color", DefaultVertexFormat.ELEMENT_COLOR)
                    .put("UV0", DefaultVertexFormat.ELEMENT_UV0)
                    .put("ColorFilter", DefaultVertexFormat.ELEMENT_COLOR)
                    .build()
    );

    public static final BiFunction<Integer, RenderStateShard.TransparencyStateShard, RenderType> COLOR_FILTER_WORLD_VIEWER_GUI = (id, transparencyStateShard) -> RenderType.create("color_filter_world_viewer_gui", POSITION_COLOR_TEX_FILTER, VertexFormat.Mode.QUADS, 256, RenderType.CompositeState.builder().setShaderState(COLOR_FILTER_SHADER).setTextureState(new TextureIDShard(id)).setTransparencyState(transparencyStateShard).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).createCompositeState(false));



    public static class TextureIDShard extends RenderStateShard.EmptyTextureStateShard {

        public TextureIDShard(int id) {
            super(() -> {
                RenderSystem.setShaderTexture(0, id);
            }, () -> {
            });
        }
    }
}

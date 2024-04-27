package dev.corgitaco.worldviewer.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.corgitaco.worldviewer.client.WorldViewerClientConfig;
import dev.corgitaco.worldviewer.client.render.WorldViewerRenderer;
import dev.corgitaco.worldviewer.mixin.RenderTargetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;

public final class WorldViewerGui implements AutoCloseable, WorldViewerRenderer.Access {
    private static final ResourceLocation TEXTURE = new ResourceLocation("worldviewer", "textures/minimap_shape/star.png");

    private static final Framebuffer FRAMEBUFFER = new Framebuffer(854, 480);

    static {
        ReloadableShaders.RELOADABLE_SHADERS.defineShader("minimap_shape", DefaultVertexFormat.POSITION_TEX);
    }

    private final WorldViewerClientConfig.Gui guiConfig;

    @Nullable
    private WorldViewerRenderer worldViewerRenderer;

    public WorldViewerGui(WorldViewerClientConfig.Gui guiConfig) {
        this.guiConfig = guiConfig;
    }

    public WorldViewerGui(WorldViewerClientConfig.Gui guiConfig, WorldViewerRenderer worldViewerRenderer) {
        this.guiConfig = guiConfig;
        this.worldViewerRenderer = worldViewerRenderer;
    }

    public void renderGui(GuiGraphics guiGraphics, float partialTicks) {
        if (Minecraft.getInstance().player != null) {
            if (this.worldViewerRenderer == null) {
                worldViewerRenderer = new WorldViewerRenderer(this.guiConfig.mapSizeX(), this.guiConfig.mapSizeY());
                worldViewerRenderer.init();
            }

            PoseStack poseStack = guiGraphics.pose();

            var renderTarget = (RenderTargetAccessor) Minecraft.getInstance().getMainRenderTarget();

            FRAMEBUFFER.bind(renderTarget.getFrameBufferId());
            glDrawBuffer(GL_COLOR_ATTACHMENT0);

            FRAMEBUFFER.clear();

            /** TODO::
             *  Render Minimap GUI offscreen onto the Framebuffer's color attachment,
             *  then in "drawShape" draw the quad and bind the shader.
             *  Within the fragment shader it multiplies the shape's rgba with the color attachment's rgba
             */
            guiGraphics.drawManaged(() -> {
                var offsetX = 0.0F;
                var offsetY = 0.0F;

                poseStack.pushPose();
                poseStack.translate(offsetX, offsetY, 0);
                worldViewerRenderer.render(guiGraphics.bufferSource(), poseStack, -1, -1, partialTicks);
                poseStack.popPose();
            });
            FRAMEBUFFER.unbind();

            // Draws the quad and binds the shader.
            drawShape(poseStack);
        }
    }

    private void drawShape(PoseStack poseStack) {
        var window = Minecraft.getInstance().getWindow();
        var width = (float) window.getWidth();
        var height = (float) window.getHeight();
        var aspectRatio = width / height;
        var size = 1.0F;
        var projection = new Matrix4f().ortho(-size * aspectRatio, size * aspectRatio, -size, size, 10.0F, -10.0F);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        var previous = RenderSystem.getShader();
        var copy = new Matrix4f(RenderSystem.getProjectionMatrix());

        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderTexture(1, FRAMEBUFFER.getColor());

        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);

        ReloadableShaders.RELOADABLE_SHADERS.setShader("minimap_shape");

        var tesselator = Tesselator.getInstance();
        var builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(builder, -0.5F, -0.5F, 0.0F, 1.0F);
        vertex(builder,  0.5F, -0.5F, 1.0F, 1.0F);
        vertex(builder,  0.5F,  0.5F, 1.0F, 0.0F);
        vertex(builder, -0.5F,  0.5F, 0.0F, 0.0F);
        tesselator.end();

        RenderSystem.setProjectionMatrix(copy, VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.setShader(() -> previous);
    }

    public void tick() {
        if (this.worldViewerRenderer != null) {
            this.worldViewerRenderer.tick();
        }
    }

    @Override
    public void close() {
        if (this.worldViewerRenderer != null) {
            this.worldViewerRenderer.close();
        }
        this.worldViewerRenderer = null;
    }

    @Override
    public WorldViewerRenderer worldViewerRenderer() {
        return this.worldViewerRenderer;
    }

    private static void vertex(BufferBuilder builder, float x, float y, float u, float v) {
        builder.vertex(x, y, 1.0F).uv(u, v).endVertex();
    }
}

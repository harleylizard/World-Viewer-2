package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.corgitaco.worldviewer.mixin.KeyMappingAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class ClientUtil {

    public static boolean isKeyOrMouseButtonDown(Minecraft minecraft, KeyMapping keyMapping) {
        InputConstants.Key key = ((KeyMappingAccess) keyMapping).wv_getKey();
        long window = minecraft.getWindow().getWindow();
        int keyValue = key.getValue();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, keyValue) == 1;
        } else {
            return InputConstants.isKeyDown(window, keyValue);
        }
    }

    public static void blit(PoseStack matrixStack, int x, int y, int blitOffset, float uOffset, float vOffset, int uWidth, int vHeight, int textureHeight, int textureWidth) {
        innerBlit(matrixStack, x, x + uWidth, y, y + vHeight, blitOffset, uWidth, vHeight, uOffset, vOffset, textureHeight, textureWidth);
    }

    public static void blit(PoseStack matrixStack, int x, int y, int width, int height, float uOffset, float vOffset, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        innerBlit(matrixStack, x, x + width, y, y + height, 0, uWidth, vHeight, uOffset, vOffset, textureWidth, textureHeight);
    }

    public static void blit(PoseStack matrixStack, int x, int y, float uOffset, float vOffset, int width, int height, int textureWidth, int textureHeight) {
        blit(matrixStack, x, y, width, height, uOffset, vOffset, width, height, textureWidth, textureHeight);
    }

    private static void innerBlit(PoseStack matrixStack, int x1, int x2, int y1, int y2, int blitOffset, int uWidth, int vHeight, float uOffset, float vOffset, int textureWidth, int textureHeight) {
        innerBlit(matrixStack.last().pose(), x1, x2, y1, y2, blitOffset, (uOffset + 0.0F) / (float) textureWidth, (uOffset + (float) uWidth) / (float) textureWidth, (vOffset + 0.0F) / (float) textureHeight, (vOffset + (float) vHeight) / (float) textureHeight);
    }

    private static void innerBlit(Matrix4f matrix, int x1, int x2, int y1, int y2, int blitOffset, float minU, float maxU, float minV, float maxV) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix, (float) x1, (float) y2, (float) blitOffset).uv(minU, maxV).endVertex();
        bufferBuilder.vertex(matrix, (float) x2, (float) y2, (float) blitOffset).uv(maxU, maxV).endVertex();
        bufferBuilder.vertex(matrix, (float) x2, (float) y1, (float) blitOffset).uv(maxU, minV).endVertex();
        bufferBuilder.vertex(matrix, (float) x1, (float) y1, (float) blitOffset).uv(minU, minV).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }


    public static int getNativeA(int pAbgrColor) {
        return pAbgrColor >> 24 & 255;
    }

    public static int getNativeR(int pAbgrColor) {
        return pAbgrColor >> 0 & 255;
    }

    public static int getNativeG(int pAbgrColor) {
        return pAbgrColor >> 8 & 255;
    }

    public static int getNativeB(int pAbgrColor) {
        return pAbgrColor >> 16 & 255;
    }

    public static int combineNative(int pAlpha, int pBlue, int pGreen, int pRed) {
        return (pAlpha & 255) << 24 | (pBlue & 255) << 16 | (pGreen & 255) << 8 | (pRed & 255) << 0;
    }

}

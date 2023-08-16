package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corgitaco.worldviewer.mixin.KeyMappingAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    public static void drawOutlineWithWidth(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int lineWidth, int color) {
        // Bottom Line
        guiGraphics.fill(x1, y1 + -lineWidth, x2, y1 + lineWidth, color);
        // Top Line
        guiGraphics.fill(x1, y2 + -lineWidth, x2, y2 + lineWidth, color);
        // Left Line
        guiGraphics.fill(x1 + -lineWidth, y1, x1 + lineWidth, y2, color);
        // Right Line
        guiGraphics.fill(x2 + -lineWidth, y1, x2 + lineWidth, y2, color);
    }

    public static void blit(VertexConsumer vertexConsumer, PoseStack matrixStack, float opacity, int x, int y, int blitOffset, float uOffset, float vOffset, int uWidth, int vHeight, int textureHeight, int textureWidth) {
        innerBlit(vertexConsumer, matrixStack, opacity, x, x + uWidth, y, y + vHeight, blitOffset, uWidth, vHeight, uOffset, vOffset, textureHeight, textureWidth);
    }

    public static void blit(VertexConsumer vertexConsumer, PoseStack matrixStack, float opacity, int x, int y, int width, int height, float uOffset, float vOffset, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        innerBlit(vertexConsumer, matrixStack, opacity, x, x + width, y, y + height, 0, uWidth, vHeight, uOffset, vOffset, textureWidth, textureHeight);
    }

    public static void blit(VertexConsumer vertexConsumer, PoseStack matrixStack, float opacity, int x, int y, float uOffset, float vOffset, int width, int height, int textureWidth, int textureHeight) {
        blit(vertexConsumer, matrixStack, opacity, x, y, width, height, uOffset, vOffset, width, height, textureWidth, textureHeight);
    }

    private static void innerBlit(VertexConsumer vertexConsumer, PoseStack matrixStack, float opacity, int x1, int x2, int y1, int y2, int blitOffset, int uWidth, int vHeight, float uOffset, float vOffset, int textureWidth, int textureHeight) {
        innerBlit(vertexConsumer, matrixStack.last().pose(), opacity, x1, x2, y1, y2, blitOffset, (uOffset + 0.0F) / (float) textureWidth, (uOffset + (float) uWidth) / (float) textureWidth, (vOffset + 0.0F) / (float) textureHeight, (vOffset + (float) vHeight) / (float) textureHeight);
    }

    private static void innerBlit(VertexConsumer vertexConsumer, Matrix4f matrix, float opacity, int x1, int x2, int y1, int y2, int blitOffset, float minU, float maxU, float minV, float maxV) {
            vertexConsumer.vertex(matrix, (float) x1, (float) y2, (float) blitOffset).color(1F, 1F, 1F, opacity).uv(minU, maxV).endVertex();
            vertexConsumer.vertex(matrix, (float) x2, (float) y2, (float) blitOffset).color(1F, 1F, 1F, opacity).uv(maxU, maxV).endVertex();
            vertexConsumer.vertex(matrix, (float) x2, (float) y1, (float) blitOffset).color(1F, 1F, 1F, opacity).uv(maxU, minV).endVertex();
            vertexConsumer.vertex(matrix, (float) x1, (float) y1, (float) blitOffset).color(1F, 1F, 1F, opacity).uv(minU, minV).endVertex();
    }
}

package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.FastColor;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

public class WVTexture {

    private final int textureID;
    private final int elementWidth;
    private final int elementHeight;
    private final int xCount;
    private final int zCount;

    public WVTexture(int elementWidth, int elementHeight, int xCount, int zCount) {
        this.elementWidth = elementWidth;
        this.elementHeight = elementHeight;
        this.xCount = xCount;
        this.zCount = zCount;
        if (!RenderSystem.isOnRenderThread()) {
            throw new RuntimeException("Must be created on render thread.");
        }

        int width = elementWidth * xCount;
        int height = elementHeight * zCount;
        textureID = glGenTextures();
        bind();
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4); // 4 for RGBA

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = FastColor.ARGB32.color(255, 255, 255, 255);
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red component
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green component
                buffer.put((byte) (pixel & 0xFF));         // Blue component
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha component
            }
        }
        buffer.flip();

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);



        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
    }

    public void set(int x, int z, ByteBuffer buffer) {
        glTexSubImage2D(GL_TEXTURE_2D, 0,
                x * elementWidth, z * elementHeight, this.elementWidth, this.elementHeight,
                GL_RGBA, GL_UNSIGNED_BYTE,
                buffer
        );
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, this.textureID);
    }

    public void draw() {
        bind();


        glBegin(GL_QUADS);
        glTexCoord2f(0, 0);
        glVertex2f(-0.5f, -0.5f);

        glTexCoord2f(1, 0);
        glVertex2f(0.5f, -0.5f);

        glTexCoord2f(1, 1);
        glVertex2f(0.5f, 0.5f);

        glTexCoord2f(0, 1);
        glVertex2f(-0.5f, 0.5f);
        glEnd();
    }
}

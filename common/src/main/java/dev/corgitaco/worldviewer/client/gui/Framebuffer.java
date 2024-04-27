package dev.corgitaco.worldviewer.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;

import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL30.*;

public final class Framebuffer {
    private final int framebuffer;
    private final int color;

    private int previous;

    public Framebuffer(int width, int height) {
        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);

        color = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, color);

        GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Incomplete framebuffer!!");
        }
        glBindRenderbuffer(GL_FRAMEBUFFER, 0);
    }

    public void bind(int previous) {
        this.previous = previous;
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    }

    public void clear() {
        glClear(GL_COLOR_BUFFER_BIT);
    }

    public int getColor() {
        return color;
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, previous);
    }
}

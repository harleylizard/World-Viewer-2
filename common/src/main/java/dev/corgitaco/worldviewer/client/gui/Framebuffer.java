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

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        var depth = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depth);

        GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, null);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Renderbuffer isn't needed, makes no difference regardless.
        //var buffer = glGenRenderbuffers();
        //glBindRenderbuffer(GL_RENDERBUFFER, buffer);
        //glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, width, height);
        //glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, buffer);

        //glBindRenderbuffer(GL_RENDERBUFFER, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Incomplete framebuffer!!");
        }
        glBindRenderbuffer(GL_FRAMEBUFFER, 0);
    }

    public void bind(int previous) {
        if (!glIsFramebuffer(framebuffer)) {
            throw new RuntimeException("Framebuffer was deleted.");
        }
        this.previous = previous;
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    }

    public void clear() {
        glClearColor(1.0F, 1.0F, 1.0F, 1.0F);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public int getColor() {
        if (!glIsTexture(color)) {
            throw new RuntimeException("Color attachment texture was deleted.");
        }
        return color;
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, previous);
    }
}

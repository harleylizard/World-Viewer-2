package dev.corgitaco.worldviewer.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;

import static org.lwjgl.opengl.GL30.*;

public final class Framebuffer {
    private final int framebuffer;
    private final int texture;

    {
        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);

        var width = 854;
        var height = 480;
        var target = GL_RGB;

        var buffer = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, buffer);
        glRenderbufferStorage(GL_RENDERBUFFER, target, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, buffer);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        texture = glGenTextures();
        RenderSystem.activeTexture(GL_TEXTURE0);
        RenderSystem.bindTexture(texture);
        glTexImage2D(GL_TEXTURE_2D, 0, target, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        glDrawBuffers(GL_COLOR_ATTACHMENT0);
        RenderSystem.bindTexture(0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != glCheckFramebufferStatus(GL_FRAMEBUFFER)) {
            throw new RuntimeException("Incomplete framebuffer");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public int getTexture() {
        return texture;
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    }

    public static void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 1);
    }
}

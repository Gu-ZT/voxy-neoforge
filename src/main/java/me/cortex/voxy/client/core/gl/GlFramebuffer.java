package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;

import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL45C.*;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferDrawBuffers;

public class GlFramebuffer extends TrackedObject {
    public final int id;
    public GlFramebuffer() {
        this.id = glCreateFramebuffers();
    }

    public GlFramebuffer bind(int attachment, GlTexture texture) {
        return this.bind(attachment, texture, 0);
    }

    public GlFramebuffer bind(int attachment, GlTexture texture, int lvl) {
        glNamedFramebufferTexture(this.id, attachment, texture.id, lvl);
        return this;
    }

    public GlFramebuffer bind(int attachment, GlRenderBuffer buffer) {
        glNamedFramebufferRenderbuffer(this.id, attachment, GL_RENDERBUFFER, buffer.id);
        return this;
    }

    public GlFramebuffer setDrawBuffers(int... buffers) {
        glNamedFramebufferDrawBuffers(this.id, buffers);
        return this;
    }

    @Override
    public void free() {
        super.free0();
        glDeleteFramebuffers(this.id);
    }

    public GlFramebuffer verify() {
        int code;
        code = glCheckNamedFramebufferStatus(this.id, GL_FRAMEBUFFER);
        if (code == GL_FRAMEBUFFER_COMPLETE) {
            return this;
        }

        // Some driver stacks can return 0 from glCheckNamedFramebufferStatus on otherwise valid FBOs.
        // Fall back to the bind-to-target check path before failing hard.
        if (code == 0) {
            int oldFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING);
            int fallbackCode;
            glBindFramebuffer(GL_FRAMEBUFFER, this.id);
            fallbackCode = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            glBindFramebuffer(GL_FRAMEBUFFER, oldFramebuffer);
            if (fallbackCode == GL_FRAMEBUFFER_COMPLETE) {
                return this;
            }
            throw new IllegalStateException("Framebuffer incomplete with error code: named=0 fallback=" + fallbackCode);
        }

        throw new IllegalStateException("Framebuffer incomplete with error code: " + code);
    }


    public GlFramebuffer name(String name) {
        return GlDebug.name(name, this);
    }
}

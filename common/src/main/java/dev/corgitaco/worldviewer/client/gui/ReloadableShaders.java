package dev.corgitaco.worldviewer.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public final class ReloadableShaders {
    public static final ReloadableShaders RELOADABLE_SHADERS = new ReloadableShaders();

    private final Set<Pair<String, VertexFormat>> shaders = new HashSet<>();
    private final Map<String, ShaderInstance> map = new HashMap<>();

    private ReloadableShaders() {}

    public ShaderInstance getShaderInstance(String name) {
        return map.get(name);
    }

    public void setShader(String name) {
        RenderSystem.setShader(() -> map.get(name));
    }

    public void defineShader(String name, VertexFormat format) {
        if (!hasPair(name)) {
            shaders.add(Pair.of(name, format));
        }
    }

    public void reloadAll(ResourceProvider resourceProvider, List<Pair<ShaderInstance, Consumer<ShaderInstance>>> list) throws IOException {
        for (var shader : shaders) {
            list.add(createShader(resourceProvider, shader.getFirst(), shader.getSecond()));
        }
    }

    private Pair<ShaderInstance, Consumer<ShaderInstance>> createShader(ResourceProvider resourceProvider, String name, VertexFormat format) throws IOException {
        return Pair.of(new ShaderInstance(resourceProvider, name, format), (shaderInstance) -> map.put(name, shaderInstance));
    }

    private boolean hasPair(String name) {
        for (var shader : shaders) {
            if (shader.getFirst().equals(name)) {
                return true;
            }
        }
        return false;
    }
}

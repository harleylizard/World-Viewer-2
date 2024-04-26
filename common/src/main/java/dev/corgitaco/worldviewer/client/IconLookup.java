package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.common.WorldViewer;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class IconLookup implements AutoCloseable {

    public static final List<ResourceKey<? extends Registry<?>>> REGISTRIES = List.of(Registries.STRUCTURE, Registries.ENTITY_TYPE);


    private final Object2ObjectOpenHashMap<ResourceKey<? extends Registry<?>>, Object2ObjectOpenHashMap<ResourceKey<?>, DynamicTexture>> textures = new Object2ObjectOpenHashMap<>();

    public IconLookup() {
    }

    public void init() {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

        for (ResourceKey<? extends Registry<?>> registry : REGISTRIES) {
            Object2ObjectOpenHashMap<ResourceKey<?>, DynamicTexture> map = textures.computeIfAbsent(registry, key -> new Object2ObjectOpenHashMap<>());


            resourceManager.listResources("worldviewer", location -> location.getPath().startsWith("worldviewer/textures/icon/%s/%s".formatted(registry.location().getNamespace(), registry.location().getPath()))).forEach((location, resource) -> {
                try (InputStream stream = resource.open()) {
                    NativeImage read = NativeImage.read(stream);


                    String[] split = location.getPath().split("/");
                    ResourceLocation lookupKey =  new ResourceLocation(location.getNamespace(), split[split.length - 1].replaceAll(".png", ""));

                    map.computeIfAbsent(ResourceKey.create((ResourceKey) registry, lookupKey), key -> new DynamicTexture(read));

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

    }

    @Override
    public void close() {
        for (Object2ObjectMap.Entry<ResourceKey<? extends Registry<?>>, Object2ObjectOpenHashMap<ResourceKey<?>, DynamicTexture>> entry : this.textures.object2ObjectEntrySet()) {
            entry.getValue().values().forEach(DynamicTexture::close);
        }
    }

    public Object2ObjectOpenHashMap<ResourceKey<? extends Registry<?>>, Object2ObjectOpenHashMap<ResourceKey<?>, DynamicTexture>> getTextures() {
        return textures;
    }
}

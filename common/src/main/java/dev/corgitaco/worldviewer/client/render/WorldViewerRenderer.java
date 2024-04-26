package dev.corgitaco.worldviewer.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.IconLookup;
import dev.corgitaco.worldviewer.client.RegionGrid;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.RenderTileContext;
import dev.corgitaco.worldviewer.client.tile.TileLayerRenderTileManager;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.platform.ModPlatform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class WorldViewerRenderer implements RenderTileContext, AutoCloseable {

    private final int height;
    private final int width;
    private BoundingBox tileArea;
    private BoundingBox worldViewArea;

    private final CoordinateShiftManager coordinateShiftManager = new CoordinateShiftManager(10, 0);
    private final BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();
    private final Map<UUID, ResourceLocation> cachedSkins = new HashMap<>();

    private TileLayerRenderTileManager tileLayerRenderTileManager;

    private IconLookup iconLookup;

    private final Long2ObjectLinkedOpenHashMap<RegionGrid> grid = new Long2ObjectLinkedOpenHashMap<>();

    public WorldViewerRenderer(int height, int width) {
        this.height = height;
        this.width = width;
    }

    public void init() {
        this.origin.set(Minecraft.getInstance().player.blockPosition());
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.getLevel(Minecraft.getInstance().level.dimension());

        updateWorldViewArea();
        DataTileManager dataTileManager = new DataTileManager(ModPlatform.INSTANCE.configPath().resolve(String.valueOf(level.getSeed())), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(), level, level.getSeed());

        this.iconLookup = new IconLookup();
        this.iconLookup.init();

        this.tileLayerRenderTileManager = new TileLayerRenderTileManager(this.origin, this, dataTileManager);
        this.tileLayerRenderTileManager.init();
    }

    private void updateWorldViewArea() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.getLevel(Minecraft.getInstance().level.dimension());
        int scaleXRadius = getScreenCenterX() << this.coordinateShiftManager.scaleShift();
        int scaleZRadius = getScreenCenterZ() << this.coordinateShiftManager.scaleShift();
        this.worldViewArea = BoundingBox.fromCorners(
                new Vec3i(
                        (int) Math.max(level.getWorldBorder().getMinX(), this.origin.getX() - scaleXRadius),
                        level.getMinBuildHeight(),
                        (int) Math.max(level.getWorldBorder().getMinZ(), this.origin.getZ() - scaleZRadius)
                ),
                new Vec3i(
                        (int) Math.min(level.getWorldBorder().getMaxX(), this.origin.getX() + scaleXRadius),
                        level.getMaxBuildHeight(),
                        (int) Math.min(level.getWorldBorder().getMaxZ(), this.origin.getZ() + scaleZRadius)
                )
        );

        this.tileArea = BoundingBox.fromCorners(
                new Vec3i(
                        (int) Math.max(level.getWorldBorder().getMinX(), this.origin.getX() - this.coordinateShiftManager.getBlockCoordFromTileCoord(getScreenXTileRange()) - 1),
                        level.getMinBuildHeight(),
                        (int) Math.max(level.getWorldBorder().getMinZ(), this.origin.getZ() - this.coordinateShiftManager.getBlockCoordFromTileCoord(getScreenZTileRange()) - 1)
                ),
                new Vec3i(
                        (int) Math.min(level.getWorldBorder().getMaxX(), this.origin.getX() + this.coordinateShiftManager.getBlockCoordFromTileCoord(getScreenXTileRange()) + 1),
                        level.getMaxBuildHeight(),
                        (int) Math.min(level.getWorldBorder().getMaxZ(), this.origin.getZ() + this.coordinateShiftManager.getBlockCoordFromTileCoord(getScreenZTileRange()) + 1)
                )
        );

        for (int regionX = this.coordinateShiftManager.getRegionCoordFromBlockCoord(this.tileArea.minX()); regionX <= this.coordinateShiftManager.getRegionCoordFromBlockCoord(this.tileArea.maxX()); regionX++) {
            for (int regionZ = this.coordinateShiftManager.getRegionCoordFromBlockCoord(this.tileArea.minZ()); regionZ <= this.coordinateShiftManager.getRegionCoordFromBlockCoord(this.tileArea.maxZ()); regionZ++) {
                this.grid.computeIfAbsent(ChunkPos.asLong(regionX, regionZ), key -> new RegionGrid(key, this.coordinateShiftManager, 3));
            }
        }
    }

    public void tick() {
        this.origin.set(Minecraft.getInstance().player.blockPosition());
        updateWorldViewArea();
        this.tileLayerRenderTileManager.cull();
        this.tileLayerRenderTileManager.fillRegions();

        this.tileLayerRenderTileManager.tick();
    }

    public void loadChunk(int chunkX, int chunkZ) {
        this.tileLayerRenderTileManager.chunkLoad(chunkX, chunkZ);
    }

    public void unloadChunk(int chunkX, int chunkZ) {
    }

    public void render(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        poseStack.pushPose();

        poseStack.translate(getScreenCenterX(), getScreenCenterZ(), 0);
        poseStack.translate(getOriginRenderOffsetX(), getOriginRenderOffsetZ(), 0);

        this.tileLayerRenderTileManager.render(bufferSource, poseStack, mouseX, mouseY, partialTicks);
        this.tileLayerRenderTileManager.renderLast(bufferSource, poseStack, mouseX, mouseY, partialTicks);

        for (Long2ObjectMap.Entry<RegionGrid> renderGridEntry : this.grid.long2ObjectEntrySet()) {
            renderGridEntry.getValue().renderCoords(bufferSource, poseStack, this.worldViewArea);
        }

        this.tileLayerRenderTileManager.renderSprites(bufferSource, poseStack);

        drawEntities(bufferSource, poseStack);
        for (Long2ObjectMap.Entry<RegionGrid> renderGridEntry : this.grid.long2ObjectEntrySet()) {
            renderGridEntry.getValue().render(bufferSource, poseStack, this.worldViewArea);
        }

        drawPlayerHead(bufferSource, poseStack, Minecraft.getInstance().player);

        poseStack.popPose();
    }

    private void drawEntities(MultiBufferSource.BufferSource bufferSource, PoseStack stack) {
        Int2ObjectMap<Pair<DynamicTexture, LongList>> entitiesToRender = new Int2ObjectOpenHashMap<>();

        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (this.worldViewArea.intersects(entity.getBlockX(), entity.getBlockZ(), entity.getBlockX(), entity.getBlockZ())) {
                if (entity instanceof Player player) {
                    drawPlayerHead(bufferSource, stack, player);
                } else {
                    Object2ObjectOpenHashMap<ResourceKey<?>, DynamicTexture> entityTextures = this.iconLookup.getTextures().get(Registries.ENTITY_TYPE);
                    ResourceKey<EntityType<?>> entityTypeResourceKey = ResourceKey.create(Registries.ENTITY_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));

                    DynamicTexture texture = entityTextures.get(entityTypeResourceKey);
                    if (texture != null) {
                        Pair<DynamicTexture, LongList> pair = entitiesToRender.get(texture.getId());
                        if (pair == null) {
                            pair = Pair.of(texture, new LongArrayList());
                            entitiesToRender.put(texture.getId(), pair);
                        }

                        pair.getSecond().add(ChunkPos.asLong(entity.getBlockX(), entity.getBlockZ()));
                    }
                }
            }
        }

        for (Int2ObjectMap.Entry<Pair<DynamicTexture, LongList>> pairEntry : entitiesToRender.int2ObjectEntrySet()) {
            VertexConsumer buffer = bufferSource.getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(pairEntry.getIntKey(), RenderType.NO_TRANSPARENCY));
            NativeImage pixels = pairEntry.getValue().getFirst().getPixels();

            LongList positions = pairEntry.getValue().getSecond();
            for (long packedBlockPos : positions) {
                int worldX = ChunkPos.getX(packedBlockPos) >> coordinateShiftManager.scaleShift();
                int worldZ = ChunkPos.getZ(packedBlockPos) >> coordinateShiftManager.scaleShift();

                int renderX = worldX - (pixels.getWidth() / 2);
                int renderY = worldZ - (pixels.getHeight() / 2);
                ClientUtil.blit(buffer,
                        stack,
                        1,
                        renderX,
                        renderY,
                        0F,
                        0F,
                        pixels.getWidth(),
                        pixels.getHeight(),
                        pixels.getWidth(),
                        pixels.getHeight()
                );
            }
        }
    }


    private void drawPlayerHead(MultiBufferSource.BufferSource bufferSource, PoseStack stack, Player player) {
        GameProfile gameProfile = player.getGameProfile();

        ResourceLocation skinLocation = cachedSkins.computeIfAbsent(gameProfile.getId(), uuid ->
                new PlayerInfo(gameProfile, false).getSkinLocation()
        );

        boolean entityUpsideDown = LivingEntityRenderer.isEntityUpsideDown(player);
        int yOffset = 8 + (entityUpsideDown ? 8 : 0);
        int yHeight = 8 * (entityUpsideDown ? -1 : 1);

        int size = 16;

        int renderX = (player.getBlockX() >> this.coordinateShiftManager.scaleShift()) - (size / 2);
        int renderZ = (player.getBlockZ() >> this.coordinateShiftManager.scaleShift()) - (size / 2);

        RenderType renderType = WVRenderType.WORLD_VIEWER_GUI.apply(Minecraft.getInstance().getTextureManager().getTexture(skinLocation).getId(), RenderType.NO_TRANSPARENCY);
        ClientUtil.blit(bufferSource.getBuffer(renderType), stack, 1, renderX, renderZ, size, size, 8.0F, (float) yOffset, 8, yHeight, 64, 64);

    }


    public List<Component> getToolTip(int mouseX, int mouseY) {
        BlockPos mouseWorldVec3 = getMouseWorldPos(mouseX, mouseY);

        List<Component> toolTip = new ArrayList<>();

        toolTip.add(Component.literal("x=%s, z=%s".formatted(mouseWorldVec3.getX(), mouseWorldVec3.getZ())).withStyle(ChatFormatting.BOLD));
        toolTip.add(Component.literal("").withStyle(ChatFormatting.BOLD));

        toolTip.addAll(this.tileLayerRenderTileManager.toolTip(mouseX, mouseY, getMouseWorldPos(mouseX, mouseY)));
        return toolTip;
    }


    @NotNull
    private BlockPos getMouseWorldPos(double mouseX, double mouseY) {
        int mouseWorldX = this.origin.getX() + (((int) mouseX - getScreenCenterX()) << this.coordinateShiftManager.scaleShift());
        int mouseWorldZ = this.origin.getZ() + (((int) mouseY - getScreenCenterZ()) << this.coordinateShiftManager.scaleShift());
        return new BlockPos(mouseWorldX, 0, mouseWorldZ);
    }

    @Override
    public void close() {
        this.tileLayerRenderTileManager.close();
        for (Long2ObjectMap.Entry<RegionGrid> regionGridEntry : this.grid.long2ObjectEntrySet()) {
            regionGridEntry.getValue().close();
        }
    }

    @Override
    public CoordinateShiftManager coordinateShiftManager() {
        return this.coordinateShiftManager;
    }

    @Override
    public BoundingBox tileArea() {
        return tileArea;
    }

    @Override
    public BoundingBox worldViewArea() {
        return this.worldViewArea;
    }

    @Override
    public IconLookup iconLookup() {
        return this.iconLookup;
    }

    @Override
    public int xTileRange() {
        return getScreenXTileRange();
    }

    @Override
    public int zTileRange() {
        return getScreenZTileRange();
    }

    private int getScreenXTileRange() {
        return this.coordinateShiftManager.getTileCoordFromBlockCoord(scaledScreenCenterX()) + 2;
    }

    private int getScreenZTileRange() {
        return this.coordinateShiftManager.getTileCoordFromBlockCoord(scaledScreenCenterZ()) + 2;
    }

    private int scaledScreenCenterX() {
        return getScreenCenterX() << this.coordinateShiftManager.scaleShift();
    }

    private int scaledScreenCenterZ() {
        return getScreenCenterZ() << this.coordinateShiftManager.scaleShift();
    }

    public int getScreenCenterX() {
        return ((width / 2));
    }

    public int getScreenCenterZ() {
        return ((height / 2));
    }

    private int getOriginRenderOffsetX() {
        return getOriginRenderOffset(origin.getX());
    }

    private int getOriginRenderOffsetZ() {
        return getOriginRenderOffset(origin.getZ());
    }

    private int getOriginRenderOffset(int origin) {
        return -(origin >> this.coordinateShiftManager.scaleShift());
    }

    public interface Access {

        WorldViewerRenderer worldViewerRenderer();
    }
}

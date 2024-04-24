package dev.corgitaco.worldviewer.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.RegionGrid;
import dev.corgitaco.worldviewer.client.StructureIconRenderer;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.RenderTileContext;
import dev.corgitaco.worldviewer.client.tile.TileLayerRenderTileManager;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.platform.ModPlatform;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class WorldViewerRenderer implements RenderTileContext, AutoCloseable {

    private final int height;
    private final int width;
    private BoundingBox tileArea;
    private BoundingBox worldViewArea;

    private final CoordinateShiftManager coordinateShiftManager = new CoordinateShiftManager(10, 1);
    private final BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();

    private TileLayerRenderTileManager tileLayerRenderTileManager;

    private StructureIconRenderer structureIconRenderer;

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




        this.structureIconRenderer = new StructureIconRenderer(level);
        this.structureIconRenderer.init();

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

    public void render(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        poseStack.pushPose();

        poseStack.translate(getScreenCenterX(), getScreenCenterZ(), 0);
        poseStack.translate(getOriginRenderOffsetX(), getOriginRenderOffsetZ(), 0);

        this.tileLayerRenderTileManager.render(bufferSource, poseStack, mouseX, mouseY, partialTicks);
        this.tileLayerRenderTileManager.renderLast(bufferSource, poseStack, mouseX, mouseY, partialTicks);

        for (Long2ObjectMap.Entry<RegionGrid> renderGridEntry : this.grid.long2ObjectEntrySet()) {
            renderGridEntry.getValue().render(bufferSource, poseStack, this.worldViewArea);
        }

        for (Long2ObjectMap.Entry<RegionGrid> renderGridEntry : this.grid.long2ObjectEntrySet()) {
            renderGridEntry.getValue().renderCoords(bufferSource, poseStack, this.worldViewArea);
        }

        this.tileLayerRenderTileManager.renderSprites(bufferSource, poseStack);

        poseStack.popPose();
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
    public StructureIconRenderer structureRenderer() {
        return this.structureIconRenderer;
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

    public double localXFromWorldX(double worldX) {
        return origin.getX() - worldX;
    }

    public double localZFromWorldZ(double worldZ) {
        return origin.getZ() + worldZ;
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
}

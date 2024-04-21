package dev.corgitaco.worldviewer.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.RegionGrid;
import dev.corgitaco.worldviewer.client.StructureIconRenderer;
import dev.corgitaco.worldviewer.client.render.ColorUtils;
import dev.corgitaco.worldviewer.client.tile.RenderTileContext;
import dev.corgitaco.worldviewer.client.tile.TileLayerRenderTileManager;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.platform.ModPlatform;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WorldScreenV3 extends Screen implements RenderTileContext {

    private BoundingBox worldViewArea;

    private final CoordinateShiftManager coordinateShiftManager = new CoordinateShiftManager(10, 1);
    private final BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();

    private TileLayerRenderTileManager tileLayerRenderTileManager;

    private StructureIconRenderer structureIconRenderer;

    private final Long2ObjectLinkedOpenHashMap<RegionGrid> grid = new Long2ObjectLinkedOpenHashMap<>();


    public WorldScreenV3(Component component) {
        super(component);
    }

    @Override
    protected void init() {
        super.init();
        this.origin.set(Minecraft.getInstance().player.blockPosition());
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.getLevel(Minecraft.getInstance().level.dimension());


        this.worldViewArea = BoundingBox.fromCorners(
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
        DataTileManager dataTileManager = new DataTileManager(ModPlatform.INSTANCE.configPath().resolve(String.valueOf(level.getSeed())), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(), level, level.getSeed());


        for (int regionX = this.coordinateShiftManager.getRegionCoordFromBlockCoord(this.worldViewArea.minX()); regionX <= this.coordinateShiftManager.getRegionCoordFromBlockCoord(this.worldViewArea.maxX()); regionX++) {
            for (int regionZ = this.coordinateShiftManager.getRegionCoordFromBlockCoord(this.worldViewArea.minZ()); regionZ <= this.coordinateShiftManager.getRegionCoordFromBlockCoord(this.worldViewArea.maxZ()); regionZ++) {
                this.grid.computeIfAbsent(ChunkPos.asLong(regionX, regionZ), key -> new RegionGrid(key, this.coordinateShiftManager, 3));
            }
        }

        this.structureIconRenderer = new StructureIconRenderer(level);
        this.structureIconRenderer.init();

        this.tileLayerRenderTileManager = new TileLayerRenderTileManager(this.origin, this, dataTileManager);
        this.tileLayerRenderTileManager.init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        guiGraphics.fill(0, 0, width, height, ColorUtils.ARGB.packARGB(255, 0, 0, 0));

        poseStack.translate(getScreenCenterX(), getScreenCenterZ(), 0);
        poseStack.translate(getOriginRenderOffsetX(), getOriginRenderOffsetZ(), 0);
        MultiBufferSource.BufferSource bufferSource = guiGraphics.bufferSource();
        this.tileLayerRenderTileManager.render(bufferSource, poseStack, mouseX, mouseY, partialTicks);
        this.tileLayerRenderTileManager.renderLast(bufferSource, poseStack, mouseX, mouseY, partialTicks);
        for (Long2ObjectMap.Entry<RegionGrid> renderGridEntry : this.grid.long2ObjectEntrySet()) {
            renderGridEntry.getValue().render(bufferSource, poseStack);
        }

        for (Long2ObjectMap.Entry<RegionGrid> renderGridEntry : this.grid.long2ObjectEntrySet()) {
            renderGridEntry.getValue().renderCoords(bufferSource, poseStack, this.worldViewArea);
        }

        this.tileLayerRenderTileManager.renderSprites(bufferSource, poseStack, mouseX, mouseY, partialTicks);

        poseStack.popPose();

        renderToolTip(guiGraphics, mouseX, mouseY);

        guiGraphics.drawString(Minecraft.getInstance().font, minecraft.fpsString, 0, 0, FastColor.ARGB32.color(255, 255, 255, 255));
    }

    private void renderToolTip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        BlockPos mouseWorldVec3 = getMouseWorldPos(mouseX, mouseY);

        List<Component> toolTip = new ArrayList<>();

        toolTip.add(Component.literal("x=%s, z=%s".formatted(mouseWorldVec3.getX(), mouseWorldVec3.getZ())).withStyle(ChatFormatting.BOLD));
        toolTip.add(Component.literal("").withStyle(ChatFormatting.BOLD));

        toolTip.addAll(this.tileLayerRenderTileManager.toolTip(mouseX, mouseY, getMouseWorldPos(mouseX, mouseY)));
        guiGraphics.renderTooltip(Minecraft.getInstance().font, toolTip, Optional.empty(), mouseX, mouseY);
    }

    @NotNull
    private BlockPos getMouseWorldPos(double mouseX, double mouseY) {
        int mouseWorldX = this.origin.getX() + (((int) mouseX - getScreenCenterX()) << this.coordinateShiftManager.scaleShift());
        int mouseWorldZ = this.origin.getZ() + (((int) mouseY - getScreenCenterZ()) << this.coordinateShiftManager.scaleShift());
        return new BlockPos(mouseWorldX, 0, mouseWorldZ);
    }

    @Override
    public void tick() {
        super.tick();
        this.tileLayerRenderTileManager.tick();
    }

    private int getScreenXTileRange() {
        return (this.coordinateShiftManager.getTileCoordFromBlockCoord(scaledScreenCenterX()) + 2);
    }

    private int getScreenZTileRange() {
        return (this.coordinateShiftManager.getTileCoordFromBlockCoord(scaledScreenCenterZ()) + 2);
    }

    private int scaledScreenCenterX() {
        return getScreenCenterX() << this.coordinateShiftManager.scaleShift();
    }

    private int scaledScreenCenterZ() {
        return getScreenCenterZ() << this.coordinateShiftManager.scaleShift();
    }

    @Override
    public void onClose() {
        this.tileLayerRenderTileManager.close();
        super.onClose();
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


    @Override
    public CoordinateShiftManager coordinateShiftManager() {
        return this.coordinateShiftManager;
    }

    @Override
    public BoundingBox worldViewArea() {
        return worldViewArea;
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
}

package dev.corgitaco.worldviewer.client.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.StructureIconRenderer;
import dev.corgitaco.worldviewer.client.tile.RenderTileManager;
import dev.corgitaco.worldviewer.client.tile.SingleScreenTileLayer;
import dev.corgitaco.worldviewer.client.tile.TileCoordinateShiftingManager;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static dev.corgitaco.worldviewer.util.LongPackingUtil.getTileX;
import static dev.corgitaco.worldviewer.util.LongPackingUtil.getTileZ;


public class WorldScreenv2 extends Screen {

    public TileCoordinateShiftingManager shiftingManager = new TileCoordinateShiftingManager(7);


    public int tileSize = shiftingManager.tileToBlock(1);

    public int sampleResolution = tileSize >> 6;

    public final BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();

    public float scale = 0.5F;
    public ServerLevel level;

    public BoundingBox worldViewArea;

    private int coolDown;

    public StructureIconRenderer structureIconRenderer;

    private static final Map<UUID, ResourceLocation> SKINS = new HashMap<>();

    public RenderTileManager renderTileManager;

    private WidgetList opacityList;
    private WidgetList highlightBiomes;


    @Nullable
    public ResourceKey<Biome> highlightedBiome = null;

    public final Map<String, Float> opacities = new HashMap<>();

    private long lastClickTime;


    public WorldScreenv2(Component title) {
        super(title);
    }


    @Override
    protected void init() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.level = server.getLevel(Minecraft.getInstance().level.dimension());
        this.origin.set(Minecraft.getInstance().player.blockPosition());
        setWorldArea();

        this.renderTileManager = new RenderTileManager(this, level, origin);
        this.structureIconRenderer = new StructureIconRenderer(this.level);
        super.init();
    }

    @Override
    public void tick() {
        if (this.coolDown == 0) {
            this.renderTileManager.blockGeneration = false;
        }
        this.renderTileManager.tick();

        coolDown--;
        super.tick();
    }

    @Override
    public void onClose() {
        this.renderTileManager.close();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack stack = guiGraphics.pose();
        stack.pushPose();
        stack.scale(scale, scale, 0);
        guiGraphics.fill(0, 0, (int) (width / scale), (int) (height / scale), FastColor.ARGB32.color(255, 0, 0, 0));

        this.renderTileManager.render(guiGraphics, mouseX, mouseY, partialTicks, this);

        drawGrid(guiGraphics);

        drawPlayers(guiGraphics);

        stack.popPose();

        guiGraphics.drawString(Minecraft.getInstance().font, minecraft.fpsString, 0, 0, FastColor.ARGB32.color(255, 255, 255, 255));

        if (!overWidget(mouseX, mouseY)) {
            renderToolTip(guiGraphics, mouseX, mouseY, partialTicks);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void renderToolTip(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        BlockPos mouseWorldPos = getMouseWorldPos(mouseX, mouseY);

        long mouseTileKey = shiftingManager.tileKey(mouseWorldPos);

        List<Component> toolTip = new ArrayList<>();

        int mouseWorldX = mouseWorldPos.getX();
        int mouseWorldZ = mouseWorldPos.getZ();

        toolTip.add(Component.literal("x=%s, z=%s".formatted(mouseWorldX, mouseWorldZ)).withStyle(ChatFormatting.BOLD));
        toolTip.add(Component.literal("").withStyle(ChatFormatting.BOLD));


        for (int i = 0; i < this.renderTileManager.loaded.length; i++) {
            Long2ObjectOpenHashMap<SingleScreenTileLayer> layerLong2ObjectOpenHashMap = this.renderTileManager.loaded[i];
            if (layerLong2ObjectOpenHashMap != null) {
                SingleScreenTileLayer singleScreenTileLayer = layerLong2ObjectOpenHashMap.get(mouseTileKey);

                if (singleScreenTileLayer != null) {
                    int mouseTileLocalX = (mouseWorldPos.getX() - singleScreenTileLayer.getMinTileWorldX());
                    int mouseTileLocalY = (mouseWorldPos.getZ() - singleScreenTileLayer.getMinTileWorldZ());
                    List<Component> components = singleScreenTileLayer.toolTip(mouseX, mouseY, mouseWorldPos.getX(), mouseWorldPos.getZ(), mouseTileLocalX, mouseTileLocalY);

                    if (!components.isEmpty()) {
                        toolTip.add(Component.literal("\"" + TileLayer.FACTORY_REGISTRY.get(i).first() + "\" " + " layer").withStyle(Style.EMPTY.withBold(true).withUnderlined(true)));
                        toolTip.addAll(components);
                        toolTip.add(Component.literal("Sample Resolution: %s blocks".formatted(singleScreenTileLayer.getSampleRes())));
                        toolTip.add(Component.literal("Tile size: %s blocks ".formatted(singleScreenTileLayer.getSize())));
                    }
                }
            }
        }

        guiGraphics.renderTooltip(Minecraft.getInstance().font, toolTip, Optional.empty(), mouseX, mouseY);
    }

    @NotNull
    private Vec3 getMouseWorldVec3(double mouseX, double mouseY) {
        double scaledMouseX = mouseX / scale;
        double scaledMouseZ = mouseY / scale;

        double mouseWorldX = this.origin.getX() - (scaledMouseX - getScreenCenterX());
        double mouseWorldZ = this.origin.getZ() - (scaledMouseZ - getScreenCenterZ());

        return new Vec3(mouseWorldX, 0, mouseWorldZ);
    }

    private BlockPos getMouseWorldPos(double mouseX, double mouseY) {
        Vec3 mouseWorldVec3 = getMouseWorldVec3(mouseX, mouseY);
        return new BlockPos((int) mouseWorldVec3.x, (int) mouseWorldVec3.y, (int) mouseWorldVec3.z);
    }

    private void drawGrid(GuiGraphics guiGraphics) {
        drawGrid(guiGraphics, new TileCoordinateShiftingManager(this.shiftingManager.getShift() + 2));
    }

    private void drawGrid(GuiGraphics guiGraphics, TileCoordinateShiftingManager tileCoordinateShiftingManager) {
        PoseStack poseStack = guiGraphics.pose();
        int gridColor = FastColor.ARGB32.color(100, 255, 255, 255);
        long originTile = this.shiftingManager.tileKey(this.origin);
        int lineWidth = (int) Math.ceil(0.75 / scale);

        int tileMinX = tileCoordinateShiftingManager.blockToTile(shiftingManager.tileToBlock(getTileX(originTile) - getXTileRange()));
        int tileMaxX = tileCoordinateShiftingManager.blockToTile(shiftingManager.tileToBlock(getTileX(originTile) + getXTileRange()));

        for (int tileX = tileMinX; tileX <= tileMaxX; tileX++) {
            int linePos = (int) (getScreenCenterX() + getLocalXFromWorldX(tileCoordinateShiftingManager.tileToBlock(tileX)));
            guiGraphics.fill(linePos - lineWidth, 0, linePos + lineWidth, (int) (height / scale), gridColor);
        }

        int tileMinZ = tileCoordinateShiftingManager.blockToTile(this.shiftingManager.tileToBlock(getTileZ(originTile) - getZTileRange()));
        int tileMaxZ = tileCoordinateShiftingManager.blockToTile(this.shiftingManager.tileToBlock(getTileZ(originTile) + getZTileRange()));

        for (int tileZ = tileMinZ; tileZ <= tileMaxZ; tileZ++) {
            int linePos = (int) (getScreenCenterZ() + getLocalZFromWorldZ(tileCoordinateShiftingManager.tileToBlock(tileZ)));
            guiGraphics.fill(0, linePos - lineWidth, (int) (width / scale), linePos + lineWidth, gridColor);
        }

        for (int tileX = tileMinX; tileX <= tileMaxX; tileX++) {
            for (int tileZ = tileMinZ; tileZ <= tileMaxZ; tileZ++) {
                int worldX = tileCoordinateShiftingManager.tileToBlock(tileX);
                int worldZ = tileCoordinateShiftingManager.tileToBlock(tileZ);

                int xScreenPos = (int) (getScreenCenterX() + getLocalXFromWorldX(worldX));
                int zScreenPos = (int) (getScreenCenterZ() + getLocalZFromWorldZ(worldZ));

                String formatted = "x%s,z%s".formatted(worldX, worldZ);
                MutableComponent component = Component.literal(formatted).withStyle(ChatFormatting.BOLD);

                int textWidth = Minecraft.getInstance().font.width(component);
                float scale = (1F / this.scale) * 0.9F;

                float fontRenderX = xScreenPos - ((textWidth / 2F) * scale);
                float fontRenderZ = zScreenPos - (Minecraft.getInstance().font.lineHeight * scale);

                poseStack.pushPose();
                poseStack.translate(fontRenderX, fontRenderZ, 0);
                poseStack.scale(scale, scale, scale);
                guiGraphics.drawString(Minecraft.getInstance().font, component, 0, 0, FastColor.ARGB32.color(255, 255, 255, 255));
                poseStack.popPose();
            }
        }
    }

    private void drawPlayers(GuiGraphics guiGraphics) {
        for (ServerPlayer player : this.level.players()) {

            if (!this.worldViewArea.intersects(player.getBlockX(), player.getBlockZ(), player.getBlockX(), player.getBlockZ())) {
                continue;
            }
            GameProfile gameProfile = player.getGameProfile();

            ResourceLocation skinLocation = SKINS.computeIfAbsent(gameProfile.getId(), uuid ->
                    new PlayerInfo(gameProfile, false).getSkinLocation()
            );


            boolean entityUpsideDown = LivingEntityRenderer.isEntityUpsideDown(player);
            int yOffset = 8 + (entityUpsideDown ? 8 : 0);
            int yHeight = 8 * (entityUpsideDown ? -1 : 1);

            int size = (int) (8 / (scale)) * 3;

            double localXFromWorldX = getLocalXFromWorldX(player.getX());

            double localZFromWorldZ = getLocalZFromWorldZ(player.getZ());

            int renderX = (int) ((getScreenCenterX() + localXFromWorldX) - (size / 2F));
            int renderZ = (int) ((getScreenCenterZ() + localZFromWorldZ) - (size / 2F));

            guiGraphics.blit(skinLocation, renderX, renderZ, size, size, 8.0F, (float) yOffset, 8, yHeight, 64, 64);
        }
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
            if (!overWidget(mouseX, mouseY)) {
                this.origin.move((int) (dragX / scale), 0, (int) (dragY / scale));
                cull();
                this.coolDown = 10;
                this.renderTileManager.blockGeneration = true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseZ, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
            Vec3 mouseWorldVec = getMouseWorldVec3(mouseX, mouseZ);

            BlockPos mouseWorldPos = new BlockPos((int) mouseWorldVec.x(), (int) mouseWorldVec.y(), (int) mouseWorldVec.z());
            LocalPlayer localPlayer = this.minecraft.player;
            if (System.currentTimeMillis() - lastClickTime < 500) {
                UUID uuid = localPlayer.getUUID();
                localPlayer.displayClientMessage(Component.literal("Preparing to teleport ").withStyle(ChatFormatting.YELLOW).append(localPlayer.getDisplayName()).append(" to ").append(Component.literal("x=%.1f, z=%.1f".formatted(mouseWorldVec.x, mouseWorldVec.z)).withStyle(ChatFormatting.AQUA)), false);
                this.level.getServer().submit(() -> {
                    Vec3 tpPos = mouseWorldVec.add(0, this.level.getChunk(mouseWorldPos).getHeight(Heightmap.Types.WORLD_SURFACE, (int) mouseWorldVec.x, (int) mouseWorldVec.z) + 20, 0);
                    Player serverPlayer = this.level.getPlayerByUUID(uuid);
                    if (serverPlayer != null) {
                        serverPlayer.teleportTo(mouseWorldVec.x, tpPos.y, mouseWorldVec.z);
                        serverPlayer.displayClientMessage(Component.literal("Teleported ").withStyle(ChatFormatting.GREEN).append(serverPlayer.getDisplayName()).append(" to ").append(Component.literal("%.1f, %.1f, %.1f".formatted(mouseWorldVec.x, tpPos.y, mouseWorldVec.z)).withStyle(ChatFormatting.AQUA)), false);
                    }
                });

                this.origin.set(mouseWorldVec.x, 0, mouseWorldVec.z);
            }

            Minecraft.getInstance().keyboardHandler.setClipboard(String.format(Locale.ROOT, "/execute in %s run tp @s %.2f ~ %.2f %.2f %.2f", this.level.dimension().location(), mouseWorldVec.x, mouseWorldVec.z, localPlayer.getYRot(), localPlayer.getXRot()));

            lastClickTime = System.currentTimeMillis();
        }
        return super.mouseClicked(mouseX, mouseZ, button);
    }

    private boolean overWidget(double mouseX, double mouseY) {
        boolean overWidget = false;
        for (GuiEventListener child : this.children()) {
            if (child.isMouseOver(mouseX, mouseY)) {
                overWidget = true;
                break;
            }
        }
        return overWidget;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!overWidget(mouseX, mouseY)) {
            if (ClientUtil.isKeyOrMouseButtonDown(this.minecraft, this.minecraft.options.keyShift)) {
                if (!this.level.isOutsideBuildHeight((int) (this.origin.getY() + delta))) {
                    this.origin.move(0, (int) delta, 0);
                }
            } else {
                int prevShift = this.shiftingManager.getShift();
                TileCoordinateShiftingManager newShiftingManager = new TileCoordinateShiftingManager((int) Mth.clamp(prevShift - delta, 6, 15));
                if (prevShift != newShiftingManager.getShift()) {
                    this.shiftingManager = newShiftingManager;
                    tileSize = shiftingManager.tileToBlock(1);
                    sampleResolution = Math.max(1, tileSize >> 6);

                    if (delta > 0) {
                        this.scale = (float) (this.scale + (delta * (this.scale)));
                    } else {
                        this.scale = (float) (this.scale + (delta * (this.scale * 0.5)));

                    }
                    this.coolDown = 100;
                    this.renderTileManager.blockGeneration = true;
                    this.renderTileManager.onScroll();
                    cull();
                }
            }
        }
        return true;
    }

    private void cull() {
        setWorldArea();
        if (this.renderTileManager != null) {
            this.renderTileManager.cull(this);
        }
    }


    private void setWorldArea() {
        int xRange = getXTileRange();
        int zRange = getZTileRange();
        this.worldViewArea = BoundingBox.fromCorners(
                new Vec3i(
                        (int) Math.max(this.level.getWorldBorder().getMinX(), this.origin.getX() - shiftingManager.tileToBlock(xRange) - 1),
                        level.getMinBuildHeight(),
                        (int) Math.max(this.level.getWorldBorder().getMinZ(), this.origin.getZ() - shiftingManager.tileToBlock(zRange) - 1)
                ),
                new Vec3i(
                        (int) Math.min(this.level.getWorldBorder().getMaxX(), this.origin.getX() + shiftingManager.tileToBlock(xRange) + 1),
                        level.getMaxBuildHeight(),
                        (int) Math.min(this.level.getWorldBorder().getMaxZ(), this.origin.getZ() + shiftingManager.tileToBlock(zRange) + 1)
                )
        );
    }

    public int getXTileRange() {
        return this.shiftingManager.blockToTile(getScreenCenterX()) + 2;
    }

    public int getZTileRange() {
        return this.shiftingManager.blockToTile(getScreenCenterZ()) + 2;
    }

    public double getLocalXFromWorldX(double worldX) {
        return this.origin.getX() - worldX;
    }

    public double getLocalZFromWorldZ(double worldZ) {
        return this.origin.getZ() - worldZ;
    }

    public int getScreenCenterX() {
        return (int) ((this.width / 2) / scale);
    }

    public int getScreenCenterZ() {
        return (int) ((this.height / 2) / scale);
    }

    public long getOriginTile() {
        return this.shiftingManager.tileKey(this.origin);
    }

    public int getTileLocalXFromWorldX(int worldX) {
        return getTileX(getOriginTile()) - this.shiftingManager.blockToTile(worldX);
    }

    public int getTileLocalZFromWorldZ(int worldZ) {
        return getTileZ(getOriginTile()) - this.shiftingManager.blockToTile(worldZ);
    }

}

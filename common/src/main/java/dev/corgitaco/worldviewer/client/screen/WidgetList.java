package dev.corgitaco.worldviewer.client.screen;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

import java.util.List;

public class WidgetList<E extends ContainerObjectSelectionList.Entry<E>> extends ContainerObjectSelectionList<E>  {

    public WidgetList(List<AbstractWidget> widgets, int width, int height, int y0, int y1, int itemHeight) {
        super(Minecraft.getInstance(), width, height, y0, y1, itemHeight);
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
        if (widgets.isEmpty()) {
//            throw new IllegalArgumentException("Must have at least 1 widget.");
        }

        for (AbstractWidget widget : widgets) {
            this.addEntry((E) new Entry(widget));
        }
    }

    @Override
    protected int getScrollbarPosition() {
        return this.x1 - 5;
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    // Fixes an issue in vanilla lists where entries would render above their bounds.
    @Override
    protected int getRowTop(int index) {
        int rowTop = super.getRowTop(index);
        if (rowTop < this.y0) {
            return Integer.MAX_VALUE;
        }
        return rowTop;
    }

    // Fixes an issue in vanilla lists where entries would render below their bounds.
    @Override
    public int getRowBottom(int index) {
        int rowBottom = super.getRowBottom(index);
        if (rowBottom > this.y1) {
            return Integer.MIN_VALUE;
        }
        return rowBottom;
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        super.renderList(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderList(GuiGraphics guiGraphics, int $$1, int $$2, float $$3) {
    }

    public static class Entry extends ContainerObjectSelectionList.Entry<Entry> {

        private final AbstractWidget widget;

        public Entry(AbstractWidget text) {
            this.widget = text;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int pIndex, int pTop, int pLeft, int rowWidth, int pHeight, int pMouseX, int pMouseY, boolean pIsMouseOver, float pPartialTick) {
            this.widget.setX(pLeft);
            this.widget.setY(pTop);
            this.widget.render(guiGraphics, pMouseX, pMouseY, pPartialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.widget);
        }
    }
}
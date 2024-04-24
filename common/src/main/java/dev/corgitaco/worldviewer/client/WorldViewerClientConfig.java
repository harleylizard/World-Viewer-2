package dev.corgitaco.worldviewer.client;

public record WorldViewerClientConfig(Gui gui, Screen screen) {


    public record Gui(int xOffset, int yOffset, int mapSizeX, int mapSizeY, int borderSize, int borderColor) {}

    public record Screen() {
    }
}

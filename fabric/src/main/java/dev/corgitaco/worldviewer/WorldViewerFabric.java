package dev.corgitaco.worldviewer;

import net.fabricmc.api.ModInitializer;

public class WorldViewerFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {
        CommonClass.init();
    }
}

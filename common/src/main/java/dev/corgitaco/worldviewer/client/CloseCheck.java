package dev.corgitaco.worldviewer.client;

public interface CloseCheck {

    boolean canClose();

    void setCanClose(boolean canClose);

    boolean shouldClose();


    void setShouldClose(boolean shouldClose);
}

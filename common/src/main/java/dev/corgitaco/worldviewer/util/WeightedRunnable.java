package dev.corgitaco.worldviewer.util;

public interface WeightedRunnable extends Runnable {
    int priority();
}
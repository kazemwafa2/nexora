package com.nexora.core.window;

public final class DesktopWindow {
    public final int id;
    public final String title;
    Rect bounds;
    Rect restore;
    WindowState state = WindowState.NORMAL;

    DesktopWindow(int id, String title, Rect bounds) {
        this.id = id;
        this.title = title;
        this.bounds = bounds;
        this.restore = bounds;
    }

    public Rect bounds() { return bounds; }

    public WindowState state() { return state; }
}

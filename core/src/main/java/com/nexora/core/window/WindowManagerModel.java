package com.nexora.core.window;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure window model (no Android classes): z-order, focus, move/resize with clamping, maximize,
 * snap, minimize, and re-layout on screen-size change. The Android view layer only renders it.
 */
public final class WindowManagerModel {
    public static final int TITLE_BAR_HEIGHT = 40;
    public static final int MIN_W = 240;
    public static final int MIN_H = 160;
    public static final int MIN_VISIBLE_TITLE = 80;

    private int screenW;
    private int screenH;
    private final int taskbarH;
    private final int maxWindows;
    private int nextId = 1;
    private final List<DesktopWindow> zOrder = new ArrayList<DesktopWindow>(); // last = topmost

    private final int titleH;
    private final int minW;
    private final int minH;
    private final int minVisible;

    public WindowManagerModel(int screenW, int screenH, int taskbarH, int maxWindows) {
        this(screenW, screenH, taskbarH, maxWindows, TITLE_BAR_HEIGHT, MIN_W, MIN_H, MIN_VISIBLE_TITLE);
    }

    /** All sizes are in pixels; the Android view passes values scaled by screen density. */
    public WindowManagerModel(int screenW, int screenH, int taskbarH, int maxWindows,
                              int titleBarHeight, int minWidth, int minHeight, int minVisibleTitle) {
        if (titleBarHeight < 1 || minWidth < 1 || minHeight < titleBarHeight || minVisibleTitle < 1) {
            throw new IllegalArgumentException("bad window size parameters");
        }
        if (screenW < minWidth || screenH < minHeight + taskbarH) throw new IllegalArgumentException("screen too small");
        if (maxWindows < 1) throw new IllegalArgumentException("maxWindows");
        this.screenW = screenW;
        this.screenH = screenH;
        this.taskbarH = taskbarH;
        this.maxWindows = maxWindows;
        this.titleH = titleBarHeight;
        this.minW = minWidth;
        this.minH = minHeight;
        this.minVisible = minVisibleTitle;
    }

    public int titleBarHeight() { return titleH; }

    private Rect workArea() { return new Rect(0, 0, screenW, screenH - taskbarH); }

    /** Returns the new window, or null if the limit for this performance tier is reached. */
    public DesktopWindow open(String title, int prefW, int prefH) {
        if (zOrder.size() >= maxWindows) return null;
        Rect wa = workArea();
        int w = clamp(prefW, minW, wa.width());
        int h = clamp(prefH, minH, wa.height());
        int cascade = (zOrder.size() % 8) * 32;
        int left = Math.min(cascade, wa.width() - w);
        int top = Math.min(cascade, wa.height() - h);
        DesktopWindow win = new DesktopWindow(nextId++, title, new Rect(left, top, left + w, top + h));
        zOrder.add(win);
        return win;
    }

    public int count() { return zOrder.size(); }

    public List<DesktopWindow> windowsBottomToTop() { return new ArrayList<DesktopWindow>(zOrder); }

    public DesktopWindow get(int id) {
        for (DesktopWindow w : zOrder) if (w.id == id) return w;
        return null;
    }

    public DesktopWindow focused() {
        for (int i = zOrder.size() - 1; i >= 0; i--) {
            if (zOrder.get(i).state != WindowState.MINIMIZED) return zOrder.get(i);
        }
        return null;
    }

    public boolean focus(int id) {
        DesktopWindow w = get(id);
        if (w == null) return false;
        if (w.state == WindowState.MINIMIZED) w.state = w.restore.equals(workArea()) ? WindowState.MAXIMIZED : WindowState.NORMAL;
        zOrder.remove(w);
        zOrder.add(w);
        return true;
    }

    public boolean close(int id) {
        DesktopWindow w = get(id);
        return w != null && zOrder.remove(w);
    }

    public boolean minimize(int id) {
        DesktopWindow w = get(id);
        if (w == null) return false;
        w.state = WindowState.MINIMIZED;
        return true;
    }

    public boolean maximize(int id) {
        DesktopWindow w = get(id);
        if (w == null) return false;
        if (w.state == WindowState.NORMAL) w.restore = w.bounds;
        w.state = WindowState.MAXIMIZED;
        w.bounds = workArea();
        focus(id);
        return true;
    }

    public boolean restore(int id) {
        DesktopWindow w = get(id);
        if (w == null) return false;
        w.state = WindowState.NORMAL;
        w.bounds = fit(w.restore);
        focus(id);
        return true;
    }

    public boolean snap(int id, boolean left) {
        DesktopWindow w = get(id);
        if (w == null) return false;
        Rect wa = workArea();
        int half = wa.width() / 2;
        if (half < minW) return false;   // a half-screen window would be narrower than the minimum
        if (w.state == WindowState.NORMAL) w.restore = w.bounds;
        w.bounds = left ? new Rect(0, 0, half, wa.height()) : new Rect(half, 0, wa.width(), wa.height());
        w.state = left ? WindowState.SNAPPED_LEFT : WindowState.SNAPPED_RIGHT;
        focus(id);
        return true;
    }

    /** Drag by the title bar. At least minVisible px of the title bar always stays reachable. */
    public boolean move(int id, int dx, int dy) {
        DesktopWindow w = get(id);
        if (w == null || w.state != WindowState.NORMAL) return false;
        Rect wa = workArea();
        int left = clamp(w.bounds.left + dx, minVisible - w.bounds.width(), wa.width() - minVisible);
        int top = clamp(w.bounds.top + dy, 0, wa.height() - titleH);
        w.bounds = new Rect(left, top, left + w.bounds.width(), top + w.bounds.height());
        return true;
    }

    /** Resize from the bottom-right corner; keeps minimum size and stays inside the work area. */
    public boolean resize(int id, int dw, int dh) {
        DesktopWindow w = get(id);
        if (w == null || w.state != WindowState.NORMAL) return false;
        Rect wa = workArea();
        int lowW = Math.max(minW, minVisible - w.bounds.left);   // right edge must stay reachable
        int nw = clamp(w.bounds.width() + dw, lowW, wa.width() - w.bounds.left);
        int nh = clamp(w.bounds.height() + dh, minH, wa.height() - w.bounds.top);
        if (nw < lowW) nw = lowW;
        if (nh < minH) nh = minH;
        w.bounds = new Rect(w.bounds.left, w.bounds.top, w.bounds.left + nw, w.bounds.top + nh);
        return true;
    }

    /** Topmost non-minimized window containing the point, or null. */
    public DesktopWindow hitTest(int x, int y) {
        for (int i = zOrder.size() - 1; i >= 0; i--) {
            DesktopWindow w = zOrder.get(i);
            if (w.state != WindowState.MINIMIZED && w.bounds.contains(x, y)) return w;
        }
        return null;
    }

    /** Rotation / resolution change: nothing may end up unreachable. */
    public void onScreenChanged(int newW, int newH) {
        if (newW < minW || newH < minH + taskbarH) return; // ignore absurd sizes, keep old layout
        screenW = newW;
        screenH = newH;
        Rect wa = workArea();
        for (DesktopWindow w : zOrder) {
            if (w.state == WindowState.MAXIMIZED) {
                w.bounds = wa;
            } else if (w.state == WindowState.SNAPPED_LEFT || w.state == WindowState.SNAPPED_RIGHT) {
                if (wa.width() / 2 < minW) {          // screen became too narrow for a half window
                    w.state = WindowState.MAXIMIZED;
                    w.bounds = wa;
                } else if (w.state == WindowState.SNAPPED_LEFT) {
                    w.bounds = new Rect(0, 0, wa.width() / 2, wa.height());
                } else {
                    w.bounds = new Rect(wa.width() / 2, 0, wa.width(), wa.height());
                }
            } else {
                w.bounds = fit(w.bounds);
            }
            w.restore = fit(w.restore);
        }
    }

    private Rect fit(Rect r) {
        Rect wa = workArea();
        int w = clamp(r.width(), minW, wa.width());
        int h = clamp(r.height(), minH, wa.height());
        int left = clamp(r.left, 0, wa.width() - w);
        int top = clamp(r.top, 0, wa.height() - h);
        return new Rect(left, top, left + w, top + h);
    }

    private static int clamp(int v, int lo, int hi) {
        if (hi < lo) return lo;
        return v < lo ? lo : v > hi ? hi : v;
    }
}

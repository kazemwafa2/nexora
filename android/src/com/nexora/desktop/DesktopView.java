package com.nexora.desktop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import com.nexora.core.capability.DeviceFacts;
import com.nexora.core.capability.Evidence;
import com.nexora.core.capability.Tier;
import com.nexora.core.capability.TierClassifier;
import com.nexora.core.capability.TierDecision;
import com.nexora.core.capability.TierPolicy;
import com.nexora.core.diag.DiagReport;
import com.nexora.core.diag.DiagnosticsRunner;
import com.nexora.core.diag.Redactor;
import com.nexora.core.diag.Status;
import com.nexora.core.diag.TestResult;
import com.nexora.core.files.FileOps;
import com.nexora.core.settings.Settings;
import com.nexora.core.util.Clock;
import com.nexora.core.util.Tri;
import com.nexora.core.window.DesktopWindow;
import com.nexora.core.window.Rect;
import com.nexora.core.window.WindowManagerModel;
import com.nexora.core.window.WindowState;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The whole desktop is one custom View: wallpaper, icons, windows, Start menu, taskbar.
 * Window geometry lives in the pure-Java WindowManagerModel (tested); this class only draws it.
 */
final class DesktopView extends View {
    private static final int MODEL_MAX_WINDOWS = 8;
    private static final String[] APP_IDS = {"files", "settings", "diag", "about"};

    private final Activity act;
    private final Settings settings;
    private final float d;
    private final int tb;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer, Content> contents = new HashMap<Integer, Content>();
    private final boolean rtl;

    private WindowManagerModel wm;
    private LinearGradient wallpaper;
    private boolean startOpen;
    private Tier autoTier = Tier.BASIC;
    private Tier tier = Tier.BASIC;
    private Tri execResult = Tri.UNKNOWN;
    private Evidence evidence = new Evidence();
    private TierDecision lastDecision;
    private String notice;
    private long noticeUntil;
    private int dragId = -1;
    private int dragMode;           // 0 none, 1 move, 2 resize
    private int lastX;
    private int lastY;
    private String clockText = "";
    private int insL;
    private int insT;
    private int insR;
    private int insB;
    private int downX;
    private int downY;
    private boolean moved;

    DesktopView(Activity act, Settings settings) {
        super(act);
        this.act = act;
        this.settings = settings;
        this.rtl = settings.isRtl();
        this.d = act.getResources().getDisplayMetrics().density;
        this.tb = (int) (52 * d);
        recomputeTier();
        tickClock();
    }

    // ------------------------------------------------------------------ tier / state

    private void recomputeTier() {
        DeviceFacts f = CapabilityProbe.collect(act, execResult);
        lastDecision = new TierClassifier(TierPolicy.defaults()).classify(f, evidence);
        autoTier = lastDecision.tier;
        tier = settings.effectiveTier(autoTier);
    }

    private int maxWindows() {
        return tier == Tier.BASIC ? 2 : tier == Tier.STANDARD ? 6 : 8;
    }

    private boolean dark() {
        String t = settings.theme();
        if ("dark".equals(t)) return true;
        if ("light".equals(t)) return false;
        int night = act.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void tickClock() {
        clockText = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        postDelayed(new Runnable() {
            @Override public void run() {
                tickClock();
                invalidate();
            }
        }, 30000L);
    }

    private void say(String s) {
        notice = s;
        noticeUntil = System.currentTimeMillis() + 3000L;
        invalidate();
    }

    boolean handleBack() {
        if (startOpen) {
            startOpen = false;
            invalidate();
            return true;
        }
        if (wm != null && wm.focused() != null) {
            closeWindow(wm.focused().id);
            return true;
        }
        return false;
    }

    void onTrim(int level) {
        if (wm == null || level < 15) return;   // 15 = TRIM_MEMORY_RUNNING_CRITICAL
        DesktopWindow keep = wm.focused();
        for (DesktopWindow w : wm.windowsBottomToTop()) {
            if (keep == null || w.id != keep.id) closeWindow(w.id);
        }
        say("Low memory: background windows were closed");
    }

    private void closeWindow(int id) {
        wm.close(id);
        contents.remove(Integer.valueOf(id));
        invalidate();
    }

    // ------------------------------------------------------------------ layout

    /** Usable width/height: the view minus system bars (navigation bar, cutout) reported by Android. */
    private int vw() { return getWidth() - insL - insR; }

    private int vh() { return getHeight() - insT - insB; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets w) {
        insL = w.getSystemWindowInsetLeft();
        insT = w.getSystemWindowInsetTop();
        insR = w.getSystemWindowInsetRight();
        insB = w.getSystemWindowInsetBottom();
        relayout();
        invalidate();
        return w;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        wallpaper = new LinearGradient(0, 0, 0, h, act.getColor(R.color.nexora_navy), act.getColor(R.color.nexora_blue_dark), Shader.TileMode.CLAMP);
        relayout();
    }

    private void relayout() {
        int w = vw();
        int h = vh();
        int minW = (int) (240 * d);
        int minH = (int) (160 * d);
        if (w < minW || h < minH + tb) return;   // keep the previous layout for absurd sizes
        if (wm == null) {
            wm = new WindowManagerModel(w, h, tb, MODEL_MAX_WINDOWS, (int) (44 * d), minW, minH, (int) (80 * d));
        } else {
            wm.onScreenChanged(w, h);
        }
    }

    private int iconSize() { return (int) (72 * d); }

    private int iconLeft(int i) {
        int gap = (int) (12 * d);
        return rtl ? vw() - gap - iconSize() : gap;
    }

    private int iconTop(int i) { return (int) (16 * d) + i * (iconSize() + (int) (16 * d)); }

    private int startBtnLeft() { return rtl ? vw() - tb : 0; }

    private int menuItemH() { return (int) (48 * d); }

    private int menuLeft() { return rtl ? vw() - (int) (260 * d) : 0; }

    private int menuTop() { return vh() - tb - APP_IDS.length * menuItemH() - (int) (40 * d); }

    // ------------------------------------------------------------------ drawing

    private void text(Canvas c, String s, float x, float y, int color, float sp, boolean bold, boolean alignEnd) {
        p.setColor(color);
        p.setTextSize(sp * d);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        p.setTextAlign(alignEnd ? Paint.Align.RIGHT : Paint.Align.LEFT);
        c.drawText(s, x, y, p);
    }

    private String fit(String s, float maxPx, float sp) {
        p.setTextSize(sp * d);
        if (p.measureText(s) <= maxPx) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s.substring(0, n) + "…") > maxPx) n--;
        return s.substring(0, n) + "…";
    }

    private String appLabel(String id) {
        if ("files".equals(id)) return act.getString(R.string.app_files);
        if ("settings".equals(id)) return act.getString(R.string.app_settings);
        if ("diag".equals(id)) return act.getString(R.string.app_diag);
        return act.getString(R.string.app_about);
    }

    @Override protected void onDraw(Canvas c) {
        boolean dk = dark();
        p.setStyle(Paint.Style.FILL);
        p.setShader(wallpaper);
        c.drawRect(0, 0, getWidth(), getHeight(), p);
        p.setShader(null);
        c.save();
        c.translate(insL, insT);   // everything below lives inside the usable area
        int w = vw();
        int h = vh();

        int ice = act.getColor(R.color.nexora_ice);
        int cyan = act.getColor(R.color.nexora_cyan);
        int navy = act.getColor(R.color.nexora_navy);
        int blueDark = act.getColor(R.color.nexora_blue_dark);

        // desktop icons
        for (int i = 0; i < APP_IDS.length; i++) {
            int l = iconLeft(i);
            int t = iconTop(i);
            p.setColor(cyan);
            c.drawRoundRect(l + 12 * d, t, l + iconSize() - 12 * d, t + iconSize() - 24 * d, 10 * d, 10 * d, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(navy);
            p.setTextSize(22 * d);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(appLabel(APP_IDS[i]).substring(0, 1), l + iconSize() / 2f, t + (iconSize() - 24 * d) / 2f + 8 * d, p);
            p.setColor(ice);
            p.setTextSize(11 * d);
            p.setTypeface(Typeface.DEFAULT);
            c.drawText(fit(appLabel(APP_IDS[i]), iconSize(), 11), l + iconSize() / 2f, t + iconSize() - 6 * d, p);
        }

        // windows
        if (wm != null) {
            for (DesktopWindow win : wm.windowsBottomToTop()) {
                if (win.state() == WindowState.MINIMIZED) continue;
                drawWindow(c, win, dk, cyan, navy, blueDark, ice);
            }
        }

        if (startOpen) drawStartMenu(c, dk, ice, navy, blueDark, cyan);
        drawTaskbar(c, w, h, ice, cyan, navy, blueDark);

        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            p.setColor(0xCC000000);
            c.drawRoundRect(w / 2f - 160 * d, h - tb - 56 * d, w / 2f + 160 * d, h - tb - 16 * d, 8 * d, 8 * d, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(13 * d);
            c.drawText(fit(notice, 300 * d, 13), w / 2f, h - tb - 30 * d, p);
            postInvalidateDelayed(3100L);
        }
        c.restore();
    }

    private void drawWindow(Canvas c, DesktopWindow win, boolean dk, int cyan, int navy, int blueDark, int ice) {
        Rect b = win.bounds();
        boolean focused = wm.focused() != null && wm.focused().id == win.id;
        int th = wm.titleBarHeight();
        p.setColor(dk ? 0xFF1B2B40 : 0xFFF2F8FC);
        c.drawRect(b.left, b.top, b.right, b.bottom, p);
        p.setColor(focused ? cyan : blueDark);
        c.drawRect(b.left, b.top, b.right, b.top + th, p);
        int fg = focused ? navy : ice;
        float tx = rtl ? b.right - 10 * d : b.left + 10 * d;
        float room = b.width() - 3 * th - 20 * d;
        text(c, fit(win.title, room, 14), tx, b.top + th / 2f + 5 * d, fg, 14, true, rtl);
        // buttons: minimize, maximize/restore, close (mirrored in RTL)
        String[] sym = {"–", win.state() == WindowState.MAXIMIZED ? "❐" : "□", "×"};
        for (int i = 0; i < 3; i++) {
            int bl = buttonLeft(b, th, i);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(fg);
            p.setTextSize(18 * d);
            c.drawText(sym[i], bl + th / 2f, b.top + th / 2f + 6 * d, p);
        }
        Content content = contents.get(Integer.valueOf(win.id));
        if (content != null) {
            c.save();
            c.clipRect(b.left, b.top + th, b.right, b.bottom);
            content.draw(c, b.left, b.top + th, b.right, b.bottom, dk ? ice : navy);
            c.restore();
        }
        if (win.state() == WindowState.NORMAL) {   // resize grip
            p.setColor(focused ? cyan : blueDark);
            c.drawRect(b.right - 20 * d, b.bottom - 20 * d, b.right, b.bottom, p);
        }
    }

    private int buttonLeft(Rect b, int th, int index) {
        // index 0 = minimize, 1 = maximize, 2 = close; close is at the outer edge
        if (rtl) return b.left + index * th;
        return b.right - (3 - index) * th;
    }

    private void drawStartMenu(Canvas c, boolean dk, int ice, int navy, int blueDark, int cyan) {
        int l = menuLeft();
        int t = menuTop();
        int r = l + (int) (260 * d);
        int b = vh() - tb;
        p.setColor(dk ? 0xF0122D4B : 0xF0E6F7FF);
        c.drawRect(l, t, r, b, p);
        int fg = dk ? ice : navy;
        text(c, act.getString(R.string.engine_status), rtl ? r - 12 * d : l + 12 * d, t + 24 * d, fg, 12, false, rtl);
        for (int i = 0; i < APP_IDS.length; i++) {
            int it = t + (int) (40 * d) + i * menuItemH();
            text(c, appLabel(APP_IDS[i]), rtl ? r - 16 * d : l + 16 * d, it + menuItemH() / 2f + 6 * d, fg, 16, false, rtl);
        }
    }

    private void drawTaskbar(Canvas c, int w, int h, int ice, int cyan, int navy, int blueDark) {
        p.setColor(0xF0081525);
        c.drawRect(0, h - tb, w, h, p);
        // start button (four small squares, the Nexora mark)
        int sl = startBtnLeft();
        float s = 9 * d;
        float cx = sl + tb / 2f;
        float cy = h - tb / 2f;
        int[] col = {cyan, act.getColor(R.color.nexora_turquoise), ice, cyan};
        for (int i = 0; i < 4; i++) {
            p.setColor(col[i]);
            float x = cx + (i % 2 == 0 ? -s - 1.5f * d : 1.5f * d);
            float y = cy + (i < 2 ? -s - 1.5f * d : 1.5f * d);
            c.drawRoundRect(x, y, x + s, y + s, 2 * d, 2 * d, p);
        }
        // window buttons
        if (wm != null) {
            int x = rtl ? vw() - tb - (int) (6 * d) : tb + (int) (6 * d);
            int bw = (int) (96 * d);
            for (DesktopWindow win : wm.windowsBottomToTop()) {
                int l = rtl ? x - bw : x;
                boolean foc = wm.focused() != null && wm.focused().id == win.id && win.state() != WindowState.MINIMIZED;
                p.setColor(foc ? blueDark : 0xFF0E2238);
                c.drawRoundRect(l, h - tb + 6 * d, l + bw, h - 6 * d, 6 * d, 6 * d, p);
                text(c, fit(win.title, bw - 12 * d, 12), rtl ? l + bw - 6 * d : l + 6 * d, h - tb / 2f + 4 * d, ice, 12, false, rtl);
                x = rtl ? x - bw - (int) (6 * d) : x + bw + (int) (6 * d);
            }
        }
        text(c, clockText, rtl ? 12 * d : w - 12 * d, h - tb / 2f + 5 * d, ice, 14, false, !rtl);
    }

    // ------------------------------------------------------------------ touch

    @Override public boolean onTouchEvent(MotionEvent e) {
        int x = (int) e.getX() - insL;
        int y = (int) e.getY() - insT;
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN) {
            lastX = x;
            lastY = y;
            downX = x;
            downY = y;
            moved = false;
            dragId = -1;
            dragMode = 0;
            onDown(x, y);
            return true;
        }
        if (a == MotionEvent.ACTION_MOVE && wm != null && dragId >= 0) {
            int dx = x - lastX;
            int dy = y - lastY;
            if (dragMode == 1) wm.move(dragId, dx, dy);
            if (dragMode == 2) wm.resize(dragId, dx, dy);
            if (dragMode == 3) {
                if (!moved && Math.abs(y - downY) > 8 * d) moved = true;
                Content ct = contents.get(Integer.valueOf(dragId));
                DesktopWindow win = wm.get(dragId);
                if (moved && ct != null && win != null) {
                    int viewH = win.bounds().height() - wm.titleBarHeight();
                    ct.scroll = Math.max(0, Math.min(ct.maxScroll(viewH), ct.scroll - dy));
                }
            }
            lastX = x;
            lastY = y;
            invalidate();
            return true;
        }
        if (a == MotionEvent.ACTION_UP && dragMode == 3 && !moved && wm != null) {
            DesktopWindow win = wm.get(dragId);
            Content ct = contents.get(Integer.valueOf(dragId));
            if (win != null && ct != null) ct.tap(x, y, win.bounds().left, win.bounds().top + wm.titleBarHeight());
            invalidate();
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            dragId = -1;
            dragMode = 0;
        }
        return true;
    }

    private void onDown(int x, int y) {
        int h = vh();
        if (startOpen) {
            int l = menuLeft();
            int t = menuTop();
            if (x >= l && x < l + (int) (260 * d) && y >= t && y < h - tb) {
                int idx = (y - t - (int) (40 * d)) / menuItemH();
                if (y - t >= (int) (40 * d) && idx >= 0 && idx < APP_IDS.length) openApp(APP_IDS[idx]);
            }
            startOpen = false;
            invalidate();
            if (y < h - tb) return;
        }
        if (y >= h - tb) {
            onTaskbar(x);
            return;
        }
        if (wm != null) {
            DesktopWindow win = wm.hitTest(x, y);
            if (win != null) {
                wm.focus(win.id);
                Rect b = win.bounds();
                int th = wm.titleBarHeight();
                if (y < b.top + th) {
                    for (int i = 0; i < 3; i++) {
                        int bl = buttonLeft(b, th, i);
                        if (x >= bl && x < bl + th) {
                            if (i == 0) wm.minimize(win.id);
                            else if (i == 1) {
                                if (win.state() == WindowState.MAXIMIZED) wm.restore(win.id); else wm.maximize(win.id);
                            } else closeWindow(win.id);
                            invalidate();
                            return;
                        }
                    }
                    dragId = win.id;
                    dragMode = 1;
                } else if (win.state() == WindowState.NORMAL && x >= b.right - 28 * d && y >= b.bottom - 28 * d) {
                    dragId = win.id;
                    dragMode = 2;
                } else {
                    dragId = win.id;     // content: scroll on drag, tap on release
                    dragMode = 3;
                }
                invalidate();
                return;
            }
        }
        for (int i = 0; i < APP_IDS.length; i++) {
            int l = iconLeft(i);
            int t = iconTop(i);
            if (x >= l && x < l + iconSize() && y >= t && y < t + iconSize()) {
                openApp(APP_IDS[i]);
                return;
            }
        }
        invalidate();
    }

    private void onTaskbar(int x) {
        int sl = startBtnLeft();
        if (x >= sl && x < sl + tb) {
            startOpen = !startOpen;
            invalidate();
            return;
        }
        if (wm == null) return;
        int xx = rtl ? vw() - tb - (int) (6 * d) : tb + (int) (6 * d);
        int bw = (int) (96 * d);
        for (DesktopWindow win : wm.windowsBottomToTop()) {
            int l = rtl ? xx - bw : xx;
            if (x >= l && x < l + bw) {
                boolean foc = wm.focused() != null && wm.focused().id == win.id && win.state() != WindowState.MINIMIZED;
                if (foc) wm.minimize(win.id); else wm.focus(win.id);
                invalidate();
                return;
            }
            xx = rtl ? xx - bw - (int) (6 * d) : xx + bw + (int) (6 * d);
        }
    }

    // ------------------------------------------------------------------ apps

    private void openApp(String id) {
        if (wm == null) return;
        String title = appLabel(id);
        for (DesktopWindow win : wm.windowsBottomToTop()) {
            if (win.title.equals(title)) {
                wm.focus(win.id);
                invalidate();
                return;
            }
        }
        if (wm.count() >= maxWindows()) {
            say(act.getString(R.string.limit_reached) + " (" + tier + ")");
            return;
        }
        int ww = Math.min((int) (360 * d), vw() - (int) (16 * d));
        int hh = Math.min((int) (440 * d), vh() - tb - (int) (16 * d));
        DesktopWindow win = wm.open(title, ww, hh);
        if (win == null) {
            say(act.getString(R.string.limit_reached));
            return;
        }
        Content ct;
        if ("files".equals(id)) ct = new FilesContent();
        else if ("settings".equals(id)) ct = new SettingsContent();
        else if ("diag".equals(id)) ct = new DiagContent();
        else ct = new AboutContent();
        contents.put(Integer.valueOf(win.id), ct);
        invalidate();
    }

    // ------------------------------------------------------------------ window contents

    private abstract class Content {
        int scroll;

        abstract void draw(Canvas c, int l, int t, int r, int b, int fg);

        void tap(int x, int y, int l, int t) { }

        /** Number of 26 dp text rows; used to limit scrolling. */
        int rows() { return 0; }

        int maxScroll(int viewH) { return Math.max(0, (int) (rows() * 26 * d) - viewH + (int) (8 * d)); }

        /** Draws text row `row` (26 dp high) below the title bar; tap handlers use the same row math. */
        void line(Canvas c, String s, int l, int t, int r, int row, int fg, boolean bold) {
            float y = t + row * 26 * d + 19 * d - scroll;
            text(c, fit(s, r - l - 20 * d, 13), rtl ? r - 10 * d : l + 10 * d, y, fg, 13, bold, rtl);
        }

        int rowAt(int y, int t) { return (int) ((y - t + scroll) / (26 * d)); }
    }

    private final class AboutContent extends Content {
        @Override int rows() { return 6; }

        @Override void draw(Canvas c, int l, int t, int r, int b, int fg) {
            int[] ids = {R.string.tagline, R.string.about_line1, R.string.about_line2, R.string.about_line3, R.string.about_line4, R.string.about_line5};
            for (int i = 0; i < ids.length; i++) line(c, act.getString(ids[i]), l, t, r, i, fg, i == 0);
        }
    }

    private final class SettingsContent extends Content {
        @Override int rows() { return 5; }

        @Override void draw(Canvas c, int l, int t, int r, int b, int fg) {
            String[] rows = {
                act.getString(R.string.mode_label) + ": " + tier + " (auto: " + autoTier + ")",
                act.getString(R.string.theme_label) + ": " + settings.theme(),
                act.getString(R.string.language_label) + ": " + settings.language(),
                act.getString(R.string.override_label) + ": " + settings.modeOverride(),
                act.getString(R.string.tap_to_change)};
            for (int i = 0; i < rows.length; i++) line(c, rows[i], l, t, r, i, fg, i == 0);
        }

        @Override void tap(int x, int y, int l, int t) {
            int row = rowAt(y, t);
            if (row == 1) {
                settings.setTheme(next(Settings.THEMES, settings.theme()));
            } else if (row == 2) {
                settings.setLanguage(next(Settings.LANGUAGES, settings.language()));
                act.recreate();   // reload strings and layout direction
            } else if (row == 3) {
                settings.setModeOverride(next(Settings.MODE_OVERRIDES, settings.modeOverride()));
                recomputeTier();
                trimToLimit();
            }
            invalidate();
        }

        private String next(List<String> all, String cur) {
            int i = all.indexOf(cur);
            return all.get((i + 1) % all.size());
        }
    }

    private void trimToLimit() {
        if (wm == null) return;
        List<DesktopWindow> ws = wm.windowsBottomToTop();
        for (int i = 0; i < ws.size() - maxWindows(); i++) closeWindow(ws.get(i).id);
    }

    private final class FilesContent extends Content {
        private final File root = act.getFilesDir();
        private File cur = root;
        private String selected;
        private File clip;
        private boolean clipCut;
        private boolean confirmDelete;
        private String msg = act.getString(R.string.files_root_note);
        private final int[] toolbar = {R.string.files_up, R.string.files_newfolder, R.string.files_copy,
            R.string.files_cut, R.string.files_paste, R.string.files_delete};

        @Override int rows() { return FileOps.list(cur).size() + 2; }

        @Override void draw(Canvas c, int l, int t, int r, int b, int fg) {
            List<FileOps.Entry> es = FileOps.list(cur);
            if (es.isEmpty()) line(c, act.getString(R.string.empty_folder), l, t, r, 2, fg, false);
            for (int i = 0; i < es.size(); i++) {
                FileOps.Entry e = es.get(i);
                String mark = e.name.equals(selected) ? "● " : "   ";
                String label = mark + e.name + (e.directory ? "/" : "  (" + sizeText(e.size) + ")");
                line(c, label, l, t, r, i + 2, fg, e.name.equals(selected));
            }
            // fixed header (toolbar + status) drawn over the scrolling list
            int keep = scroll;
            scroll = 0;
            p.setColor(dark() ? 0xFF1B2B40 : 0xFFF2F8FC);
            c.drawRect(l, t, r, t + 52 * d, p);
            int colW = (r - l) / toolbar.length;
            for (int i = 0; i < toolbar.length; i++) {
                int idx = rtl ? toolbar.length - 1 - i : i;
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(11 * d);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                boolean off = (toolbar[idx] == R.string.files_paste && clip == null);
                p.setColor(off ? 0x66808080 : fg);
                c.drawText(fit(act.getString(toolbar[idx]), colW - 4 * d, 11), l + i * colW + colW / 2f, t + 18 * d, p);
            }
            line(c, "/" + relative() + (msg.length() > 0 ? "   " + msg : ""), l, t, r, 1, fg, false);
            scroll = keep;
        }

        private String sizeText(long n) {
            if (n < 1024) return n + " B";
            if (n < 1024 * 1024) return (n / 1024) + " KB";
            return (n / (1024 * 1024)) + " MB";
        }

        private String relative() {
            String a = cur.getAbsolutePath();
            String base = root.getAbsolutePath();
            return a.length() > base.length() ? a.substring(base.length() + 1) : "";
        }

        private void report(FileOps.Result r) {
            msg = r.isOk() ? act.getString(R.string.files_done) : act.getString(R.string.files_failed) + " " + r.code;
        }

        @Override void tap(int x, int y, int l, int t) {
            if (y - t < 52 * d) {
                if (y - t < 26 * d) toolbarTap(x, l);
                invalidate();
                return;
            }
            int row = rowAt(y, t) - 2;
            List<FileOps.Entry> es = FileOps.list(cur);
            if (row < 0 || row >= es.size()) return;
            FileOps.Entry e = es.get(row);
            confirmDelete = false;
            if (e.name.equals(selected) && e.directory) {
                cur = new File(cur, e.name);
                selected = null;
                scroll = 0;
            } else {
                selected = e.name;
            }
            invalidate();
        }

        private void toolbarTap(int x, int l) {
            Rect b = null;
            int width = 0;
            for (DesktopWindow w : wm.windowsBottomToTop()) {
                if (contents.get(Integer.valueOf(w.id)) == this) {
                    b = w.bounds();
                    width = b.width();
                }
            }
            if (b == null || width <= 0) return;
            int col = (x - l) * toolbar.length / width;
            if (col < 0 || col >= toolbar.length) return;
            int action = toolbar[rtl ? toolbar.length - 1 - col : col];
            if (action != R.string.files_delete) confirmDelete = false;
            if (action == R.string.files_up) {
                if (!cur.equals(root)) {
                    cur = cur.getParentFile();
                    selected = null;
                    scroll = 0;
                }
            } else if (action == R.string.files_newfolder) {
                report(FileOps.newFolder(root, cur, act.getString(R.string.files_new_folder_name)));
            } else if (action == R.string.files_copy || action == R.string.files_cut) {
                if (selected == null) { msg = act.getString(R.string.files_select); return; }
                clip = new File(cur, selected);
                clipCut = action == R.string.files_cut;
                msg = act.getString(R.string.files_done);
            } else if (action == R.string.files_paste) {
                if (clip == null) return;
                FileOps.Result r = clipCut ? FileOps.move(root, clip, cur) : FileOps.copy(root, clip, cur);
                report(r);
                if (r.isOk() && clipCut) clip = null;
            } else if (action == R.string.files_delete) {
                if (selected == null) { msg = act.getString(R.string.files_select); return; }
                if (!confirmDelete) {
                    confirmDelete = true;
                    msg = act.getString(R.string.files_confirm);
                    return;
                }
                report(FileOps.delete(root, new File(cur, selected)));
                confirmDelete = false;
                selected = null;
            }
        }
    }

    private final class DiagContent extends Content {
        private volatile List<String> lines = new ArrayList<String>();
        private volatile String reportText = "";

        @Override int rows() { return lines.size(); }

        DiagContent() {
            lines.add(act.getString(R.string.diag_running));
            Thread th = new Thread(new Runnable() {
                @Override public void run() {
                    final DiagReport rep = new DiagnosticsRunner(15000L, Clock.SYSTEM).run(AndroidDiagTests.create(act));
                    post(new Runnable() {
                        @Override public void run() { finish(rep); }
                    });
                }
            }, "nexora-diag");
            th.setDaemon(true);
            th.start();
        }

        private void finish(DiagReport rep) {
            Status ex = rep.statusOf(AndroidDiagTests.EXEC);
            execResult = ex == Status.PASS ? Tri.YES : ex == Status.FAIL ? Tri.NO : Tri.UNKNOWN;
            Evidence ev = new Evidence();
            for (TestResult tr : rep.results()) ev.put(tr.id, tr.status);
            evidence = ev;
            recomputeTier();
            trimToLimit();
            List<String> l = new ArrayList<String>();
            int tsdk = act.getApplicationInfo().targetSdkVersion;
            l.add("PASS " + rep.count(Status.PASS) + "   FAIL " + rep.count(Status.FAIL) + "   UNKNOWN " + rep.count(Status.UNKNOWN));
            l.add("targetSdk " + tsdk + "   device API " + android.os.Build.VERSION.SDK_INT);
            for (TestResult tr : rep.results()) {
                l.add(tr.status + "  " + tr.id);
                if (tr.reason.length() > 0) l.add("      " + tr.reason);
            }
            l.add("Tier: " + autoTier + (lastDecision != null && !lastDecision.blockers(Tier.ADVANCED).isEmpty()
                    ? "  (Advanced blocked: " + lastDecision.blockers(Tier.ADVANCED).size() + ")" : ""));
            l.add(act.getString(R.string.diag_share));
            lines = l;
            reportText = Redactor.redact("targetSdk: " + tsdk + "\ndevice API: " + android.os.Build.VERSION.SDK_INT + "\n\n" + rep.toText() + "\nTier: " + autoTier + "\n" + blockerText());
            invalidate();
        }

        private String blockerText() {
            StringBuilder sb = new StringBuilder("Advanced blockers:\n");
            if (lastDecision != null) {
                for (String s : lastDecision.blockers(Tier.ADVANCED)) sb.append("- ").append(s).append('\n');
            }
            return sb.toString();
        }

        @Override void draw(Canvas c, int l, int t, int r, int b, int fg) {
            List<String> ls = lines;
            for (int i = 0; i < ls.size(); i++) line(c, ls.get(i), l, t, r, i, fg, i == 0);
        }

        @Override void tap(int x, int y, int l, int t) {
            List<String> ls = lines;
            int row = rowAt(y, t);
            if (reportText.length() > 0 && row == ls.size() - 1) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, reportText);
                act.startActivity(Intent.createChooser(i, act.getString(R.string.diag_share)));
            }
        }
    }
}

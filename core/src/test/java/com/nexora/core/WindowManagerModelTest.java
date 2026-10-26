package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.window.*;

import java.util.List;
import java.util.Random;

public class WindowManagerModelTest {
    private WindowManagerModel wm() { return new WindowManagerModel(1080, 2000, 120, 6); }

    public void testOpenCascadesAndRespectsLimit() {
        WindowManagerModel m = new WindowManagerModel(1080, 2000, 120, 2);
        DesktopWindow a = m.open("A", 600, 500);
        DesktopWindow b = m.open("B", 600, 500);
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(b.bounds().left > a.bounds().left);
        assertNull(m.open("C", 600, 500));
        assertEquals(2, m.count());
    }

    public void testFocusBringsToTop() {
        WindowManagerModel m = wm();
        DesktopWindow a = m.open("A", 400, 300);
        DesktopWindow b = m.open("B", 400, 300);
        assertEquals(b.id, m.focused().id);
        m.focus(a.id);
        assertEquals(a.id, m.focused().id);
        assertFalse(m.focus(9999));
    }

    public void testMoveKeepsTitleBarReachable() {
        WindowManagerModel m = wm();
        DesktopWindow a = m.open("A", 500, 400);
        m.move(a.id, -100000, -100000);
        assertTrue(a.bounds().right >= WindowManagerModel.MIN_VISIBLE_TITLE);
        assertEquals(0, a.bounds().top);
        m.move(a.id, 100000, 100000);
        assertTrue(a.bounds().left <= 1080 - WindowManagerModel.MIN_VISIBLE_TITLE);
        assertTrue(a.bounds().top <= 2000 - 120 - WindowManagerModel.TITLE_BAR_HEIGHT);
    }

    public void testResizeHasMinimumAndStaysOnScreen() {
        WindowManagerModel m = wm();
        DesktopWindow a = m.open("A", 500, 400);
        m.resize(a.id, -100000, -100000);
        assertEquals(WindowManagerModel.MIN_W, a.bounds().width());
        assertEquals(WindowManagerModel.MIN_H, a.bounds().height());
        m.resize(a.id, 100000, 100000);
        assertTrue(a.bounds().right <= 1080);
        assertTrue(a.bounds().bottom <= 2000 - 120);
    }

    public void testMaximizeRestoreRoundTrip() {
        WindowManagerModel m = wm();
        DesktopWindow a = m.open("A", 500, 400);
        Rect before = a.bounds();
        m.maximize(a.id);
        assertEquals(WindowState.MAXIMIZED, a.state());
        assertEquals(new Rect(0, 0, 1080, 1880), a.bounds());
        m.restore(a.id);
        assertEquals(WindowState.NORMAL, a.state());
        assertEquals(before, a.bounds());
    }

    public void testSnapLeftRight() {
        WindowManagerModel m = wm();
        DesktopWindow a = m.open("A", 500, 400);
        m.snap(a.id, true);
        assertEquals(new Rect(0, 0, 540, 1880), a.bounds());
        m.snap(a.id, false);
        assertEquals(new Rect(540, 0, 1080, 1880), a.bounds());
        assertFalse(m.move(a.id, 5, 5));
    }

    public void testMinimizedWindowIsNotHitAndFocusRestoresIt() {
        WindowManagerModel m = wm();
        DesktopWindow a = m.open("A", 500, 400);
        int x = a.bounds().left + 10, y = a.bounds().top + 10;
        assertEquals(a.id, m.hitTest(x, y).id);
        m.minimize(a.id);
        assertNull(m.hitTest(x, y));
        assertNull(m.focused());
        m.focus(a.id);
        assertEquals(WindowState.NORMAL, a.state());
        assertNotNull(m.hitTest(x, y));
    }

    public void testRotationReflowKeepsEverythingInside() {
        WindowManagerModel m = wm();
        DesktopWindow a = m.open("A", 900, 1500);
        m.move(a.id, 150, 300);
        DesktopWindow b = m.open("B", 600, 500);
        m.maximize(b.id);
        m.onScreenChanged(2000, 1080);   // landscape
        assertTrue(a.bounds().right <= 2000 && a.bounds().bottom <= 1080 - 120);
        assertEquals(new Rect(0, 0, 2000, 960), b.bounds());
        m.onScreenChanged(10, 10);       // absurd size is ignored
        assertEquals(new Rect(0, 0, 2000, 960), b.bounds());
    }

    /** Property test: after thousands of random operations every window stays usable. */
    public void testRandomOperationsKeepInvariants() {
        Random rnd = new Random(12345);
        WindowManagerModel m = new WindowManagerModel(1080, 2000, 120, 8);
        int w = 1080, h = 2000;
        for (int step = 0; step < 20000; step++) {
            List<DesktopWindow> ws = m.windowsBottomToTop();
            int id = ws.isEmpty() ? 1 : ws.get(rnd.nextInt(ws.size())).id;
            switch (rnd.nextInt(10)) {
                case 0: m.open("w" + step, 100 + rnd.nextInt(3000), 100 + rnd.nextInt(3000)); break;
                case 1: m.close(id); break;
                case 2: m.move(id, rnd.nextInt(4001) - 2000, rnd.nextInt(4001) - 2000); break;
                case 3: m.resize(id, rnd.nextInt(4001) - 2000, rnd.nextInt(4001) - 2000); break;
                case 4: m.maximize(id); break;
                case 5: m.restore(id); break;
                case 6: m.snap(id, rnd.nextBoolean()); break;
                case 7: m.minimize(id); break;
                case 8: m.focus(id); break;
                default:
                    if (rnd.nextInt(20) == 0) {
                        boolean land = rnd.nextBoolean();
                        w = land ? 2000 : 1080;
                        h = land ? 1080 : 2000;
                        m.onScreenChanged(w, h);
                    }
            }
            for (DesktopWindow d : m.windowsBottomToTop()) {
                Rect r = d.bounds();
                assertTrue("width >= min at step " + step + " " + r, r.width() >= WindowManagerModel.MIN_W);
                assertTrue("height >= min at step " + step + " " + r, r.height() >= WindowManagerModel.MIN_H);
                assertTrue("title reachable at step " + step + " " + r, r.right >= WindowManagerModel.MIN_VISIBLE_TITLE
                        && r.left <= w - WindowManagerModel.MIN_VISIBLE_TITLE && r.top >= 0 && r.top <= h - 120 - WindowManagerModel.TITLE_BAR_HEIGHT);
            }
            assertTrue(m.count() <= 8);
        }
    }

    /** Same invariants with sizes scaled for a 3x-density phone (title bar 132 px, min window 720x480). */
    public void testScaledModelKeepsInvariants() {
        java.util.Random rnd = new java.util.Random(777);
        WindowManagerModel m = new WindowManagerModel(1080, 2400, 156, 8, 132, 720, 480, 240);
        assertEquals(132, m.titleBarHeight());
        for (int step = 0; step < 10000; step++) {
            List<DesktopWindow> ws = m.windowsBottomToTop();
            int id = ws.isEmpty() ? 1 : ws.get(rnd.nextInt(ws.size())).id;
            switch (rnd.nextInt(7)) {
                case 0: m.open("w", 200 + rnd.nextInt(3000), 200 + rnd.nextInt(3000)); break;
                case 1: m.close(id); break;
                case 2: m.move(id, rnd.nextInt(6001) - 3000, rnd.nextInt(6001) - 3000); break;
                case 3: m.resize(id, rnd.nextInt(6001) - 3000, rnd.nextInt(6001) - 3000); break;
                case 4: m.maximize(id); break;
                case 5: m.restore(id); break;
                default: m.snap(id, rnd.nextBoolean());
            }
            for (DesktopWindow d : m.windowsBottomToTop()) {
                Rect r = d.bounds();
                assertTrue("min width at step " + step + " " + r, r.width() >= 720);
                assertTrue("min height at step " + step + " " + r, r.height() >= 480);
                assertTrue("title reachable at step " + step + " " + r, r.right >= 240 && r.left <= 1080 - 240
                        && r.top >= 0 && r.top <= 2400 - 156 - 132);
            }
        }
    }

    public void testScreenTooSmallForScaledSizesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WindowManagerModel(600, 2400, 156, 8, 132, 720, 480, 240));
    }

    public void testSnapRefusedWhenHalfScreenIsBelowMinimum() {
        WindowManagerModel m = new WindowManagerModel(1080, 2400, 156, 8, 132, 720, 480, 240);
        DesktopWindow a = m.open("A", 800, 800);
        Rect before = a.bounds();
        assertFalse(m.snap(a.id, true));
        assertEquals(WindowState.NORMAL, a.state());
        assertEquals(before, a.bounds());
    }

    public void testSnappedWindowBecomesMaximizedWhenScreenGetsNarrow() {
        WindowManagerModel m = new WindowManagerModel(2400, 1080, 156, 8, 132, 720, 480, 240);
        DesktopWindow a = m.open("A", 900, 600);
        assertTrue(m.snap(a.id, true));
        m.onScreenChanged(1080, 2400);   // rotate to portrait: half width 540 < 720
        assertEquals(WindowState.MAXIMIZED, a.state());
        assertTrue(a.bounds().width() >= 720);
    }
}

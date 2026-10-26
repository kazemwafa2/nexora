package com.nexora.core.window;

public final class Rect {
    public final int left, top, right, bottom;

    public Rect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int width() { return right - left; }

    public int height() { return bottom - top; }

    public boolean contains(int x, int y) { return x >= left && x < right && y >= top && y < bottom; }

    public Rect offset(int dx, int dy) { return new Rect(left + dx, top + dy, right + dx, bottom + dy); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Rect)) return false;
        Rect r = (Rect) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override public int hashCode() { return ((left * 31 + top) * 31 + right) * 31 + bottom; }

    @Override public String toString() { return "[" + left + "," + top + "," + right + "," + bottom + "]"; }
}

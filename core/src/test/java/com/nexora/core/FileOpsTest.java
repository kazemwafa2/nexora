package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.files.FileOps;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class FileOpsTest {
    static File tmp() throws Exception {
        File d = File.createTempFile("nxfiles", "");
        d.delete();
        d.mkdirs();
        return d;
    }

    static File file(File dir, String name, int size) throws Exception {
        dir.mkdirs();
        File f = new File(dir, name);
        FileOutputStream o = new FileOutputStream(f);
        o.write(new byte[size]);
        o.close();
        return f;
    }

    public void testListIsFoldersFirstThenCaseInsensitive() throws Exception {
        File r = tmp();
        file(r, "b.txt", 1); file(r, "A.txt", 2); new File(r, "zeta").mkdir(); new File(r, "Alpha").mkdir();
        List<FileOps.Entry> l = FileOps.list(r);
        assertEquals("Alpha", l.get(0).name);
        assertEquals("zeta", l.get(1).name);
        assertEquals("A.txt", l.get(2).name);
        assertEquals("b.txt", l.get(3).name);
        assertEquals(2, l.get(2).size);
    }

    public void testNameValidation() {
        String[] bad = {null, "", ".", "..", "a/b", "a\\b", "x\u0000y", "tab\tname"};
        for (String s : bad) assertFalse("must reject: " + s, FileOps.validName(s));
        assertTrue(FileOps.validName("New folder"));
        assertTrue(FileOps.validName("فایل جدید"));
    }

    public void testNewFolderGetsFreeNames() throws Exception {
        File r = tmp();
        assertEquals("New folder", FileOps.newFolder(r, r, "New folder").file.getName());
        assertEquals("New folder (2)", FileOps.newFolder(r, r, "New folder").file.getName());
        assertEquals(FileOps.Code.BAD_NAME, FileOps.newFolder(r, r, "../x").code);
    }

    public void testCopyNeverOverwritesAndCopiesTrees() throws Exception {
        File r = tmp();
        File a = new File(r, "a"); file(a, "x.bin", 10); file(new File(a, "sub"), "y.bin", 20);
        File dest = new File(r, "dest"); dest.mkdir();
        FileOps.Result c1 = FileOps.copy(r, a, dest);
        FileOps.Result c2 = FileOps.copy(r, a, dest);
        assertTrue(c1.isOk() && c2.isOk());
        assertEquals("a", c1.file.getName());
        assertEquals("a (2)", c2.file.getName());
        assertEquals(20, new File(c2.file, "sub/y.bin").length());
        assertEquals(4, c1.count);   // a, x.bin, sub, y.bin
        assertTrue(a.isDirectory());
    }

    public void testCopyFolderIntoItselfRefused() throws Exception {
        File r = tmp();
        File a = new File(r, "a"); new File(a, "inner").mkdirs();
        assertEquals(FileOps.Code.INTO_ITSELF, FileOps.copy(r, a, new File(a, "inner")).code);
        assertEquals(FileOps.Code.INTO_ITSELF, FileOps.move(r, a, new File(a, "inner")).code);
    }

    public void testMoveKeepsContentAndRemovesSource() throws Exception {
        File r = tmp();
        File f = file(r, "m.bin", 33);
        File dest = new File(r, "d"); dest.mkdir();
        FileOps.Result m = FileOps.move(r, f, dest);
        assertTrue(m.isOk());
        assertFalse(f.exists());
        assertEquals(33, new File(dest, "m.bin").length());
    }

    public void testDeleteRefusesRootAndOutside() throws Exception {
        File r = tmp(); File other = tmp();
        File victim = file(other, "keep.txt", 5);
        assertEquals(FileOps.Code.IS_ROOT, FileOps.delete(r, r).code);
        assertEquals(FileOps.Code.OUTSIDE_ROOT, FileOps.delete(r, victim).code);
        assertEquals(FileOps.Code.OUTSIDE_ROOT, FileOps.delete(r, new File(r, "../" + other.getName() + "/keep.txt")).code);
        assertTrue(victim.exists());
        assertTrue(r.exists());
    }

    public void testDeleteRemovesTree() throws Exception {
        File r = tmp();
        File a = new File(r, "a"); file(new File(a, "s"), "f", 3);
        FileOps.Result d = FileOps.delete(r, a);
        assertTrue(d.isOk());
        assertEquals(3, d.count);
        assertFalse(a.exists());
    }

    public void testSymlinkEscapeIsNeverFollowed() throws Exception {
        File r = tmp(); File outside = tmp();
        File secret = file(outside, "secret.txt", 7);
        File link = new File(r, "link");
        java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath());
        assertTrue(FileOps.isSymlink(link));
        assertFalse(FileOps.isInside(r, new File(link, "secret.txt")));     // resolves outside the root
        assertEquals(FileOps.Code.OUTSIDE_ROOT, FileOps.delete(r, new File(link, "secret.txt")).code);
        assertEquals(FileOps.Code.SYMLINK_SKIPPED, FileOps.copy(r, link, r).code);
        // deleting the link itself removes only the link
        assertTrue(FileOps.delete(r, link).isOk());
        assertFalse(link.exists());
        assertTrue(secret.exists());
    }

    public void testSymlinkInsideCopiedFolderIsSkipped() throws Exception {
        File r = tmp(); File outside = tmp();
        file(outside, "secret.txt", 7);
        File a = new File(r, "a"); a.mkdir(); file(a, "ok.txt", 1);
        java.nio.file.Files.createSymbolicLink(new File(a, "esc").toPath(), outside.toPath());
        File dest = new File(r, "dest"); dest.mkdir();
        FileOps.Result c = FileOps.copy(r, a, dest);
        assertTrue(c.isOk());
        assertTrue(new File(c.file, "ok.txt").exists());
        assertFalse(new File(c.file, "esc").exists());
    }
}

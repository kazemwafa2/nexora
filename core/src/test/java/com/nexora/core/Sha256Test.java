package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.util.Sha256;

import java.io.File;
import java.io.FileOutputStream;

public class Sha256Test {
    public void testKnownVector() throws Exception {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.ofBytes("abc".getBytes("UTF-8")));
    }

    public void testFileMatchesBytes() throws Exception {
        File f = File.createTempFile("nxsha", ".bin");
        try {
            byte[] data = new byte[200000];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31);
            FileOutputStream o = new FileOutputStream(f);
            o.write(data);
            o.close();
            assertEquals(Sha256.ofBytes(data), Sha256.ofFile(f));
        } finally {
            f.delete();
        }
    }

    public void testIsHex64() {
        assertTrue(Sha256.isHex64("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        assertFalse(Sha256.isHex64("BA7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        assertFalse(Sha256.isHex64("abc"));
        assertFalse(Sha256.isHex64(null));
    }
}

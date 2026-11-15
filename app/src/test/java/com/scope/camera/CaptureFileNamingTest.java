package com.scope.camera;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for CaptureFileNaming sanitization, naming patterns, collision prevention,
 * folder organization subpaths, and JPEG quality mapping.
 */
public class CaptureFileNamingTest {

    @Test
    public void testSanitizePrefixHandlesBlankSymbolsAndLength() {
        assertEquals("LIKA", CaptureFileNaming.sanitizePrefix(null));
        assertEquals("LIKA", CaptureFileNaming.sanitizePrefix("   "));
        assertEquals("LIKA", CaptureFileNaming.sanitizePrefix("///..@@"));
        assertEquals("My_Roll-01", CaptureFileNaming.sanitizePrefix("  My Roll-01!  "));
        assertEquals("ABC", CaptureFileNaming.sanitizePrefix("__ABC--"));
        String longPrefix = CaptureFileNaming.sanitizePrefix("ABCDEFGHIJKLMNOPQRSTUVWXYZ123456");
        assertTrue(longPrefix.length() <= 24);
    }

    @Test
    public void testResolvePrefixSupportsStandardAndCustom() {
        assertEquals("LIKA", CaptureFileNaming.resolvePrefix("LIKA", "CUSTOM_X"));
        assertEquals("IMG", CaptureFileNaming.resolvePrefix("IMG", "CUSTOM_X"));
        assertEquals("Portrait_01", CaptureFileNaming.resolvePrefix("Custom...", " Portrait 01 "));
        assertEquals("LIKA", CaptureFileNaming.resolvePrefix("CUSTOM", "   "));
    }

    @Test
    public void testBuildStillBaseStemPatterns() {
        assertEquals("LIKA_20261115_120000_001",
                CaptureFileNaming.buildStillBaseStem("LIKA", "Prefix + Timestamp", "KODACHROME_25", "20261115_120000_001", 7));
        assertEquals("LIKA_KODACHROME25_20261115_120000_001",
                CaptureFileNaming.buildStillBaseStem("LIKA", "Prefix + Film Look + Timestamp", "KODACHROME 25", "20261115_120000_001", 7));
        assertEquals("LIKA_ROLL_0007",
                CaptureFileNaming.buildStillBaseStem("LIKA", "Roll counter (LIKA_ROLL_0001)", "KODACHROME", "20261115_120000_001", 7));
    }

    @Test
    public void testEnsureUniqueStemPreventsCollisions() {
        Set<String> existing = new HashSet<>();
        existing.add("TEST_UNIQUE_STEM_BASE");
        existing.add("TEST_UNIQUE_STEM_BASE_2");

        String first = CaptureFileNaming.ensureUniqueStem("TEST_UNIQUE_STEM_BASE", existing::contains);
        assertEquals("TEST_UNIQUE_STEM_BASE_3", first);

        // Calling again with the exact same candidate must avoid the recently issued stem too.
        String second = CaptureFileNaming.ensureUniqueStem("TEST_UNIQUE_STEM_BASE", existing::contains);
        assertEquals("TEST_UNIQUE_STEM_BASE_4", second);
        assertNotEquals(first, second);
    }

    @Test
    public void testBuildFolderSubpath() {
        assertEquals("", CaptureFileNaming.buildFolderSubpath("Single folder (Pictures/Lika Lens)", "FUJI ETERNA", "2026-11"));
        assertEquals("2026-11", CaptureFileNaming.buildFolderSubpath("Subfolders by month (2026-11)", "FUJI ETERNA", "2026-11"));
        assertEquals("FUJI_ETERNA", CaptureFileNaming.buildFolderSubpath("Subfolders by film look", "FUJI ETERNA", "2026-11"));
        assertEquals("LIKA_FILM", CaptureFileNaming.buildFolderSubpath("Subfolders by film look", "///", "2026-11"));
    }

    @Test
    public void testResolveJpegQuality() {
        assertEquals(98, CaptureFileNaming.resolveJpegQuality("Maximum · 98%"));
        assertEquals(95, CaptureFileNaming.resolveJpegQuality("High · 95% (Recommended)"));
        assertEquals(90, CaptureFileNaming.resolveJpegQuality("Balanced · 90%"));
        assertEquals(85, CaptureFileNaming.resolveJpegQuality("Compact · 85%"));
        assertEquals(95, CaptureFileNaming.resolveJpegQuality(null));
    }
}

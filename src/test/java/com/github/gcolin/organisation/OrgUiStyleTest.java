package com.github.gcolin.organisation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OrgUiStyleTest {

    @Test
    void sanitizeColorAcceptsHexAndFallsBack() {
        assertEquals("#7f553d", OrgUiStyle.sanitizeColor(null, OrgUiStyle.DEFAULT_PRIMARY));
        assertEquals("#7f553d", OrgUiStyle.sanitizeColor("not-a-color", OrgUiStyle.DEFAULT_PRIMARY));
        assertEquals("#112233", OrgUiStyle.sanitizeColor("#112233", OrgUiStyle.DEFAULT_PRIMARY));
        assertEquals("#aabbcc", OrgUiStyle.sanitizeColor("AABBCC", OrgUiStyle.DEFAULT_PRIMARY));
        assertEquals("#abcdef", OrgUiStyle.sanitizeColor("#AbCdEf", OrgUiStyle.DEFAULT_PRIMARY));
    }

    @Test
    void sanitizeFontIdKeepsKnownValuesOnly() {
        assertEquals("", OrgUiStyle.sanitizeFontId(null));
        assertEquals("", OrgUiStyle.sanitizeFontId("comic-sans"));
        assertEquals("inter", OrgUiStyle.sanitizeFontId("Inter"));
        assertEquals("open-sans", OrgUiStyle.sanitizeFontId("open-sans"));
    }

    @Test
    void resolveAppliesDefaultsAndGoogleFont() {
        OrgUiStyle.Resolved defaults = OrgUiStyle.resolve(key -> null);
        assertEquals(OrgUiStyle.DEFAULT_PRIMARY, defaults.primaryColor());
        assertEquals(OrgUiStyle.DEFAULT_SECONDARY, defaults.secondaryColor());
        assertEquals("127, 85, 61", defaults.primaryRgb());
        assertNull(defaults.fontFamily());
        assertNull(defaults.googleFontHref());

        Map<String, String> values = Map.of(
                OrgUiStyle.KEY_PRIMARY, "#336699",
                OrgUiStyle.KEY_SECONDARY, "#ffeeaa",
                OrgUiStyle.KEY_FONT, "inter");
        OrgUiStyle.Resolved custom = OrgUiStyle.resolve(values::get);
        assertEquals("#336699", custom.primaryColor());
        assertEquals("51, 102, 153", custom.primaryRgb());
        assertEquals("#2a547d", custom.primaryHover());
        assertEquals("inter", custom.fontId());
        assertTrue(custom.fontFamily().contains("Inter"));
        assertTrue(custom.googleFontHref().contains("Inter"));
    }

    @Test
    void shadeDarkensChannels() {
        assertEquals("#804020", OrgUiStyle.shade("#ff8040", 0.5));
    }
}

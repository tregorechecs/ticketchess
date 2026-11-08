package com.github.gcolin.organisation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Simple per-organisation UI colours and font (stored under {@code ui.*} in settings_json). */
public final class OrgUiStyle {

    public static final String KEY_PRIMARY = "ui.primaryColor";
    public static final String KEY_SECONDARY = "ui.secondaryColor";
    public static final String KEY_FONT = "ui.font";

    /** Matches {@code theme.css} Bootstrap primary. */
    public static final String DEFAULT_PRIMARY = "#7f553d";
    /** Matches {@code theme.css} Bootstrap secondary. */
    public static final String DEFAULT_SECONDARY = "#E8D8C3";

    private static final Pattern HEX = Pattern.compile("^#([0-9A-Fa-f]{6})$");

    public record FontOption(String id, String label, String cssFamily, String googleFamily) {
        boolean isGoogle() {
            return googleFamily != null && !googleFamily.isBlank();
        }
    }

    public record Resolved(
            String primaryColor,
            String secondaryColor,
            String primaryRgb,
            String secondaryRgb,
            String primaryHover,
            String secondaryHover,
            String fontId,
            String fontFamily,
            String googleFontHref) {}

    private static final List<FontOption> FONTS = List.of(
            new FontOption("", "Default", null, null),
            new FontOption("system", "System", "system-ui, -apple-system, Segoe UI, Roboto, sans-serif", null),
            new FontOption("georgia", "Georgia", "Georgia, 'Times New Roman', serif", null),
            new FontOption("inter", "Inter", "'Inter', system-ui, sans-serif", "Inter"),
            new FontOption("open-sans", "Open Sans", "'Open Sans', system-ui, sans-serif", "Open+Sans"),
            new FontOption("source-sans-3", "Source Sans 3", "'Source Sans 3', system-ui, sans-serif", "Source+Sans+3"),
            new FontOption("nunito", "Nunito", "'Nunito', system-ui, sans-serif", "Nunito"),
            new FontOption("lora", "Lora", "'Lora', Georgia, serif", "Lora"),
            new FontOption("playfair", "Playfair Display", "'Playfair Display', Georgia, serif", "Playfair+Display"));

    private OrgUiStyle() {}

    public static List<FontOption> fonts() {
        return FONTS;
    }

    public static Resolved resolve(Function<String, String> properties) {
        String primary = sanitizeColor(properties.apply(KEY_PRIMARY), DEFAULT_PRIMARY);
        String secondary = sanitizeColor(properties.apply(KEY_SECONDARY), DEFAULT_SECONDARY);
        FontOption font = fontById(sanitizeFontId(properties.apply(KEY_FONT))).orElse(FONTS.get(0));
        String googleHref = null;
        if (font.isGoogle()) {
            googleHref = "https://fonts.googleapis.com/css2?family="
                    + font.googleFamily()
                    + ":wght@400;600;700&display=swap";
        }
        return new Resolved(
                primary,
                secondary,
                toRgb(primary),
                toRgb(secondary),
                shade(primary, 0.82),
                shade(secondary, 0.9),
                font.id(),
                font.cssFamily(),
                googleHref);
    }

    public static String sanitizeColor(String value, String defaultColor) {
        if (value == null) {
            return defaultColor;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return defaultColor;
        }
        if (!trimmed.startsWith("#") && trimmed.matches("[0-9A-Fa-f]{6}")) {
            trimmed = "#" + trimmed;
        }
        if (HEX.matcher(trimmed).matches()) {
            return "#" + trimmed.substring(1).toLowerCase(Locale.ROOT);
        }
        return defaultColor;
    }

    public static String sanitizeFontId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String id = value.trim().toLowerCase(Locale.ROOT);
        return fontById(id).map(FontOption::id).orElse("");
    }

    public static String toRgb(String hex) {
        String color = sanitizeColor(hex, DEFAULT_PRIMARY);
        int[] rgb = rgbComponents(color);
        return rgb[0] + ", " + rgb[1] + ", " + rgb[2];
    }

    /** Multiplies each RGB channel by {@code factor} (e.g. 0.82 ≈ hover darkening). */
    public static String shade(String hex, double factor) {
        String color = sanitizeColor(hex, DEFAULT_PRIMARY);
        double f = Math.max(0, Math.min(1, factor));
        int[] rgb = rgbComponents(color);
        return String.format(
                Locale.ROOT,
                "#%02x%02x%02x",
                (int) Math.round(rgb[0] * f),
                (int) Math.round(rgb[1] * f),
                (int) Math.round(rgb[2] * f));
    }

    private static int[] rgbComponents(String hex) {
        return new int[] {
            Integer.parseInt(hex.substring(1, 3), 16),
            Integer.parseInt(hex.substring(3, 5), 16),
            Integer.parseInt(hex.substring(5, 7), 16)
        };
    }

    public static Optional<FontOption> fontById(String id) {
        String needle = id == null ? "" : id;
        for (FontOption font : FONTS) {
            if (font.id().equals(needle)) {
                return Optional.of(font);
            }
        }
        return Optional.empty();
    }
}

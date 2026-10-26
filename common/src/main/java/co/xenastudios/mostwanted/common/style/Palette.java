package co.xenastudios.mostwanted.common.style;

/**
 * The only colours in the game: 24 hues, 15° apart, each {@code hsl(h, 100%, 60%)},
 * plus white and grey for plain text. See docs/09-brand.md.
 */
public final class Palette {

    public static final int WHITE = 0xffffff;
    /** Minecraft's {@code <gray>}. */
    public static final int GREY = 0xaaaaaa;

    public static final int BRAND = hue(30);
    public static final int INMATE = hue(30);
    public static final int CRIMINAL = hue(0);
    public static final int GUARD = hue(210);
    public static final int SUCCESS = hue(120);
    public static final int MONEY = hue(60);
    public static final int INFO = hue(180);
    public static final int STAFF = hue(300);
    public static final int ERROR = hue(0);

    private Palette() {}

    /** The palette colour at {@code degrees}, which must be a multiple of 15 from 0 to 345. */
    public static int hue(int degrees) {
        if (degrees < 0 || degrees >= 360 || degrees % 15 != 0) {
            throw new IllegalArgumentException("Not a palette hue: " + degrees);
        }
        // hsl(h, 100%, 60%): the strongest channel is 255, the weakest 51.
        double chroma = 0.8;
        double low = 0.2;
        double sector = degrees / 60.0;
        double x = chroma * (1 - Math.abs(sector % 2 - 1));
        double r;
        double g;
        double b;
        switch ((int) sector) {
            case 0 -> { r = chroma; g = x; b = 0; }
            case 1 -> { r = x; g = chroma; b = 0; }
            case 2 -> { r = 0; g = chroma; b = x; }
            case 3 -> { r = 0; g = x; b = chroma; }
            case 4 -> { r = x; g = 0; b = chroma; }
            default -> { r = chroma; g = 0; b = x; }
        }
        return channel(r + low) << 16 | channel(g + low) << 8 | channel(b + low);
    }

    /** {@code #ff9933}, for MiniMessage, CSS and Discord. */
    public static String hex(int rgb) {
        return String.format("#%06x", rgb);
    }

    private static int channel(double value) {
        return (int) Math.round(value * 255);
    }
}

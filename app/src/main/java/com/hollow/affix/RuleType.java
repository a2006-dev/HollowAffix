package com.hollow.affix;

public final class RuleType {

    public static final String DRIVE = "drive";

    public static final String CASSETTE = "cassette";

    public static final String BOTH = "both";

    private RuleType() {}

    public static String normalize(String raw) {
        if (raw == null) return BOTH;
        String s = raw.trim().toLowerCase(java.util.Locale.US);
        if (s.equals("drive") || s.equals("驱动块") || s.equals("块")) return DRIVE;
        if (s.equals("cassette") || s.equals("空幕") || s.equals("幕")) return CASSETTE;
        return BOTH;
    }

    public static String label(String type) {
        String t = normalize(type);
        if (DRIVE.equals(t)) return "驱动块";
        if (CASSETTE.equals(t)) return "空幕";
        return "两者";
    }

    public static boolean needDrive(String type) {
        String t = normalize(type);
        return DRIVE.equals(t) || BOTH.equals(t);
    }

    public static boolean needCassette(String type) {
        String t = normalize(type);
        return CASSETTE.equals(t) || BOTH.equals(t);
    }
}

package com.advancedlogin.adapter.in.web.qr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Chrome 152" / "Windows" for the approval screen; deliberately tiny, not a full UA parser.
 * Order matters: Edge and Opera also say "Chrome", and Chrome also says "Safari".
 */
record UserAgentSummary(String browser, String os) {

    private static final Pattern[] BROWSERS = {
            Pattern.compile("(Edg)/(\\d+)"),
            Pattern.compile("(OPR)/(\\d+)"),
            Pattern.compile("(Firefox)/(\\d+)"),
            Pattern.compile("(Chrome)/(\\d+)"),
            Pattern.compile("Version/(\\d+).*(Safari)/"),
    };

    static UserAgentSummary parse(String userAgent) {
        String ua = userAgent == null ? "" : userAgent;
        return new UserAgentSummary(browser(ua), os(ua));
    }

    private static String browser(String ua) {
        for (Pattern pattern : BROWSERS) {
            Matcher m = pattern.matcher(ua);
            if (m.find()) {
                return switch (m.group(1)) {
                    case "Edg" -> "Edge " + m.group(2);
                    case "OPR" -> "Opera " + m.group(2);
                    default -> m.group(2).equals("Safari") ? "Safari " + m.group(1) : m.group(1) + " " + m.group(2);
                };
            }
        }
        return "Unknown browser";
    }

    private static String os(String ua) {
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("CrOS")) return "ChromeOS";
        if (ua.contains("Linux")) return "Linux";
        return "Unknown OS";
    }
}

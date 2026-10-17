package com.advancedlogin.adapter.in.web.qr;

import java.net.URI;
import java.util.Locale;

/**
 * The URL inside the QR, {@code <base>/m/approve/<qrId>}. Base: the configured
 * {@code app.qr.approve-base-url}, else the desktop's Origin (an address the phone on the same
 * Wi-Fi can reach), else localhost. Trusting Origin is fine: it only shapes the link shown back to
 * the same browser, so a client faking it only fools itself.
 */
final class ApproveLinks {

    static final String FALLBACK_BASE = "http://localhost:5173";

    private ApproveLinks() {
    }

    static String forQr(String qrId, String configuredBase, String originHeader) {
        return base(configuredBase, originHeader) + "/m/approve/" + qrId;
    }

    private static String base(String configuredBase, String originHeader) {
        if (configuredBase != null && !configuredBase.isBlank()) {
            return stripTrailingSlash(configuredBase.trim());
        }
        String origin = sanitizeOrigin(originHeader);
        return origin != null ? origin : FALLBACK_BASE;
    }

    /** Accepts only a bare http(s) origin ("scheme://host[:port]"); anything else is ignored. */
    private static String sanitizeOrigin(String originHeader) {
        if (originHeader == null || originHeader.isBlank() || "null".equals(originHeader)) {
            return null;
        }
        try {
            URI uri = URI.create(originHeader.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean bareOrigin = (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null && bareOrigin) {
                return scheme + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
            }
        } catch (IllegalArgumentException ignored) {
            // malformed header
        }
        return null;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

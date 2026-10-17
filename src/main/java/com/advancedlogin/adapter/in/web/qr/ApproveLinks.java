package com.advancedlogin.adapter.in.web.qr;

import java.net.URI;
import java.util.Locale;

/**
 * Builds the URL encoded in the QR: {@code <base>/m/approve/<qrId>}. The phone must be able to
 * open it, so the base matters.
 *
 * <ol>
 *   <li>{@code app.qr.approve-base-url} if configured (production: the public https URL);</li>
 *   <li>otherwise the desktop request's {@code Origin}. If the desktop opened the app at
 *       https://192.168.1.5:5173, that's exactly an address a phone on the same Wi-Fi can reach;</li>
 *   <li>otherwise (no browser Origin, e.g. curl or tests) {@code http://localhost:5173}.</li>
 * </ol>
 *
 * <p>Trusting Origin here is fine: it only shapes the link shown back to the same browser, for
 * that browser's own QR session. A client faking it only fools itself.
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
        } catch (IllegalArgumentException e) {
            // malformed header: ignore
        }
        return null;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

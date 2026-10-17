package com.advancedlogin.domain.qr;

import java.util.Objects;

/**
 * The desktop browser asking to be logged in. Shown on the phone before approval, so the user
 * can spot a QR that was relayed from somebody else's machine (QRLjacking defence).
 *
 * @param approximateLocation e.g. "Jakarta, Indonesia" or "Local network"; null if unknown
 */
public record RequestingDevice(String ipAddress, String userAgent, String approximateLocation) {

    private static final int MAX_USER_AGENT_LENGTH = 512;

    public RequestingDevice {
        Objects.requireNonNull(ipAddress, "ipAddress");
        userAgent = userAgent == null ? "" : truncate(userAgent);
        approximateLocation = approximateLocation == null || approximateLocation.isBlank() ? null : approximateLocation;
    }

    /** As seen by the web layer, before the location is looked up. */
    public RequestingDevice(String ipAddress, String userAgent) {
        this(ipAddress, userAgent, null);
    }

    public RequestingDevice withLocation(String location) {
        return new RequestingDevice(ipAddress, userAgent, location);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_USER_AGENT_LENGTH ? value : value.substring(0, MAX_USER_AGENT_LENGTH);
    }
}

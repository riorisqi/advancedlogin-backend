package com.advancedlogin.domain.qr;

import java.util.Objects;

/** The desktop asking to log in; shown on the phone before approval (QRLjacking defence). */
public record RequestingDevice(String ipAddress, String userAgent, String approximateLocation) {

    private static final int MAX_USER_AGENT_LENGTH = 512;

    public RequestingDevice {
        Objects.requireNonNull(ipAddress, "ipAddress");
        userAgent = userAgent == null ? "" : truncate(userAgent);
        approximateLocation = approximateLocation == null || approximateLocation.isBlank() ? null : approximateLocation;
    }

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

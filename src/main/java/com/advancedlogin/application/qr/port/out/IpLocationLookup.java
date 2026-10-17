package com.advancedlogin.application.qr.port.out;

import java.util.Optional;

/**
 * Turns an IP address into a rough, human-readable place ("Jakarta, Indonesia"). Best effort:
 * IP geolocation is approximate (VPNs, mobile carriers), so it's a hint for the user, never a
 * security decision.
 */
public interface IpLocationLookup {

    Optional<String> approximateLocation(String ipAddress);
}

package com.advancedlogin.application.qr.port.out;

import java.util.Optional;

/** Approximate by nature (VPNs, carriers): a hint for the user, never a security decision. */
public interface IpLocationLookup {

    Optional<String> approximateLocation(String ipAddress);
}

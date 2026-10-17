package com.advancedlogin.adapter.out.geo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class GeoIpLocationLookupTest {

    private final GeoIpLocationLookup lookup = new GeoIpLocationLookup("");

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "0:0:0:0:0:0:0:1", "192.168.1.10", "10.1.2.3", "172.20.0.5",
            "169.254.1.1", "fd12:3456:789a::1", "fe80::1"})
    void privateAndLoopbackAddressesAreTheLocalNetwork(String ip) {
        assertThat(lookup.approximateLocation(ip)).contains("Local network");
    }

    @Test
    void publicAddressWithoutDatabaseHasNoLocation() {
        assertThat(lookup.approximateLocation("203.0.113.7")).isEmpty();
    }

    @Test
    void neverResolvesHostnames() {
        // Must not trigger a DNS lookup.
        assertThat(lookup.approximateLocation("example.com")).isEmpty();
        assertThat(lookup.approximateLocation(null)).isEmpty();
    }
}

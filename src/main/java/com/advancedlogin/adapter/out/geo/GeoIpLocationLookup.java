package com.advancedlogin.adapter.out.geo;

import com.advancedlogin.application.qr.port.out.IpLocationLookup;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Offline lookup in a MaxMind-format {@code .mmdb} (optional), so no IP is sent to a third party.
 * Private/loopback addresses are always "Local network".
 */
@Component
class GeoIpLocationLookup implements IpLocationLookup {

    static final String LOCAL_NETWORK = "Local network";

    private static final Logger log = LoggerFactory.getLogger(GeoIpLocationLookup.class);

    // Only IP literals: InetAddress.getByName on anything else would trigger a DNS lookup.
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.%]+");

    private final DatabaseReader reader;

    GeoIpLocationLookup(@Value("${app.geoip.database:}") String databasePath) {
        this.reader = open(databasePath);
    }

    @Override
    public Optional<String> approximateLocation(String ipAddress) {
        InetAddress address = parse(ipAddress);
        if (address == null) {
            return Optional.empty();
        }
        if (isLocal(address)) {
            return Optional.of(LOCAL_NETWORK);
        }
        if (reader == null) {
            return Optional.empty();
        }
        try {
            return reader.tryCity(address).flatMap(GeoIpLocationLookup::format);
        } catch (IOException | com.maxmind.geoip2.exception.GeoIp2Exception | RuntimeException e) {
            log.debug("GeoIP lookup failed for an address", e);
            return Optional.empty();
        }
    }

    private static Optional<String> format(CityResponse response) {
        StringJoiner place = new StringJoiner(", ");
        String city = response.city() == null ? null : response.city().getName();
        String country = response.country() == null ? null : response.country().getName();
        if (city != null) place.add(city);
        if (country != null) place.add(country);
        return place.length() == 0 ? Optional.empty() : Optional.of(place.toString());
    }

    private static boolean isLocal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()) {
            return true;
        }
        // IPv6 unique local (fc00::/7); isSiteLocalAddress only knows the deprecated fec0::/10.
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC;
    }

    private static InetAddress parse(String ipAddress) {
        if (ipAddress == null || !IP_LITERAL.matcher(ipAddress).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(ipAddress);
        } catch (IOException e) {
            return null;
        }
    }

    private static DatabaseReader open(String databasePath) {
        if (databasePath == null || databasePath.isBlank()) {
            log.info("GeoIP database not configured (app.geoip.database): public IPs will show no location");
            return null;
        }
        File file = new File(databasePath);
        try {
            DatabaseReader reader = new DatabaseReader.Builder(file).build();
            log.info("GeoIP database loaded: {}", file.getName());
            return reader;
        } catch (IOException e) {
            log.warn("Could not open GeoIP database {}: public IPs will show no location", file, e);
            return null;
        }
    }

    @PreDestroy
    void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
    }
}

package com.advancedlogin.adapter.in.web.qr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ApproveLinksTest {

    private static final String ID = "q".repeat(43);

    @Test
    void configuredBaseWins() {
        assertThat(ApproveLinks.forQr(ID, "https://login.example.com/", "https://192.168.1.5:5173"))
                .isEqualTo("https://login.example.com/m/approve/" + ID);
    }

    @Test
    void otherwiseUsesTheDesktopsOrigin() {
        assertThat(ApproveLinks.forQr(ID, "", "https://192.168.1.5:5173"))
                .isEqualTo("https://192.168.1.5:5173/m/approve/" + ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "javascript:alert(1)", "https://evil.example/path", "ftp://x", "not a url"})
    void ignoresMissingOrWeirdOrigins(String origin) {
        assertThat(ApproveLinks.forQr(ID, null, origin)).isEqualTo("http://localhost:5173/m/approve/" + ID);
    }
}

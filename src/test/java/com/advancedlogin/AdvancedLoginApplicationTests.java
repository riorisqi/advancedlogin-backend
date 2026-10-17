package com.advancedlogin;

import com.advancedlogin.application.auth.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AdvancedLoginApplicationTests {

    @Autowired
    UserRepository users;

    @Test
    void contextLoads() {
    }

    @Test
    void testAccountsExistOnlyInTheDevProfile() {
        assertThat(users.findByUsername("alice")).isEmpty();
        assertThat(users.findByUsername("bob")).isEmpty();
    }
}

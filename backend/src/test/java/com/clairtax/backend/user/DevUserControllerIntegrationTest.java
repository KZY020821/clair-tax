package com.clairtax.backend.user;

import com.clairtax.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Value("${clair.dev-user.email}")
    private String devUserEmail;

    @Test
    void getCurrentDevUserReturnsBootstrappedUser() throws Exception {
        appUserRepository.findByEmail(devUserEmail)
                .orElseThrow(() -> new AssertionError("Expected bootstrapped dev user to exist"));

        mockMvc.perform(get("/api/dev/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(devUserEmail)))
                .andExpect(jsonPath("$.mode", is("dev")))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }
}

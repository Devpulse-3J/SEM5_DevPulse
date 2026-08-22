package com.devpulse.auth.controller;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.LoginRequest;
import com.devpulse.auth.dto.RegisterRequest;
import com.devpulse.auth.dto.UserProfileResponse;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.DuplicateEmailException;
import com.devpulse.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for {@link AuthController}.
 *
 * <p>Uses {@code @SpringBootTest} + {@code @AutoConfigureMockMvc} rather than a
 * {@code @WebMvcTest} slice, because the real {@code SecurityConfig} filter
 * chain must be in play — the point of several of these cases is to prove that
 * the production security rules behave correctly. The production config is
 * never modified or disabled; only {@link AuthService} is mocked, so no
 * database rows are required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private static AuthResponse sampleAuth() {
        return new AuthResponse("test.jwt.token", 3600L, 1,
                "dev@demo.devpulse", "Demo Dev", "member");
    }

    private static User principal() {
        User user = new User();
        user.setUserId(1);
        user.setEmail("dev@demo.devpulse");
        user.setFullName("Demo Dev");
        user.setPasswordHash("irrelevant-for-this-test");
        user.setSystemRole("member");
        return user;
    }

    // ---- POST /auth/register ------------------------------------------------

    @Test
    void registerReturns201AndToken() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(sampleAuth());

        RegisterRequest request =
                new RegisterRequest("dev@demo.devpulse", "password123", "Demo Dev", 1);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("test.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void registerRejectsMalformedEmail() throws Exception {
        RegisterRequest request =
                new RegisterRequest("not-an-email", "password123", "Demo Dev", 1);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        // RegisterRequest requires at least 8 characters.
        RegisterRequest request =
                new RegisterRequest("dev@demo.devpulse", "short", "Demo Dev", 1);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReturns409WhenEmailAlreadyExists() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateEmailException("dev@demo.devpulse"));

        RegisterRequest request =
                new RegisterRequest("dev@demo.devpulse", "password123", "Demo Dev", 1);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ---- POST /auth/login ---------------------------------------------------

    @Test
    void loginReturns200AndToken() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuth());

        LoginRequest request = new LoginRequest("dev@demo.devpulse", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test.jwt.token"))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void loginReturns401OnBadCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("bad"));

        LoginRequest request = new LoginRequest("dev@demo.devpulse", "wrong-password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /auth/me (secured) --------------------------------------------

    @Test
    void meIsRejectedWithoutAuthentication() throws Exception {
        // SecurityConfig permits only /auth/register, /auth/login and the two
        // actuator endpoints; everything else requires a valid JWT.
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void meReturnsProfileForAuthenticatedUser() throws Exception {
        UserProfileResponse profile = new UserProfileResponse();
        profile.setUserId(1);
        profile.setEmail("dev@demo.devpulse");
        profile.setFullName("Demo Dev");
        profile.setSystemRole("member");
        when(authService.getUserProfile(anyInt())).thenReturn(profile);

        // The controller reads @AuthenticationPrincipal User, so the principal
        // must be the real entity — User implements UserDetails.
        mockMvc.perform(get("/auth/me").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dev@demo.devpulse"))
                .andExpect(jsonPath("$.systemRole").value("member"));
    }
}

package com.devpulse.auth.controller;

import com.devpulse.auth.dto.GithubPreviewResponse;
import com.devpulse.auth.dto.LinkGithubResponse;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.service.GithubIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GithubIdentityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GithubIdentityService githubIdentityService;

    private static User principal() {
        User user = new User();
        user.setUserId(24);
        user.setEmail("umaya@example.com");
        user.setPasswordHash("irrelevant");
        user.setSystemRole("member");
        return user;
    }

    @Test
    void linkReturnsTheLinkedAccount() throws Exception {
        when(githubIdentityService.link(24, "UmayaJayasuriya"))
                .thenReturn(new LinkGithubResponse(194699006L, "UmayaJayasuriya"));

        mockMvc.perform(put("/auth/me/github").with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUsername\":\"UmayaJayasuriya\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubId").value(194699006))
                .andExpect(jsonPath("$.githubLogin").value("UmayaJayasuriya"));
    }

    @Test
    void anInvalidUsernameIsRejectedBeforeAnyLookup() throws Exception {
        mockMvc.perform(put("/auth/me/github").with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUsername\":\"-bad name!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void linkRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/auth/me/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUsername\":\"octocat\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void lookupShowsTheAccountForConfirmation() throws Exception {
        when(githubIdentityService.preview(24, "ChanulPathirana")).thenReturn(new GithubPreviewResponse(
                149255216L, "ChanulPathirana", "Pathirana D.P.C.N.",
                "https://avatars.githubusercontent.com/u/149255216", "https://github.com/ChanulPathirana", false));

        mockMvc.perform(get("/auth/me/github/lookup").param("username", "ChanulPathirana").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubId").value(149255216))
                .andExpect(jsonPath("$.name").value("Pathirana D.P.C.N."))
                .andExpect(jsonPath("$.linkedToAnotherUser").value(false));
    }

    @Test
    void lookupOfAnInvalidUsernameIsA400NotA500() throws Exception {
        mockMvc.perform(get("/auth/me/github/lookup").param("username", "-bad name!").with(user(principal())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookupRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/auth/me/github/lookup").param("username", "octocat"))
                .andExpect(status().is4xxClientError());
    }
}

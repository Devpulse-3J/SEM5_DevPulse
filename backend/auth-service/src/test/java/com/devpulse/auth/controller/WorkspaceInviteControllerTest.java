package com.devpulse.auth.controller;

import com.devpulse.auth.dto.*;
import com.devpulse.auth.entity.*;
import com.devpulse.auth.service.WorkspaceInviteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WorkspaceInviteControllerTest {

    private WorkspaceInviteService workspaceInviteService;
    private WorkspaceInviteController controller;

    private User adminUser;

    @BeforeEach
    public void setUp() {
        workspaceInviteService = mock(WorkspaceInviteService.class);
        controller = new WorkspaceInviteController(workspaceInviteService);

        Company company = new Company();
        company.setCompanyId(1);

        adminUser = new User();
        adminUser.setUserId(10);
        adminUser.setEmail("admin@devpulse.com");
        adminUser.setCompany(company);
        adminUser.setSystemRole("admin");
    }

    @Test
    public void testInviteToOrganizationEndpoint() {
        OrganizationInviteRequest req = new OrganizationInviteRequest(1, "new@devpulse.com", "member");
        OrganizationInvitation invitation = new OrganizationInvitation();
        invitation.setInvitationId(1);
        invitation.setEmail("new@devpulse.com");

        when(workspaceInviteService.inviteToOrganization(eq(adminUser), any(OrganizationInviteRequest.class)))
                .thenReturn(invitation);

        ResponseEntity<OrganizationInvitation> response = controller.inviteToOrganization(adminUser, req);

        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("new@devpulse.com", response.getBody().getEmail());
    }

    @Test
    public void testAcceptOrganizationInviteEndpoint() {
        User updatedUser = new User();
        Company company = new Company();
        company.setCompanyId(1);
        updatedUser.setCompany(company);

        when(workspaceInviteService.acceptOrganizationInvite(eq("token-123"), any(User.class)))
                .thenReturn(updatedUser);

        ResponseEntity<Map<String, Object>> response = controller.acceptOrganizationInvite(adminUser, "token-123");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("success", response.getBody().get("status"));
        assertEquals(1, response.getBody().get("companyId"));
    }

    @Test
    public void testGetPendingJoinRequestsEndpoint() {
        WorkspaceJoinRequest req1 = new WorkspaceJoinRequest();
        when(workspaceInviteService.getPendingJoinRequests(adminUser, 1)).thenReturn(List.of(req1));

        ResponseEntity<List<WorkspaceJoinRequest>> response = controller.getPendingJoinRequests(adminUser, 1);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }
}

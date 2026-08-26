package com.devpulse.metrics.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devpulse.metrics.exception.ApiException;
import com.devpulse.metrics.repository.ProjectScopeRepository;
import com.devpulse.metrics.repository.ProjectScopeRepository.ProjectScope;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock
    private ProjectScopeRepository repository;
    private ProjectAccessService service;

    @BeforeEach
    void setUp() {
        service = new ProjectAccessService(repository);
    }

    @Test
    void companyAdminCanViewWithoutProjectMembership() {
        RequestContext context = new RequestContext(3, 2);
        ProjectScope scope = new ProjectScope(8, 2, "payments", 1);
        when(repository.findProject(2, 8)).thenReturn(Optional.of(scope));
        when(repository.findSystemRole(2, 3)).thenReturn(Optional.of("admin"));

        assertThat(service.requireViewAccess(context, 8)).isEqualTo(scope);
        verify(repository, never()).isProjectMember(8, 3);
    }

    @Test
    void nonMemberCannotReadAnotherProject() {
        RequestContext context = new RequestContext(3, 2);
        when(repository.findProject(2, 8))
                .thenReturn(Optional.of(new ProjectScope(8, 2, "payments", 1)));
        when(repository.findSystemRole(2, 3)).thenReturn(Optional.of("member"));
        when(repository.isProjectMember(8, 3)).thenReturn(false);

        assertThatThrownBy(() -> service.requireViewAccess(context, 8))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not a member");
    }
}

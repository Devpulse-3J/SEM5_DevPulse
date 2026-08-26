package com.devpulse.metrics.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpulse.metrics.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestContextResolverTest {

    private final RequestContextResolver resolver = new RequestContextResolver();

    @Test
    void resolvesHeadersCurrentlyForwardedByTheGateway() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "12");
        request.addHeader("X-Company-Id", "7");

        assertThat(resolver.resolve(request)).isEqualTo(new RequestContext(12, 7));
    }

    @Test
    void alsoAcceptsTheDocumentedCompatibilityHeaderNames() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DevPulse-User-Id", "12");
        request.addHeader("X-DevPulse-Company-Id", "7");

        assertThat(resolver.resolve(request)).isEqualTo(new RequestContext(12, 7));
    }

    @Test
    void rejectsMissingOrNonNumericIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "not-an-id");
        request.addHeader("X-Company-Id", "7");

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid");
    }
}

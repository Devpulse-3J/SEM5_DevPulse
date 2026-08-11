package com.devpulse.integration.jira;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraSignatureValidatorTest {

    private JiraSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JiraSignatureValidator();
        ReflectionTestUtils.setField(validator, "webhookSecret", "test-jira-secret");
    }

    @Test
    void testValidDirectSecretHeader() {
        assertTrue(validator.isValidSignature("{}", "test-jira-secret"));
    }

    @Test
    void testInvalidSecretHeader() {
        assertFalse(validator.isValidSignature("{}", "wrong-secret"));
    }

    @Test
    void testNullOrBlankSignatureHeader() {
        assertFalse(validator.isValidSignature("{}", null));
        assertFalse(validator.isValidSignature("{}", "  "));
    }
}

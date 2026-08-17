package com.ayush.documentqa.service;

import com.ayush.documentqa.ai.AiService;
import com.ayush.documentqa.exception.ModelProviderException;
import com.ayush.documentqa.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests that model provider failures result in clean ModelProviderException,
 * which is mapped to HTTP 503 by the GlobalExceptionHandler.
 */
@ExtendWith(MockitoExtension.class)
class ProviderFailureTest {

    @Mock
    private AiService aiService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void whenProviderFails_throwsModelProviderException() {
        when(aiService.call(anyString(), anyString()))
                .thenThrow(new ModelProviderException("Connection timeout"));

        assertThatThrownBy(() -> aiService.call("system", "user"))
                .isInstanceOf(ModelProviderException.class)
                .hasMessageContaining("timeout");
    }
}

package com.ayush.documentqa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI documentQaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Document Q&A Assistant API")
                        .description("Multi-tenant RAG-based document question-answering system")
                        .version("1.0.0"));
    }

    @Bean
    public OperationCustomizer addTenantHeader() {
        return (operation, method) -> {
            Parameter tenantParam = new Parameter()
                    .in("header")
                    .name("X-Tenant-Id")
                    .description("Tenant identifier (required for all /api/** endpoints)")
                    .required(true)
                    .schema(new io.swagger.v3.oas.models.media.StringSchema());
            operation.addParametersItem(tenantParam);
            return operation;
        };
    }
}

package com.ayush.documentqa.config;

import com.ayush.documentqa.config.AppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AiConfig {

    /**
     * ChatClient is the primary Spring AI abstraction for LLM interaction.
     * The underlying provider (OpenAI, Anthropic, etc.) is determined by
     * which starter is on the classpath — no provider-specific code here.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}

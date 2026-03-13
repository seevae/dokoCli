package com.dokocli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration.class
})
public class DokoCliApplication {

    public static void main(String[] args) {
        // 禁用 Spring Boot 的 banner，使用我们自己的
        SpringApplication app = new SpringApplication(DokoCliApplication.class);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.run(args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

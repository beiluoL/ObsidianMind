package com.obsidianmind.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。访问 /swagger-ui.html 查看全部 API。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI obsidianMindOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ObsidianMind Backend API")
                .description("Local First AI Personal Knowledge Base —— Vault / Notes / Search / Index / Chat / System")
                .version("0.1.0"));
    }
}

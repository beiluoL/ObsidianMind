package com.obsidianmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ObsidianMind Backend 启动类。
 * Local First AI Personal Knowledge Base —— Markdown 文件系统为唯一事实来源。
 */
@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class ObsidianMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObsidianMindApplication.class, args);
    }
}

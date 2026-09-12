package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.net.SocketFactory;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 依赖健康检查：Ollama（HTTP /api/tags）与 Milvus（TCP 探测）。
 * 全部按需探测、短超时——依赖未启动绝不影响主应用运行。
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final AiProperties aiProperties;
    private final MilvusProperties milvusProperties;
    private final com.obsidianmind.repository.VaultRepository vaultRepository;
    private final OllamaProbe ollamaProbe;

    public HealthService(AiProperties aiProperties, MilvusProperties milvusProperties,
                         com.obsidianmind.repository.VaultRepository vaultRepository) {
        this.aiProperties = aiProperties;
        this.milvusProperties = milvusProperties;
        this.vaultRepository = vaultRepository;
        this.ollamaProbe = new OllamaProbe(aiProperties);
    }

    public String checkOllama() {
        try {
            ollamaProbe.probe();
            return "CONNECTED";
        } catch (RuntimeException e) {
            log.info("Ollama 未连接: {}", aiProperties.ollama().baseUrl());
            return "DISCONNECTED";
        }
    }

    public String checkMilvus() {
        try (Socket socket = SocketFactory.getDefault().createSocket()) {
            socket.connect(
                    new InetSocketAddress(milvusProperties.host(), milvusProperties.port()),
                    milvusProperties.timeoutSeconds() * 1000);
            return "CONNECTED";
        } catch (Exception e) {
            log.info("Milvus 未连接: {}:{}", milvusProperties.host(), milvusProperties.port());
            return "DISCONNECTED";
        }
    }

    public String checkVault() {
        return vaultRepository.isConnected() ? "CONNECTED" : "DISCONNECTED";
    }

    /** Ollama HTTP 探测（独立类便于测试替换）。 */
    static class OllamaProbe {
        private final RestClient client;

        OllamaProbe(AiProperties properties) {
            this.client = RestClient.builder()
                    .baseUrl(properties.ollama().baseUrl())
                    .build();
        }

        void probe() {
            client.get().uri("/api/tags").retrieve().toBodilessEntity();
        }
    }
}

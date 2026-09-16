package com.obsidianmind.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Search API 契约测试（Phase 6）：POST /api/v1/search 的 mode 语义、
 * 降级可观察性、配置视图、Debug 门禁与错误信封。
 * 环境无 Ollama/Milvus：KEYWORD 模式全链路可跑；HYBRID/VECTOR 走降级路径（本身即被测行为）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchControllerTest {

    @TempDir
    static Path vaultDir;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    void connectVault() throws Exception {
        Path java = Files.createDirectories(vaultDir.resolve("Java"));
        Files.writeString(java.resolve("HashMap.md"), """
                ---
                title: HashMap
                tags: [Java]
                ---

                # HashMap

                HashMap 底层是数组 + 链表 + 红黑树，负载因子超过 0.75 触发扩容 resize。
                """);
        Files.writeString(java.resolve("ConcurrentHashMap.md"), """
                # ConcurrentHashMap

                ConcurrentHashMap 使用 CAS 与 synchronized 保证线程安全。
                """);
        mockMvc.perform(post("/api/v1/vault/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + vaultDir + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1)
    void keywordModeSearchesWithoutExternalDependencies() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"HashMap 扩容\",\"mode\":\"KEYWORD\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("HashMap 扩容"))
                .andExpect(jsonPath("$.requestedMode").value("KEYWORD"))
                .andExpect(jsonPath("$.effectiveMode").value("KEYWORD"))
                .andExpect(jsonPath("$.fallbacks").isArray())
                .andExpect(jsonPath("$.rerankerStatus").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.sources[0].title").value("HashMap"))
                .andExpect(jsonPath("$.sources[0].path").value("Java/HashMap.md"))
                .andExpect(jsonPath("$.sources[0].heading").exists())
                .andExpect(jsonPath("$.sources[0].snippet").exists())
                .andExpect(jsonPath("$.sources[0].score").isNumber());
    }

    @Test
    @Order(2)
    void defaultModeIsServerConfiguredHybrid() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"HashMap 扩容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedMode").value("HYBRID"))
                // 无 Ollama/Milvus 环境：允许 HYBRID（外部依赖齐全时）或 KEYWORD（降级），不允许其它
                .andExpect(jsonPath("$.effectiveMode").value(
                        org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.is("HYBRID"),
                                org.hamcrest.Matchers.is("KEYWORD"))))
                .andExpect(jsonPath("$.fallbacks").isArray());
    }

    @Test
    @Order(3)
    void invalidModeReturns400Envelope() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"mode\":\"BM25\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @Order(4)
    void blankQueryReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @Order(5)
    void configEndpointExposesReadOnlyRetrievalSettings() throws Exception {
        mockMvc.perform(get("/api/v1/search/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultMode").value("HYBRID"))
                .andExpect(jsonPath("$.rrfK").value(60))
                .andExpect(jsonPath("$.rerankerEnabled").value(false))
                .andExpect(jsonPath("$.debugEnabled").value(false));
    }

    @Test
    @Order(6)
    void debugEndpointIsDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/v1/search/debug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"HashMap 扩容\",\"mode\":\"KEYWORD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @Order(7)
    void exactTechnicalQueryHitsViaKeyword() throws Exception {
        // 类名/机制词精确命中是关键词路的价值场景
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"ConcurrentHashMap CAS\",\"mode\":\"KEYWORD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].path").value("Java/ConcurrentHashMap.md"));
    }
}

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试：验收清单全链路。
 * 关键验收：Ollama / Milvus 未启动时，应用与全部 Vault 功能仍正常（system/status 返回 200）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiControllerIntegrationTest {

    @TempDir
    static Path vaultDir;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    void seedVault() throws Exception {
        Path ai = Files.createDirectories(vaultDir.resolve("AI"));
        Files.writeString(ai.resolve("RAG.md"), """
                ---
                title: RAG 技术原理
                tags: [AI, RAG]
                ---

                # RAG

                RAG 是检索增强生成技术，依赖 [[Embedding]] 与 [[Milvus]]。
                """);
        Files.writeString(ai.resolve("Embedding.md"), """
                # Embedding

                向量嵌入把文本映射为高维向量。
                """);
    }

    @Test
    @Order(1)
    void systemStatusShouldBeUpEvenWhenOllamaAndMilvusDown() throws Exception {
        // 关键验收：Ollama / Milvus 无论在不在，system/status 必须返回 200 且应用 UP
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("ObsidianMind"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.ollama.status").value(
                        org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.is("CONNECTED"),
                                org.hamcrest.Matchers.is("DISCONNECTED"))))
                .andExpect(jsonPath("$.milvus.status").value(
                        org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.is("CONNECTED"),
                                org.hamcrest.Matchers.is("DISCONNECTED"))))
                .andExpect(jsonPath("$.vault.status").value("DISCONNECTED"));
    }

    @Test
    @Order(2)
    void vaultInfoShouldReportDisconnectedBeforeConnect() throws Exception {
        mockMvc.perform(get("/api/v1/vault"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    @Order(3)
    void shouldRejectNonexistentVaultPath() throws Exception {
        mockMvc.perform(post("/api/v1/vault/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/no/such/dir/xyz\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VAULT_NOT_FOUND"));
    }

    @Test
    @Order(3)
    void semanticSearchBlankQueryReturns400EvenBeforeVaultCheck() throws Exception {
        // 参数校验先于 Vault 解析：空白 query 无论连没连 Vault 都是 400
        mockMvc.perform(post("/api/v1/search/semantic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @Order(3)
    void semanticSearchWithoutVaultReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/search/semantic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"HashMap 扩容\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VAULT_NOT_FOUND"));
    }

    @Test
    @Order(4)
    void shouldConnectScanAndListFiles() throws Exception {
        // connect 只校验路径不扫描（noteCount 为 0），扫描由 /scan 触发
        mockMvc.perform(post("/api/v1/vault/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + vaultDir + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true));

        mockMvc.perform(post("/api/v1/vault/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdownFiles").value(2))
                .andExpect(jsonPath("$.folders").value(1));

        mockMvc.perform(get("/api/v1/vault"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.noteCount").value(2));

        mockMvc.perform(get("/api/v1/vault/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.path == 'AI/RAG.md')].type")
                        .value(org.hamcrest.Matchers.hasItem("markdown")));
    }

    @Test
    @Order(5)
    void shouldReadAndSaveNote() throws Exception {
        mockMvc.perform(get("/api/v1/notes/AI/RAG.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("RAG 技术原理"))
                .andExpect(jsonPath("$.tags[0]").value("AI"))
                .andExpect(jsonPath("$.links[0]").value("Embedding"));

        mockMvc.perform(put("/api/v1/notes/AI/RAG.md")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"# RAG\\n\\n更新后的内容 [[Embedding]]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));

        mockMvc.perform(get("/api/v1/notes/AI/RAG.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("更新后的内容")));
    }

    @Test
    @Order(6)
    void shouldReturnBacklinks() throws Exception {
        mockMvc.perform(get("/api/v1/notes/backlinks").param("note", "AI/Embedding.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].noteId").value("AI/RAG.md"));
    }

    @Test
    @Order(7)
    void shouldFullTextSearch() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "RAG"))
                .andExpect(status().isOk())
                // Order 5 已将 RAG.md 改写为无 frontmatter 内容，标题回退为文件名；按 noteId 判定排序第一
                .andExpect(jsonPath("$.results[0].noteId").value("AI/RAG.md"))
                .andExpect(jsonPath("$.results[0].matchType").value("TEXT_MATCH"));
    }

    @Test
    @Order(8)
    void shouldChatReturnSemanticErrorEnvelopeWhenVectorStackDown() throws Exception {
        // Phase 5 起 /api/v1/chat 为真实 RAG 链路：测试环境 Milvus / Ollama 不保证可用，
        // 同步端点应返回统一错误信封（503 + 语义化 code），而不是 500 / NPE。
        // （若本地全栈可用则返回 200，两种状态均视为契约正确）
        var result = mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"RAG 是什么\",\"scope\":\"VAULT\"}"))
                .andReturn();
        int status = result.getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(status == 200 || status == 503,
                "chat 应返回 200（全栈可用）或 503（依赖不可用），实际 " + status);
        if (status == 200) {
            String body = result.getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"content\"") && body.contains("\"sources\""),
                    "全栈可用时 chat 应返回 RAG 回答结构: " + body);
        } else {
            String body = result.getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"code\""),
                    "错误信封必须包含 code 字段: " + body);
        }
    }

    @Test
    @Order(9)
    void shouldStreamChatOverSse() throws Exception {
        // 真实 RAG 链路（依赖不可用 → phase(searching) 后发 error 事件；全栈可用 → 完整事件序列）。
        // 两种环境下 phase 事件都必须出现（检索阶段先于任何可能失败的下游调用）。
        var result = mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"RAG 流式测试\"}"))
                .andReturn();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("event:phase")));
    }

    @Test
    @Order(12)
    void shouldRejectBlankChatMessage() throws Exception {
        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @Order(10)
    void shouldTriggerIndexAndReportStatus() throws Exception {
        mockMvc.perform(post("/api/v1/index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
        // 异步任务，轮询等待终态（READY=管线完成；FAILED=向量库不可用等整体失败，
        // Phase 3 起真实管线依赖 Milvus，测试环境无 Milvus 时终态为 FAILED）
        boolean terminal = false;
        for (int i = 0; i < 60 && !terminal; i++) {
            String body = mockMvc.perform(get("/api/v1/index/status"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            terminal = body.contains("\"status\":\"READY\"") || body.contains("\"status\":\"FAILED\"");
            if (!terminal) {
                Thread.sleep(100);
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(terminal, "索引任务应在超时时间内到达终态");
    }

    @Test
    @Order(11)
    void shouldReturnNoteNotFoundAs404() throws Exception {
        mockMvc.perform(get("/api/v1/notes/AI/missing.md"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));
    }
}

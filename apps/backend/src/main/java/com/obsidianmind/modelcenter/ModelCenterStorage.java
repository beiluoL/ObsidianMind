package com.obsidianmind.modelcenter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Model Center 持久化（Phase 5.5）：config.json（Provider + Model 配置，非敏感）+ CredentialStore（敏感）。
 * 项目无数据库，延续「文件即存储」的项目原则；配置文件不含 Key，可随仓库之外自由备份。
 * 首次启动播种三个内置 Provider（Ollama / DeepSeek / DashScope），模型条目由用户添加。
 */
@Component
public class ModelCenterStorage {

    private static final Logger log = LoggerFactory.getLogger(ModelCenterStorage.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // Instant 等 java.time 类型（jsr310 模块随 spring-boot-starter-json 提供）；ISO-8601 文本便于人工检查
        MAPPER.findAndRegisterModules();
        MAPPER.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private final Path configFile;
    private final CredentialStore credentialStore;
    private final Object lock = new Object();

    public ModelCenterStorage(com.obsidianmind.config.ModelCenterProperties properties,
                              com.obsidianmind.config.AiProperties aiProperties) {
        Path dir = Path.of(properties.storageDir());
        this.configFile = dir.resolve("model-center.json");
        this.credentialStore = new EncryptedFileCredentialStore(dir);
        seedIfNeeded(aiProperties);
        log.info("Model Center 存储目录: {}", dir);
    }

    /** CredentialStore 暴露给 Service / Router（凭据与配置分离原则）。 */
    public CredentialStore credentials() {
        return credentialStore;
    }

    // ---- Provider ----

    public List<AIProvider> listProviders() {
        synchronized (lock) {
            return read().providers.stream().map(AIProvider::copy).toList();
        }
    }

    public Optional<AIProvider> findProvider(String id) {
        synchronized (lock) {
            return read().providers.stream().filter(p -> p.getId().equals(id)).findFirst().map(AIProvider::copy);
        }
    }

    public void upsertProvider(AIProvider provider) {
        synchronized (lock) {
            Config cfg = read();
            cfg.providers.removeIf(p -> p.getId().equals(provider.getId()));
            cfg.providers.add(provider);
            write(cfg);
        }
    }

    public boolean deleteProvider(String id) {
        synchronized (lock) {
            Config cfg = read();
            boolean removed = cfg.providers.removeIf(p -> p.getId().equals(id));
            if (removed) {
                // 级联：删 Provider 必须删其下 Model，否则路由出现悬挂引用
                cfg.models.removeIf(m -> m.getProviderId().equals(id));
                write(cfg);
                credentialStore.delete(id);
            }
            return removed;
        }
    }

    // ---- Model ----

    public List<AIModel> listModels() {
        synchronized (lock) {
            return read().models.stream().map(AIModel::copy).toList();
        }
    }

    public Optional<AIModel> findModel(String id) {
        synchronized (lock) {
            return read().models.stream().filter(m -> m.getId().equals(id)).findFirst().map(AIModel::copy);
        }
    }

    public void upsertModel(AIModel model) {
        synchronized (lock) {
            Config cfg = read();
            cfg.models.removeIf(m -> m.getId().equals(model.getId()));
            cfg.models.add(model);
            if (model.isDefault()) {
                // 默认模型全局唯一：新默认出现时取消其他
                cfg.models.forEach(m -> {
                    if (!m.getId().equals(model.getId())) {
                        m.setDefault(false);
                    }
                });
            }
            write(cfg);
        }
    }

    public boolean deleteModel(String id) {
        synchronized (lock) {
            Config cfg = read();
            boolean removed = cfg.models.removeIf(m -> m.getId().equals(id));
            if (removed) {
                write(cfg);
            }
            return removed;
        }
    }

    public Optional<AIModel> defaultModel() {
        synchronized (lock) {
            return read().models.stream()
                    .filter(AIModel::isDefault)
                    .filter(AIModel::isEnabled)
                    .findFirst()
                    .map(AIModel::copy);
        }
    }

    // ---- persistence ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Config {
        public int version = 1;
        public List<AIProvider> providers = new ArrayList<>();
        public List<AIModel> models = new ArrayList<>();
    }

    private Config read() {
        if (!Files.exists(configFile)) {
            return new Config();
        }
        try {
            return MAPPER.readValue(configFile.toFile(), Config.class);
        } catch (IOException e) {
            log.error("Model Center 配置读取失败，按空配置处理: {}", e.getMessage());
            return new Config();
        }
    }

    private void write(Config cfg) {
        try {
            Files.createDirectories(configFile.getParent());
            Path tmp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), cfg);
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Model Center 配置写入失败", e);
        }
    }

    /** 首次启动播种内置 Provider（不含任何模型条目，不写任何凭据）。 */
    private void seedIfNeeded(com.obsidianmind.config.AiProperties aiProperties) {
        synchronized (lock) {
            if (Files.exists(configFile)) {
                return;
            }
            Config cfg = new Config();
            Instant now = Instant.now();
            String ollamaBaseUrl = "http://localhost:11434";
            try {
                ollamaBaseUrl = aiProperties.ollama().baseUrl();
            } catch (Exception ignore) {
                // AiProperties 异常时用默认值，Model Center 不应阻塞启动
            }
            cfg.providers.add(new AIProvider("ollama", "Ollama", AIProvider.Type.OLLAMA,
                    ollamaBaseUrl, true, now, now));
            cfg.providers.add(new AIProvider("deepseek", "DeepSeek", AIProvider.Type.DEEPSEEK,
                    "https://api.deepseek.com", true, now, now));
            cfg.providers.add(new AIProvider("dashscope", "DashScope", AIProvider.Type.DASHSCOPE,
                    "https://dashscope.aliyuncs.com/compatible-mode/v1", true, now, now));
            write(cfg);
            log.info("Model Center 已播种内置 Provider: ollama / deepseek / dashscope");
        }
    }

    /** 供测试断言默认互斥等不变量。 */
    public long countDefaultModels() {
        synchronized (lock) {
            return read().models.stream().filter(AIModel::isDefault).count();
        }
    }

    /** 供测试列举全部（只读拷贝，按创建时间排序保证输出稳定）。 */
    public List<AIModel> modelsSorted() {
        synchronized (lock) {
            return read().models.stream()
                    .sorted(Comparator.comparing(AIModel::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(AIModel::copy)
                    .toList();
        }
    }
}

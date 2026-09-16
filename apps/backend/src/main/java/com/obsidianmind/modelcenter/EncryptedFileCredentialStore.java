package com.obsidianmind.modelcenter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * AES-GCM 加密文件凭据存储（Phase 5.5 MVP 实现）。
 *
 * 设计：
 *   <storageDir>/.credential.key    —— 首次使用时随机生成的 256-bit 密钥（POSIX 600，仅本机可解）
 *   <storageDir>/credentials.json   —— { providerId: { "v": "base64(iv ‖ ciphertext) } }
 *
 * 为什么不是明文 JSON：Model Center 导出的配置文件可能被用户随手拷贝/同步，密钥文件独立存放
 * 且权限收紧后，配置文件单独泄露不等于 Key 泄露。OS Keychain 是后续演进，CredentialStore 接口已留好。
 * Key 值永远不进日志——异常信息只含文件路径与操作名。
 */
public class EncryptedFileCredentialStore implements CredentialStore {

    private static final Logger log = LoggerFactory.getLogger(EncryptedFileCredentialStore.class);
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final Path dir;
    private final Path configFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private volatile SecretKeySpec keySpec;

    public EncryptedFileCredentialStore(Path dir) {
        this.dir = dir;
        this.configFile = dir.resolve("credentials.json");
    }

    @Override
    public synchronized void save(String providerId, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            delete(providerId);
            return;
        }
        try {
            ObjectNode root = readRoot();
            root.putObject(providerId).put("v", encrypt(apiKey.strip()));
            atomicWrite(root);
        } catch (Exception e) {
            log.error("凭据保存失败: {}", e.getMessage());
            throw new IllegalStateException("凭据保存失败", e);
        }
    }

    @Override
    public synchronized String get(String providerId) {
        try {
            JsonNode node = readRoot().path(providerId).path("v");
            return node.isMissingNode() ? null : decrypt(node.asText());
        } catch (Exception e) {
            log.error("凭据读取失败: {}", e.getMessage());
            return null; // 读失败按未配置处理，不让凭据层炸掉业务请求
        }
    }

    @Override
    public synchronized void delete(String providerId) {
        try {
            ObjectNode root = readRoot();
            if (root.has(providerId)) {
                root.remove(providerId);
                atomicWrite(root);
            }
        } catch (Exception e) {
            log.error("凭据删除失败: {}", e.getMessage());
            throw new IllegalStateException("凭据删除失败", e);
        }
    }

    @Override
    public synchronized boolean exists(String providerId) {
        return get(providerId) != null;
    }

    // ---- crypto ----

    private SecretKeySpec key() throws Exception {
        SecretKeySpec k = keySpec;
        if (k == null) {
            synchronized (this) {
                if (keySpec == null) {
                    Path keyFile = dir.resolve(".credential.key");
                    byte[] raw;
                    if (Files.exists(keyFile)) {
                        raw = Base64.getDecoder().decode(Files.readString(keyFile).strip());
                    } else {
                        raw = new byte[32];
                        random.nextBytes(raw);
                        Files.createDirectories(dir);
                        Files.writeString(keyFile, Base64.getEncoder().encodeToString(raw));
                        restrictOwnerOnly(keyFile);
                    }
                    keySpec = k = new SecretKeySpec(raw, "AES");
                }
            }
        }
        return k;
    }

    private String encrypt(String plain) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    private String decrypt(String encoded) throws Exception {
        byte[] all = Base64.getDecoder().decode(encoded);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, all, 0, GCM_IV_BYTES));
        return new String(cipher.doFinal(all, GCM_IV_BYTES, all.length - GCM_IV_BYTES), StandardCharsets.UTF_8);
    }

    private void restrictOwnerOnly(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignore) {
            // Windows 无 POSIX 权限：目录位于用户主目录下，风险可接受
        }
    }

    // ---- file io ----

    private ObjectNode readRoot() throws IOException {
        if (!Files.exists(configFile)) {
            return mapper.createObjectNode();
        }
        JsonNode node = mapper.readTree(configFile.toFile());
        return node instanceof ObjectNode o ? o : mapper.createObjectNode();
    }

    private void atomicWrite(ObjectNode root) throws IOException {
        Files.createDirectories(dir);
        Path tmp = dir.resolve("credentials.json.tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
        Files.move(tmp, configFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        restrictOwnerOnly(configFile);
    }

    /** 供测试 / 管理端核查：当前存储了哪些 providerId（只返回 ID，绝不返回 Key）。 */
    public synchronized Map<String, Boolean> snapshotIds() {
        Map<String, Boolean> ids = new HashMap<>();
        try {
            Iterator<String> it = readRoot().fieldNames();
            while (it.hasNext()) {
                ids.put(it.next(), true);
            }
        } catch (IOException ignore) {
            // 空存储视为空 Map
        }
        return ids;
    }
}

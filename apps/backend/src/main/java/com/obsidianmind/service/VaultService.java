package com.obsidianmind.service;

import com.obsidianmind.config.ObsidianProperties;
import com.obsidianmind.domain.Vault;
import com.obsidianmind.repository.VaultRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Vault 服务：连接校验、扫描、文件树。只访问用户显式指定的目录（Local First）。
 */
@Service
public class VaultService {

    private final VaultRepository vaultRepository;
    private final ObsidianProperties properties;

    public VaultService(VaultRepository vaultRepository, ObsidianProperties properties) {
        this.vaultRepository = vaultRepository;
        this.properties = properties;
    }

    public void connect(String path) {
        vaultRepository.connect(path);
    }

    public void disconnect() {
        vaultRepository.disconnect();
    }

    public Vault info() {
        if (!vaultRepository.isConnected()) {
            return new Vault("", "", false, 0, 0, null);
        }
        Path root = vaultRepository.requireRoot();
        return new Vault(
                root.getFileName().toString(),
                root.toString(),
                true,
                vaultRepository.allNotes().size(),
                vaultRepository.folderCount(),
                vaultRepository.lastScanAt());
    }

    public VaultRepository.ScanResult scan() {
        return vaultRepository.scan();
    }

    public java.util.List<VaultRepository.FileNode> fileTree() {
        return vaultRepository.listFileTree();
    }

    /** 应用启动时尝试连接配置的默认 Vault（失败不阻塞启动）。 */
    public void tryConnectConfiguredVault() {
        vaultRepository.tryConnectDefault(properties.vault().path());
    }
}

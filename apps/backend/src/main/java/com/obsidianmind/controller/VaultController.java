package com.obsidianmind.controller;

import com.obsidianmind.domain.Vault;
import com.obsidianmind.dto.vault.ConnectVaultRequest;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.VaultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Vault API：连接 / 断开 / 元信息 / 扫描 / 文件树。
 */
@RestController
@RequestMapping("/api/v1/vault")
@Tag(name = "Vault", description = "Vault 连接与扫描")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @GetMapping
    @Operation(summary = "Vault 元信息")
    public Vault info() {
        return vaultService.info();
    }

    @PostMapping("/connect")
    @Operation(summary = "连接 Vault（校验路径存在 / 为目录 / 可读）")
    public ResponseEntity<Vault> connect(@Valid @RequestBody ConnectVaultRequest request) {
        vaultService.connect(request.path());
        return ResponseEntity.ok(vaultService.info());
    }

    @PostMapping("/disconnect")
    @Operation(summary = "断开 Vault")
    public Map<String, Object> disconnect() {
        vaultService.disconnect();
        return Map.of("connected", false);
    }

    @PostMapping("/scan")
    @Operation(summary = "扫描 Vault（只收集文件元数据，不读正文）")
    public Map<String, Object> scan() {
        VaultRepository.ScanResult result = vaultService.scan();
        return Map.of(
                "totalFiles", result.markdownFiles(),
                "markdownFiles", result.markdownFiles(),
                "folders", result.folders(),
                "durationMs", result.durationMs());
    }

    @GetMapping("/files")
    @Operation(summary = "文件树（folder / markdown / image / other）")
    public java.util.List<VaultRepository.FileNode> files() {
        return vaultService.fileTree();
    }
}

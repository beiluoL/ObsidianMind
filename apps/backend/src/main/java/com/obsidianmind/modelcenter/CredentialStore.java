package com.obsidianmind.modelcenter;

/**
 * 凭据存储抽象（Phase 5.5）：Provider Service 只依赖本接口，不触碰存储细节。
 * 未来可替换为 OS Keychain / 数据库加密列，ModelRouter 与 CRUD 层零改动。
 * 实现必须保证：get 返回的 Key 不进入日志 / 异常 / API 响应。
 */
public interface CredentialStore {

    enum Source {
        /** 用户在 UI 中配置（加密文件保存）。 */
        USER_CONFIGURED,
        /** 从允许的环境变量读取（仅开发 / 测试），调用时解析、绝不持久化。 */
        ENVIRONMENT,
        /** 未配置。 */
        NONE
    }

    /** 保存（加密覆盖）。key 为空/空白时视为删除。 */
    void save(String providerId, String apiKey);

    /** 取原文 Key；不存在返回 null。调用方负责不落日志。 */
    String get(String providerId);

    void delete(String providerId);

    boolean exists(String providerId);
}

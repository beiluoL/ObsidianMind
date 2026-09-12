# infrastructure/docker

Docker 相关配置（应用镜像 Dockerfile、构建脚本等）预留目录。

当前阶段应用以本地进程运行（前端 Vite、后端 `mvn spring-boot:run`），仅在需要容器化部署时在此补充：

- `Dockerfile.frontend`（多阶段构建：node build → nginx 托管）
- `Dockerfile.backend`（多阶段构建：maven build → JRE 运行）

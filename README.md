# PersonaChat（简称 PChat）

纯 Compose 绘制、零位图的人设角色扮演聊天 App，由无定工作室开发。包名 `com.wunding.personachat`。

## 功能

- **手机上跑的本地 AI**：双引擎——`.task` 走 MediaPipe（Gemma 1B int4，GPU/CPU 可切）；`.gguf` 走 llama.cpp（Qwen / Llama / Gemma / Phi 等，纯 CPU，自动用模型内置对话模板，逐 token 流式、可中断）。模型导入后完全离线。
- **API AI**：任意 OpenAI 兼容服务；请求带 3 次指数退避重试，流式失败自动退回一次性请求。
- **人设包**：`.aist` / zip（`AI.md` + 主设定 + 扩展包）、SillyTavern PNG 角色卡、JSON 角色卡；支持搜索、随机生成（姓名/性格/世界观）、手写创建、本地导入、远程 700 包下载（含 10 类二次元角色）（raw → jsDelivr → fastly 三源回退）。
- **记忆**：永久 / 长期 / 短期，关键词召回 + 检索测试面板。
- **对话**：多会话页签、消息编辑 / 重新生成 / 删除、流式闪烁光标、完整人设模式开关、归档只读。
- **模型库**：7 个 GGUF 卡片（Qwen2.5 / Llama-3.2 / TinyLlama / DeepSeek-R1-Distill），ModelScope/HuggingFace 镜像切换、自定义直链、下载进度、模型管理。
- **存储**：模型与人设统一放 `/storage/emulated/0/Android/data/com.wunding.personachat/files/`，卸载自动清理，旧数据启动时迁移。
- **APK 远程更新**：从 GitHub Release 检查、下载、拉起系统安装器（签名固定）。
- **导出**：JSON / Markdown，JSON 含 `persona_snapshot`。

## 构建

```
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> gradle --no-daemon assembleRelease
```

固定签名：`keystore/personachat.jks`（不入仓库），SHA-256 `E7:00:F0:1D:8A:7E:4A:54:74:28:33:2B:BE:3D:8B:B9:CC:07:16:D2:0B:13:2D:96:BF:71:B6:8E:AF:EA:78:69`。

## 本地模型

设置 → 模型接入 → 手机本地 → 模型库（下载 / 管理）→ 使用 → 加载 → 连接测试。模型不随 APK 分发，存放在 `Android/data/com.wunding.personachat/files/models`。

- `.task`：MediaPipe，推荐 Gemma 1B int4，可选 GPU/CPU。
- `.gguf`：llama.cpp，官方 Android arm64 预编译库 + 自写 JNI（`app/src/main/cpp/gguf_jni.cpp`，产物 `libpersonagguf.so`）。推荐 **Q4_K_M、1~2B 参数**；上下文 2048、线程 4、CPU 推理。仅 `arm64-v8a`。
- GGUF 优先用模型自带 chat template，没有模板时回退 `system + User/Assistant` 纯文本；`停止生成` 通过 native 原子标志在下一个 token 生效。

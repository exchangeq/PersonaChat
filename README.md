# 人设对话 · PersonaChat

一个纯 Compose 绘制、 cero 位图资源的人设角色扮演聊天 App。由无定工作室开发。

## 功能

- 人设包：`.aist` / zip（内含 `AI.md` + 主设定 + 扩展包），导入即锁定注入；也可从远程仓库 230 包一键下载。
- 双后端：本地 AI（Ollama / llama.cpp 的 OpenAI 兼容端口）与 API AI（任意 OpenAI 兼容服务），一键切换、连接测试。
- 记忆：永久 / 长期 / 短期三层，按关键词召回；可手动锁、编辑、降级、清空。
- 会话：多会话、归档、重命名、导出 JSON / Markdown（含 persona_snapshot）。
- UI：启动页 5s 分段进度 + 扫光 + 逐字「由无定工作室开发」；首字哈希色块头像；全深色主题。
- 零位图：头像、图标、插画、进度条全部 Compose / 矢量绘制。

## 构建

```
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> gradle --no-daemon assembleRelease
```

签名固定：`keystore/personachat.jks`（不入仓库），证书 SHA-256 `E7:00:F0:1D:8A:7E:4A:54:74:28:33:2B:BE:3D:8B:B9:CC:07:16:D2:0B:13:2D:96:BF:71:B6:8E:AF:EA:78:69`。

## 包格式

`.aist` 是 ZIP：根目录（或任意一级目录）下有 `AI.md`，字段 `名称 / 版本 / 定位 / 扩展包：...`；其余 `*.md` 按 AI.md 顺序合并注入，`//` 开头行剔除，上限 16000 字。

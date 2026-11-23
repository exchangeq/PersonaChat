# PersonaChat（简称 PChat）

纯 Compose 绘制、零位图的人设角色扮演聊天 App，由无定工作室开发。包名 `com.wunding.personachat`。

## 功能

- **手机上跑的本地 AI**：MediaPipe LLM（`.task` 模型，推荐 Gemma 1B int4；支持 GPU / CPU 切换），模型导入后完全离线。
- **API AI**：任意 OpenAI 兼容服务；请求带 3 次指数退避重试，流式失败自动退回一次性请求。
- **人设包**：`.aist` / zip（`AI.md` + 主设定 + 扩展包）、SillyTavern PNG 角色卡、JSON 角色卡；支持搜索、手写创建、本地导入、远程 530 包下载（raw → jsDelivr → fastly 三源回退）。
- **记忆**：永久 / 长期 / 短期，关键词召回 + 检索测试面板。
- **对话**：多会话页签、消息编辑 / 重新生成 / 删除、流式闪烁光标、完整人设模式开关、归档只读。
- **APK 远程更新**：从 GitHub Release 检查、下载、拉起系统安装器（签名固定）。
- **导出**：JSON / Markdown，JSON 含 `persona_snapshot`。

## 构建

```
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> gradle --no-daemon assembleRelease
```

固定签名：`keystore/personachat.jks`（不入仓库），SHA-256 `E7:00:F0:1D:8A:7E:4A:54:74:28:33:2B:BE:3D:8B:B9:CC:07:16:D2:0B:13:2D:96:BF:71:B6:8E:AF:EA:78:69`。

## 本地模型

设置 → 模型接入 → 手机本地 → 导入 `.task` → 选 GPU/CPU → 加载 → 连接测试。模型不随 APK 分发。

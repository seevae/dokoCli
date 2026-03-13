# Spring AI 引入与优化计划

## 一、可优化内容概览

| 模块 | 当前实现 | 优化后 | 收益 |
|------|----------|--------|------|
| **工具定义与注册** | 自研 `@Tool` / `@ToolParameter`，`ToolRegistry` 反射扫描、手写 JSON Schema、手写参数解析与执行 | 使用 Spring AI `@Tool` / `@ToolParam`，`ToolCallbacks.from(bean)` 生成 Schema 与执行 | 删除约 150 行自研代码，Schema 与多模型兼容由框架保证 |
| **模型调用** | 自写 `KimiModelClient`（OkHttp、请求/响应拼装、reasoning_content 处理）| 使用 Spring AI OpenAI 兼容 ChatModel（base-url 指向 Moonshot）| 删除约 240 行 HTTP/解析代码；Kimi 的 reasoning_content 需在适配层保留 |
| **消息与请求/响应** | 自维护 `Message`、`ChatRequest`、`ChatResponse`、`ToolCall` 等 | 使用 Spring AI 的 `Message`、`Prompt`、`ChatResponse`；Session 存 Spring AI 消息或保留转换层 | 减少 DTO 与序列化逻辑 |
| **CLI 与工具循环** | `AgentCommand` 内手写 while 工具循环、拼装消息、trim | 可选用 `ChatClient.prompt().tools().call()` 由框架执行工具循环；或保留当前循环结构仅替换底层调用 | 逻辑更清晰，或代码量减少 |

## 二、分阶段实施

### 阶段 1：仅工具层接入 Spring AI（风险最小）

- **目标**：工具定义与执行统一到 Spring AI，模型层与消息层不变。
- **改动**：
  1. 引入 Spring AI BOM + `spring-ai-starter-model-openai`（或仅 tool 相关依赖）。
  2. `BashTools` 改为使用 `org.springframework.ai.tool.annotation.@Tool`、`@ToolParam`。
  3. `ToolRegistry` 改为基于 `ToolCallbacks.from(bashTools)`：获取工具列表、Schema 提供给 Kimi API、执行时委托给 `ToolCallback.call(...)`。
  4. 删除自研 `@Tool`、`@ToolParameter`、`ToolDefinition`（core）、`ToolExecutor`。
- **保留**：`KimiModelClient`、自研 `Message`/`ChatRequest`/`ChatResponse`、`AgentCommand` 中的工具循环（保证 reasoning_content 正确回传）。

### 阶段 2：模型层接入 Spring AI（可选）

- **目标**：用 `ChatModel` 替代 `KimiModelClient`，进一步删减代码。
- **前提**：Kimi 的 `reasoning_content` 需在请求/响应中保留。若 Spring AI OpenAI 客户端不支持该字段，需要：
  - 使用 `extra-body` 或自定义请求构建器注入 reasoning_content；或
  - 保留一层薄封装，在调用 Spring AI 前后做消息的 reasoning_content 注入/提取。
- **改动**：
  1. 配置 `spring.ai.openai.base-url=https://api.moonshot.cn/v1`、api-key、model。
  2. Session 改为存储 Spring AI 的 `Message`，或保留当前 Message 并在调用前转为 `Prompt(messages)`。
  3. `AgentCommand` 使用 `ChatModel.call(prompt)` 或 `ChatClient`；若框架内部完成工具循环且不暴露中间消息，则需评估是否保留“自管工具循环”以正确写入 reasoning_content 到 Session。

### 阶段 3：会话与 CLI 精简（可选）

- 若阶段 2 中采用框架内工具循环且能拿到完整消息列表，可进一步用 `ChatClient` 流式 API、统一错误与重试，并精简 Session 的 trim 逻辑。

## 三、依赖与版本

- **Spring Boot**：当前 3.2.0；Spring AI 1.0 官方推荐 3.4.x。若 1.0 与 3.2 不兼容，可升级到 3.4.x 或选用 Spring AI 0.8.x。
- **Spring AI**：优先使用 1.0.0（Maven Central）；BOM：`spring-ai-bom`。
- **Starter**：`spring-ai-starter-model-openai`，通过 base-url 对接 Kimi（Moonshot OpenAI 兼容接口）。

## 四、文件变更清单（阶段 1）

| 操作 | 文件 |
|------|------|
| 修改 | `pom.xml`：增加 Spring AI BOM、openai starter；视情况升级 Spring Boot |
| 修改 | `src/main/java/.../tool/bash/BashTools.java`：改用 Spring AI `@Tool` / `@ToolParam` |
| 修改 | `src/main/java/.../core/tool/ToolRegistry.java`：基于 Spring AI ToolCallbacks，保留对 Kimi 的 schema/执行接口 |
| 修改 | `AgentCommand.java`：若 ToolRegistry 接口不变则仅依赖注入不变 |
| 删除 | `core/tool/Tool.java`、`ToolParameter.java`、`ToolDefinition.java`、`ToolExecutor.java` |
| 修改 | `model/api/ToolDefinition.java`：保留为“对 Kimi 暴露的 DTO”，从 Spring AI ToolDefinition 转换 |

## 五、已实施内容（阶段 1 完成）

- **pom.xml**：Spring Boot 升级至 3.4.0；引入 Spring AI BOM 1.0.0、`spring-ai-starter-model-openai`。
- **BashTools**：改用 `org.springframework.ai.tool.annotation.@Tool`、`@ToolParam`。
- **ToolRegistry**：基于 `ToolCallbacks.from(bashTools)`，Schema 与执行委托给 Spring AI；`getAllToolDefinitions()` 将 Spring AI 的 `inputSchema()` 转为对 Kimi 的 `ToolDefinition`。
- **删除**：`core/tool/Tool.java`、`ToolParameter.java`、`ToolDefinition.java`、`ToolExecutor.java`。
- **application.yml**：增加 `spring.ai.openai.*` 与 `spring.ai.model.chat=none`；Kimi 仍使用 `kimi.api-key`（或 `KIMI_API_KEY`）。
- **DokoCliApplication**：排除 OpenAI 各模型自动配置（Chat、Speech、Embedding、Image、Moderation、AudioTranscription），避免未配置 API Key 时启动失败；当前仅使用 Spring AI 的 tool 能力。

## 六、注意事项

- **Kimi reasoning_content**：多轮中 assistant 消息若带 tool_calls，必须带 reasoning_content。阶段 1 不改变模型调用，故无影响；阶段 2 若用 Spring AI 发请求，必须确保该字段被正确携带。
- **API Key**：可继续使用 `kimi.api-key` 或迁移到 `spring.ai.openai.api-key`，在 `application.yml` 中统一。
- **测试**：每阶段完成后跑通：启动 CLI、输入问题触发 execute_bash、多轮对话、/clear、/tools。

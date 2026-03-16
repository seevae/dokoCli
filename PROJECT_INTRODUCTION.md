# Doko CLI 项目介绍

## 📖 项目概述

**Doko CLI** 是一款基于 Java + Spring Boot 构建的智能 AI 编程助手命令行工具。它类似于 OpenCode，支持用户通过自然语言与 AI 进行交互式对话，完成代码编写、文件操作、命令执行、Git 操作等开发任务。

该项目采用**多轮对话 + 工具调用（Function Calling）**架构，接入 Kimi K2.5 等大语言模型，通过 Spring AI 框架实现工具管理与调用。

---

## 🚀 核心特性

| 特性 | 说明 |
|------|------|
| **交互式对话** | 基于 JLine 的终端交互界面，支持命令历史、自动补全 |
| **文件系统操作** | 读取、写入、编辑文件，支持自动目录创建和内容截断 |
| **Bash 命令执行** | 执行任意 Shell 命令，支持超时控制和目录切换 |
| **Git 操作支持** | 内置 Git 命令执行能力，支持版本控制工作流 |
| **多轮对话管理** | 自动维护对话上下文，支持历史消息清理和截断 |
| **工具调用机制** | 基于 Spring AI `@Tool` 注解的声明式工具定义 |
| **流式响应** | 支持模型输出的流式处理（具备接口能力） |
| **会话隔离** | 多会话支持，每个会话独立管理对话历史 |

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                         CLI 层                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              AgentCommand (交互入口)                 │    │
│  │  - 启动 Banner、会话初始化                           │    │
│  │  - 命令解析 (/help, /clear, /exit 等)               │    │
│  │  - 调用 AgentService 处理用户输入                    │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       核心逻辑层 (Core)                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │  AgentService   │  │ SessionManager  │  │ ToolRegistry│ │
│  │  - Agent Loop   │  │  - 会话生命周期  │  │ - Spring AI │ │
│  │  - 工具编排     │  │  - 上下文管理    │  │   工具注册   │ │
│  │  - 消息回填     │  │  - 消息截断     │  │ - 工具执行   │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       模型接入层 (Model)                     │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              KimiModelClient                        │    │
│  │  - Kimi API 调用封装                                │    │
│  │  - 消息/工具转换                                     │    │
│  │  - reasoning_content 处理（Kimi K2.5 Thinking 模式）│    │
│  │  - 流式响应解析                                      │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        工具实现层                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐        │
│  │ execute_bash │ │   read_file  │ │  write_file  │        │
│  │  Bash命令执行 │ │   读取文件   │ │   写入文件   │        │
│  └──────────────┘ └──────────────┘ └──────────────┘        │
│  ┌──────────────┐                                          │
│  │  edit_file   │                                          │
│  │  编辑文件内容 │                                          │
│  └──────────────┘                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 项目结构

```
com.dokocli/
├── cli/
│   └── AgentCommand.java          # CLI 交互入口
├── core/
│   ├── agent/
│   │   └── AgentService.java      # Agent 编排逻辑（核心）
│   ├── session/
│   │   ├── Session.java           # 会话实体
│   │   └── SessionManager.java    # 会话管理
│   └── tool/
│       ├── AgentTool.java         # 工具接口标记
│       ├── ToolRegistry.java      # Spring AI 工具注册中心
│       ├── FileToolUtils.java     # 文件工具工具类
│       └── impl/
│           ├── BashTools.java     # Bash 命令执行
│           ├── ReadFileTool.java  # 读取文件
│           ├── WriteFileTool.java # 写入文件
│           └── EditFileTool.java  # 编辑文件
├── model/
│   ├── api/                        # 模型通用接口
│   │   ├── Message.java           # 消息基类
│   │   ├── UserMessage.java       # 用户消息
│   │   ├── AssistantMessage.java  # 助手消息
│   │   ├── SystemMessage.java     # 系统消息
│   │   ├── ToolMessage.java       # 工具消息
│   │   ├── ChatRequest.java       # 对话请求
│   │   ├── ChatResponse.java      # 对话响应
│   │   ├── ToolCall.java          # 工具调用
│   │   ├── ToolDefinition.java    # 工具定义
│   │   └── ModelClient.java       # 模型客户端接口
│   └── kimi/
│       └── KimiModelClient.java   # Kimi API 实现
└── DokoCliApplication.java        # 启动类
```

---

## 🔧 可用工具

| 工具名 | 功能 | 主要参数 |
|--------|------|----------|
| `execute_bash` | 执行 Bash 命令 | `command`, `workingDir`, `timeoutSeconds` |
| `read_file` | 读取文件内容 | `path`, `limitLines` |
| `write_file` | 写入/覆盖文件 | `path`, `content` |
| `edit_file` | 编辑文件内容（查找替换）| `path`, `oldText`, `newText` |

### 使用场景示例

通过 `execute_bash` 可间接实现：
- **文件操作**: `cat`, `echo`, `mkdir`, `rm`, `mv`, `cp`, `touch`
- **目录浏览**: `ls -la`, `pwd`
- **文件搜索**: `grep -r 'keyword'`, `find -name '*.java'`
- **Git 操作**: `git status`, `git diff`, `git log`, `git add`, `git commit`
- **代码运行**: `python`, `node`, `mvn`, `gradle`, `java`

---

## 🔄 Agent Loop 工作流程

```
用户输入
    │
    ▼
┌─────────────────┐
│ 添加 UserMessage │
└─────────────────┘
    │
    ▼
┌─────────────────┐     ┌─────────────────┐
│ 调用 Kimi API   │────▶│ 响应包含 tool_calls? │
└─────────────────┘     └─────────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
                是 (Yes)              否 (No)
                    │                   │
        ┌───────────┘                   │
        ▼                               ▼
┌─────────────────┐             ┌─────────────────┐
│ 执行工具调用    │             │ 输出最终回复    │
│ 添加 Assistant  │             │ 添加到 Session  │
│   + ToolMessage │             └─────────────────┘
└─────────────────┘
        │
        └───────────────────────┐
                                │
                                ▼
                    ┌─────────────────┐
                    │  再次调用 API   │
                    │ （循环直到无工具调用）
                    └─────────────────┘
```

---

## ⚙️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.4.0 | 应用框架 |
| Spring AI | 1.0.0 | AI 工具管理、模型调用 |
| JLine | 3.25.0 | 终端交互 |
| Picocli | 4.7.5 | 命令行解析 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| Jackson | - | JSON 处理 |

---

## 📚 项目演进（Spring AI 优化）

本项目经历了从自研工具框架到 Spring AI 集成的演进：

### 阶段 1（已完成）
- ✅ 引入 Spring AI BOM
- ✅ 工具层迁移到 `@Tool` / `@ToolParam`
- ✅ `ToolRegistry` 基于 `ToolCallbacks.from()`
- ✅ 删除自研工具注解和反射扫描代码

### 阶段 2-3（规划中）
- ⏳ 模型层接入 Spring AI `ChatModel`
- ⏳ 消息层统一使用 Spring AI Message
- ⏳ 简化 Agent Loop，使用 `ChatClient`

详见 `docs/SPRING_AI_OPTIMIZATION_PLAN.md`

---

## 🎯 设计亮点

1. **清晰的层间分离**: CLI → Core → Model → Tool，每层职责单一
2. **Agent Loop 自管**: 手动维护工具调用循环，确保 `reasoning_content` 正确回传（Kimi Thinking 模式必需）
3. **Spring AI 集成**: 工具定义和执行标准化，同时保留模型调用的灵活性
4. **会话上下文管理**: 自动消息截断，防止上下文过长
5. **完善的错误处理**: 工具执行超时、API 错误均有友好提示

---

## 📝 许可证

MIT License

---

## 🤝 贡献

欢迎提交 Issue 和 PR！

---

*Generated by Doko CLI - AI Powered Code Assistant*

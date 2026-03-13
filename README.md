# Doko CLI

一个类似 OpenCode 的 AI 编程助手 CLI 工具，使用 Java + Spring Boot 构建，支持 Kimi K2.5 等大模型。

## 特性

- 交互式对话界面
- 文件系统操作（读、写、编辑、搜索）
- Bash 命令执行
- Git 操作支持
- 可扩展的模型接入架构
- 工具调用机制

## 快速开始

### 1. 配置 API Key

```bash
export KIMI_API_KEY=your-api-key
```

### 2. 构建项目

```bash
./mvnw clean package
```

### 3. 运行

```bash
./mvnw spring-boot:run
```

或直接使用 JAR：

```bash
java -jar target/dokocli-0.0.1-SNAPSHOT.jar
```

## 可用命令

在交互模式中输入：

- `/help` - 显示帮助
- `/clear` - 清空对话历史
- `/session` - 显示会话信息
- `/tools` - 显示可用工具
- `/exit` - 退出程序

## 可用工具

- `read_file` - 读取文件内容
- `write_file` - 创建或覆盖文件
- `edit_file` - 编辑文件内容
- `list_directory` - 列出目录
- `create_directory` - 创建目录
- `file_exists` - 检查文件是否存在
- `search_files` - 搜索文件内容
- `execute_bash` - 执行 Bash 命令
- `view_git_status` - 查看 Git 状态
- `git_diff` - 查看 Git 变更
- `git_log` - 查看 Git 提交历史

## 项目结构

```
com.dokocli
├── cli           # CLI 交互层
├── core          # 核心领域
│   ├── session   # 会话管理
│   └── tool      # 工具框架
├── model         # 模型接入层
│   ├── api       # 统一接口
│   └── kimi      # Kimi 实现
└── tool          # 具体工具实现
    ├── filesystem
    └── bash
```

## 配置

编辑 `application.yml`：

```yaml
kimi:
  api-key: your-api-key
  model: kimi-k2.5
```

## 许可证

MIT

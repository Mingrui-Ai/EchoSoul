# EchoSoul

EchoSoul is a JavaFX desktop chat application with persona-based conversations, local history management, optional online model access, and optional speech features.

This repository has been prepared for a minimal open-source release:

- standard Maven build entry
- sanitized public configuration
- root-level project documentation
- CI build workflow
- ignore rules for local runtime data and generated files

## Tech Stack

- Java 21+
- JavaFX
- Maven
- Gson
- Baidu Speech SDK

## Architecture

```mermaid
graph TD
    %% 定义样式
    classDef ui fill:#e1f5fe,stroke:#03a9f4,stroke-width:2px;
    classDef service fill:#fff3e0,stroke:#ff9800,stroke-width:2px;
    classDef storage fill:#e8f5e9,stroke:#4caf50,stroke-width:2px;
    classDef external fill:#f3e5f5,stroke:#9c27b0,stroke-width:2px;

    %% 节点定义
    User((用户))
    UI[JavaFX 界面层<br/>主界面/角色列表]:::ui
    
    subgraph 核心服务层
        ChatService{ChatService<br/>核心调度器}:::service
        Speech[语音服务层<br/>Baidu / Vosk]:::service
    end

    subgraph 外部调用与本地引擎
        DeepSeek[DeepSeek API<br/>在线大模型]:::external
        LocalBot[本地 ChatBot<br/>离线降级方案]:::service
    end

    subgraph 本地存储模块
        HistoryManager[HistoryManager<br/>会话管理]:::storage
        ConfigManager[ConfigManager<br/>配置管理]:::storage
        ContactsService[ContactsService<br/>角色管理]:::storage
        JSON[(JSON/TXT 文件<br/>历史会话/词库)]:::storage
        Props[(config.properties<br/>API 密钥)]:::storage
    end

    %% 数据流向
    User -->|文本/语音输入| UI
    UI -->|发送消息| ChatService
    
    ChatService -->|1. 在线请求 OkHttp| DeepSeek
    DeepSeek -->|返回 JSON 解析| UI
    
    ChatService -->|2. 网络异常/断网| LocalBot
    LocalBot -->|读取| JSON
    
    UI -->|录音调用| Speech
    Speech <-->|STT/TTS| User
    
    ChatService -->|持久化数据| HistoryManager
    HistoryManager -->|读写| JSON
    ContactsService -->|读写| JSON
    
    ChatService -->|读取运行时配置| ConfigManager
    ConfigManager -->|读取| Props
```

## Requirements

- JDK 21 or newer
- Maven 3.9 or newer

## Quick Start

1. Install JDK 21+ and Maven 3.9+.
2. Create a local `config.properties` in the repository root based on `config.example.properties`.
3. Fill in any external service credentials you want to use.
4. Run:

```powershell
mvn javafx:run
```

Or use the helper script:

```powershell
.\scripts\run.bat
```

## Build

Package the project:

```powershell
mvn -B -DskipTests clean package
```

Or use:

```powershell
.\scripts\compile.bat
```

## Configuration

Public defaults live in:

- `resources/config.properties`
- `config.example.properties`

Local overrides should live in:

- `config.properties` in the repository root

Optional integrations:

- DeepSeek: set `deepseek.api.key`
- Baidu speech: set `baidu.app.id`, `baidu.api.key`, and `baidu.secret.key`
- Vosk offline speech: place a compatible speech model in a top-level `model/` directory if you want offline recognition

If no DeepSeek key is provided, the app can still fall back to its local reply logic.

## Repository Layout

```text
.
|-- .github/workflows/      CI build workflow
|-- docs/                   release notes and maintenance docs
|-- resources/              bundled config and static assets
|-- scripts/                helper scripts for build and run
|-- src/                    Java source code
|-- CHANGELOG.md
|-- config.example.properties
|-- LICENSE
|-- pom.xml
`-- README.md
```

## Open-Source Notes

- Private credentials are not committed.
- Runtime data such as chat history and generated audio files are ignored.
- Before wider public redistribution, review bundled images and any third-party assets for license compliance.

## Contributing

See `CONTRIBUTING.md`.

## License

This project is released under the MIT License. See `LICENSE`.

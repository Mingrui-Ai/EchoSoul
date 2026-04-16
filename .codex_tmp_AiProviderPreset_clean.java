package service.ai;

public enum AiProviderPreset {
    DEEPSEEK(
            "DeepSeek",
            AiProtocol.OPENAI_COMPATIBLE,
            "https://api.deepseek.com/v1/chat/completions",
            "deepseek-chat",
            true,
            "DeepSeek official OpenAI-compatible chat completions endpoint."
    ),
    OPENAI(
            "OpenAI",
            AiProtocol.OPENAI_COMPATIBLE,
            "https://api.openai.com/v1/chat/completions",
            "gpt-4.1-mini",
            true,
            "OpenAI official chat completions endpoint."
    ),
    MOONSHOT(
            "Moonshot",
            AiProtocol.OPENAI_COMPATIBLE,
            "https://api.moonshot.cn/v1/chat/completions",
            "moonshot-v1-8k",
            true,
            "Moonshot AI endpoint with OpenAI-compatible request format."
    ),
    QWEN(
            "Qwen / DashScope",
            AiProtocol.OPENAI_COMPATIBLE,
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "qwen-plus",
            true,
            "Alibaba DashScope compatible chat endpoint for Qwen models."
    ),
    SILICONFLOW(
            "SiliconFlow",
            AiProtocol.OPENAI_COMPATIBLE,
            "https://api.siliconflow.cn/v1/chat/completions",
            "Qwen/Qwen2.5-7B-Instruct",
            true,
            "SiliconFlow OpenAI-compatible inference gateway."
    ),
    OPENROUTER(
            "OpenRouter",
            AiProtocol.OPENAI_COMPATIBLE,
            "https://openrouter.ai/api/v1/chat/completions",
            "openai/gpt-4.1-mini",
            true,
            "OpenRouter multi-model routing endpoint."
    ),
    ZHIPU(
            "Zhipu / GLM",
            AiProtocol.OPENAI_COMPATIBLE,
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            "glm-4-flash",
            true,
            "Zhipu GLM chat completions endpoint."
    ),
    OLLAMA(
            "Ollama",
            AiProtocol.OPENAI_COMPATIBLE,
            "http://localhost:11434/v1/chat/completions",
            "llama3.1",
            false,
            "Local Ollama OpenAI-compatible endpoint. API key is optional."
    ),
    ANTHROPIC(
            "Anthropic Claude",
            AiProtocol.ANTHROPIC,
            "https://api.anthropic.com/v1/messages",
            "claude-3-5-sonnet-latest",
            true,
            "Anthropic Messages API endpoint."
    ),
    GEMINI(
            "Google Gemini",
            AiProtocol.GEMINI,
            "https://generativelanguage.googleapis.com/v1beta/models",
            "gemini-2.5-flash",
            true,
            "Google Gemini GenerateContent endpoint. Base URL should point at the models root."
    ),
    CUSTOM_OPENAI(
            "Custom OpenAI-Compatible",
            AiProtocol.OPENAI_COMPATIBLE,
            "https://your-openai-compatible-host/v1/chat/completions",
            "your-model-name",
            true,
            "Custom endpoint that accepts OpenAI-compatible chat requests."
    ),
    CUSTOM_ANTHROPIC(
            "Custom Anthropic-Compatible",
            AiProtocol.ANTHROPIC,
            "https://your-anthropic-compatible-host/v1/messages",
            "your-model-name",
            true,
            "Custom endpoint that accepts Anthropic Messages API requests."
    ),
    CUSTOM_GEMINI(
            "Custom Gemini-Compatible",
            AiProtocol.GEMINI,
            "https://generativelanguage.googleapis.com/v1beta/models",
            "your-model-name",
            true,
            "Custom endpoint that accepts Gemini GenerateContent requests."
    );

    private final String displayName;
    private final AiProtocol protocol;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final boolean apiKeyRequired;
    private final String description;

    AiProviderPreset(
            String displayName,
            AiProtocol protocol,
            String defaultBaseUrl,
            String defaultModel,
            boolean apiKeyRequired,
            String description
    ) {
        this.displayName = displayName;
        this.protocol = protocol;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.apiKeyRequired = apiKeyRequired;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AiProtocol getProtocol() {
        return protocol;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public boolean isApiKeyRequired() {
        return apiKeyRequired;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCustomPreset() {
        return name().startsWith("CUSTOM_");
    }

    public static AiProviderPreset fromConfigValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DEEPSEEK;
        }
        for (AiProviderPreset preset : values()) {
            if (preset.name().equalsIgnoreCase(rawValue.trim())) {
                return preset;
            }
        }
        return DEEPSEEK;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

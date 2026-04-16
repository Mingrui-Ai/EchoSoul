package service.ai;

public enum AiProtocol {
    OPENAI_COMPATIBLE("OpenAI Compatible"),
    ANTHROPIC("Anthropic Messages"),
    GEMINI("Gemini GenerateContent");

    private final String displayName;

    AiProtocol(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AiProtocol fromConfigValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return OPENAI_COMPATIBLE;
        }
        for (AiProtocol protocol : values()) {
            if (protocol.name().equalsIgnoreCase(rawValue.trim())) {
                return protocol;
            }
        }
        return OPENAI_COMPATIBLE;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

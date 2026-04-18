package service.ai;

public final class AiProviderAdapterFactory {
    private static final AiChatProvider OPENAI_COMPATIBLE = new OpenAiCompatibleAdapter();
    private static final AiChatProvider ANTHROPIC = new AnthropicAdapter();
    private static final AiChatProvider GEMINI = new GeminiAdapter();

    private AiProviderAdapterFactory() {
    }

    public static AiChatProvider forProtocol(AiProtocol protocol) {
        if (protocol == null) {
            return OPENAI_COMPATIBLE;
        }
        return switch (protocol) {
            case OPENAI_COMPATIBLE -> OPENAI_COMPATIBLE;
            case ANTHROPIC -> ANTHROPIC;
            case GEMINI -> GEMINI;
        };
    }
}

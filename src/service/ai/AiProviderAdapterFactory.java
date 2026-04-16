package service.ai;

public final class AiProviderAdapterFactory {
    private static final AiProviderAdapter OPENAI_COMPATIBLE = new OpenAiCompatibleAdapter();
    private static final AiProviderAdapter ANTHROPIC = new AnthropicAdapter();
    private static final AiProviderAdapter GEMINI = new GeminiAdapter();

    private AiProviderAdapterFactory() {
    }

    public static AiProviderAdapter forProtocol(AiProtocol protocol) {
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

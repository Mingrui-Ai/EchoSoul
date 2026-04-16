package service.ai;

public record AiChatConfig(
        AiProviderPreset preset,
        AiProtocol protocol,
        String apiKey,
        String baseUrl,
        String model,
        double temperature,
        int maxTokens,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int writeTimeoutSeconds
) {
    public boolean requiresApiKey() {
        return preset != null && preset.isApiKeyRequired();
    }

    public boolean isApiKeyMissing() {
        return requiresApiKey() && (apiKey == null || apiKey.isBlank());
    }

    public String normalizedApiKey() {
        return apiKey == null ? "" : apiKey.trim();
    }

    public String normalizedBaseUrl() {
        return baseUrl == null ? "" : baseUrl.trim();
    }

    public String normalizedModel() {
        return model == null ? "" : model.trim();
    }
}

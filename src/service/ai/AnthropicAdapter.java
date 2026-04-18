package service.ai;

import com.google.gson.JsonObject;
import model.Message;
import okhttp3.Request;

import java.util.List;

public class AnthropicAdapter extends AbstractAiChatProvider {
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    protected JsonObject buildRequestBody(List<Message> messages, AiChatConfig config) {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", config.normalizedModel());
        requestJson.addProperty("temperature", config.temperature());
        requestJson.addProperty("max_tokens", config.maxTokens());

        String systemPrompt = collectSystemPrompt(messages);
        if (!systemPrompt.isBlank()) {
            requestJson.addProperty("system", systemPrompt);
        }
        requestJson.add("messages", buildAnthropicMessages(messages));
        return requestJson;
    }

    @Override
    protected void applyHeaders(Request.Builder builder, AiChatConfig config) {
        addHeaderIfPresent(builder, "anthropic-version", ANTHROPIC_VERSION);
        addHeaderIfPresent(builder, "x-api-key", config.normalizedApiKey());
    }

    @Override
    protected String extractResponseText(JsonObject json) {
        if (json.has("content")) {
            return AiJsonSupport.extractText(json.get("content"));
        }
        return "";
    }
}

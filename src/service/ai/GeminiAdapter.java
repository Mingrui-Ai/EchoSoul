package service.ai;

import com.google.gson.JsonObject;
import model.Message;
import okhttp3.Request;

import java.util.List;

public class GeminiAdapter extends AbstractAiChatProvider {
    @Override
    protected JsonObject buildRequestBody(List<Message> messages, AiChatConfig config) {
        JsonObject requestJson = new JsonObject();

        String systemPrompt = collectSystemPrompt(messages);
        if (!systemPrompt.isBlank()) {
            JsonObject systemInstruction = new JsonObject();
            systemInstruction.add("parts", createSingleTextPartArray(systemPrompt));
            requestJson.add("systemInstruction", systemInstruction);
        }

        requestJson.add("contents", buildGeminiContents(messages));

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", config.temperature());
        generationConfig.addProperty("maxOutputTokens", config.maxTokens());
        generationConfig.addProperty("topP", 0.9);
        requestJson.add("generationConfig", generationConfig);
        return requestJson;
    }

    @Override
    protected void applyHeaders(Request.Builder builder, AiChatConfig config) {
        addHeaderIfPresent(builder, "x-goog-api-key", config.normalizedApiKey());
    }

    @Override
    protected String extractResponseText(JsonObject json) {
        if (json.has("candidates") && !json.getAsJsonArray("candidates").isEmpty()) {
            JsonObject firstCandidate = json.getAsJsonArray("candidates").get(0).getAsJsonObject();
            if (firstCandidate.has("content")) {
                JsonObject content = firstCandidate.getAsJsonObject("content");
                if (content.has("parts")) {
                    return AiJsonSupport.extractText(content.get("parts"));
                }
            }
        }
        return "";
    }

    @Override
    protected String resolveUrl(AiChatConfig config) {
        String baseUrl = config.normalizedBaseUrl();
        String model = config.normalizedModel();
        if (baseUrl.contains(":generateContent")) {
            return baseUrl;
        }

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalizedBase.endsWith("/models")) {
            return normalizedBase + "/" + model + ":generateContent";
        }
        if (normalizedBase.contains("/models/")) {
            return normalizedBase + ":generateContent";
        }
        return normalizedBase + "/models/" + model + ":generateContent";
    }
}

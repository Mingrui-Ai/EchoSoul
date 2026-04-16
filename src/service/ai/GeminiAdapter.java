package service.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.Message;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.List;

public class GeminiAdapter implements AiProviderAdapter {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public Request buildRequest(List<Message> messages, AiChatConfig config) {
        JsonObject requestJson = new JsonObject();

        String systemPrompt = AiProviderAdapter.collectSystemPrompt(messages);
        if (!systemPrompt.isBlank()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", systemPrompt);
            parts.add(textPart);
            systemInstruction.add("parts", parts);
            requestJson.add("systemInstruction", systemInstruction);
        }

        JsonArray contents = new JsonArray();
        for (Message message : messages) {
            if (message.getRole() == Message.Role.SYSTEM) {
                continue;
            }
            JsonObject content = new JsonObject();
            String role = message.getRole() == Message.Role.ASSISTANT ? "model" : "user";
            content.addProperty("role", role);

            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", message.getContent());
            parts.add(textPart);
            content.add("parts", parts);
            contents.add(content);
        }
        requestJson.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", config.temperature());
        generationConfig.addProperty("maxOutputTokens", config.maxTokens());
        generationConfig.addProperty("topP", 0.9);
        requestJson.add("generationConfig", generationConfig);

        Request.Builder builder = new Request.Builder()
                .url(buildGeminiEndpoint(config))
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestJson.toString(), JSON));

        if (!config.normalizedApiKey().isBlank()) {
            builder.addHeader("x-goog-api-key", config.normalizedApiKey());
        }
        return builder.build();
    }

    @Override
    public String parseResponse(String responseBody) {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        if (json.has("error")) {
            return AiProviderAdapter.extractText(json.get("error"));
        }
        if (json.has("candidates") && json.getAsJsonArray("candidates").size() > 0) {
            JsonObject firstCandidate = json.getAsJsonArray("candidates").get(0).getAsJsonObject();
            if (firstCandidate.has("content")) {
                JsonObject content = firstCandidate.getAsJsonObject("content");
                if (content.has("parts")) {
                    return AiProviderAdapter.extractText(content.get("parts"));
                }
            }
        }
        return "";
    }

    private String buildGeminiEndpoint(AiChatConfig config) {
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

package service.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.Message;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.List;

public class AnthropicAdapter implements AiProviderAdapter {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public Request buildRequest(List<Message> messages, AiChatConfig config) {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", config.normalizedModel());
        requestJson.addProperty("temperature", config.temperature());
        requestJson.addProperty("max_tokens", config.maxTokens());

        String systemPrompt = AiProviderAdapter.collectSystemPrompt(messages);
        if (!systemPrompt.isBlank()) {
            requestJson.addProperty("system", systemPrompt);
        }

        JsonArray messageArray = new JsonArray();
        for (Message message : messages) {
            if (message.getRole() == Message.Role.SYSTEM) {
                continue;
            }
            JsonObject messageObject = new JsonObject();
            String role = message.getRole() == Message.Role.ASSISTANT ? "assistant" : "user";
            messageObject.addProperty("role", role);

            JsonArray contentArray = new JsonArray();
            JsonObject textBlock = new JsonObject();
            textBlock.addProperty("type", "text");
            textBlock.addProperty("text", message.getContent());
            contentArray.add(textBlock);
            messageObject.add("content", contentArray);

            messageArray.add(messageObject);
        }
        requestJson.add("messages", messageArray);

        Request.Builder builder = new Request.Builder()
                .url(config.normalizedBaseUrl())
                .addHeader("Content-Type", "application/json")
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .post(RequestBody.create(requestJson.toString(), JSON));

        if (!config.normalizedApiKey().isBlank()) {
            builder.addHeader("x-api-key", config.normalizedApiKey());
        }
        return builder.build();
    }

    @Override
    public String parseResponse(String responseBody) {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        if (json.has("error")) {
            return AiProviderAdapter.extractText(json.get("error"));
        }
        if (json.has("content")) {
            return AiProviderAdapter.extractText(json.get("content"));
        }
        return "";
    }
}

package service.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.Message;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.List;

public class OpenAiCompatibleAdapter implements AiProviderAdapter {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public Request buildRequest(List<Message> messages, AiChatConfig config) {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", config.normalizedModel());

        JsonArray messageArray = new JsonArray();
        for (Message message : messages) {
            JsonObject messageObject = new JsonObject();
            messageObject.addProperty("role", message.getRole().toString().toLowerCase());
            messageObject.addProperty("content", message.getContent());
            messageArray.add(messageObject);
        }
        requestJson.add("messages", messageArray);
        requestJson.addProperty("temperature", config.temperature());
        requestJson.addProperty("max_tokens", config.maxTokens());
        requestJson.addProperty("top_p", 0.9);

        Request.Builder builder = new Request.Builder()
                .url(config.normalizedBaseUrl())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestJson.toString(), JSON));

        if (!config.normalizedApiKey().isBlank()) {
            builder.addHeader("Authorization", "Bearer " + config.normalizedApiKey());
        }
        return builder.build();
    }

    @Override
    public String parseResponse(String responseBody) {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        if (json.has("error")) {
            return AiProviderAdapter.extractText(json.get("error"));
        }
        if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
            JsonObject firstChoice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
            if (firstChoice.has("message")) {
                return AiProviderAdapter.extractText(firstChoice.getAsJsonObject("message").get("content"));
            }
            if (firstChoice.has("text")) {
                return AiProviderAdapter.extractText(firstChoice.get("text"));
            }
        }
        return "";
    }
}

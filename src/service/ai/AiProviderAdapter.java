package service.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import model.Message;
import okhttp3.Request;

import java.util.List;

public interface AiProviderAdapter {
    Request buildRequest(List<Message> messages, AiChatConfig config);

    String parseResponse(String responseBody);

    static String collectSystemPrompt(List<Message> messages) {
        StringBuilder builder = new StringBuilder();
        for (Message message : messages) {
            if (message.getRole() == Message.Role.SYSTEM) {
                if (!builder.isEmpty()) {
                    builder.append("\n\n");
                }
                builder.append(message.getContent());
            }
        }
        return builder.toString().trim();
    }

    static String extractText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                String text = extractText(item);
                if (!text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
            return builder.toString().trim();
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("text")) {
                return extractText(object.get("text"));
            }
            if (object.has("content")) {
                return extractText(object.get("content"));
            }
            if (object.has("parts")) {
                return extractText(object.get("parts"));
            }
        }
        return element.toString();
    }
}

package service.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class AiJsonSupport {
    private AiJsonSupport() {
    }

    public static JsonObject parseObject(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return new JsonObject();
        }
        JsonElement element = JsonParser.parseString(jsonText);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    public static String extractErrorText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            String error = extractErrorText(parseObject(responseBody));
            return error.isBlank() ? responseBody.trim() : error;
        } catch (Exception ignored) {
            return responseBody.trim();
        }
    }

    public static String extractErrorText(JsonObject json) {
        if (json == null) {
            return "";
        }
        if (json.has("error")) {
            return extractText(json.get("error")).trim();
        }
        if (json.has("message")) {
            return extractText(json.get("message")).trim();
        }
        if (json.has("promptFeedback")) {
            return extractText(json.get("promptFeedback")).trim();
        }
        return "";
    }

    public static String extractText(JsonElement element) {
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

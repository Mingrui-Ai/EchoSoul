package service.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import model.Message;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.List;

public abstract class AbstractAiChatProvider implements AiChatProvider {
    protected static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public final Request buildRequest(List<Message> messages, AiChatConfig config) {
        Request.Builder builder = new Request.Builder()
                .url(resolveUrl(config))
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(buildRequestBody(messages, config).toString(), JSON));
        applyHeaders(builder, config);
        return builder.build();
    }

    @Override
    public final String parseResponse(String responseBody) {
        JsonObject json = AiJsonSupport.parseObject(responseBody);
        String errorMessage = AiJsonSupport.extractErrorText(json);
        if (!errorMessage.isBlank()) {
            return errorMessage;
        }
        return extractResponseText(json).trim();
    }

    protected abstract JsonObject buildRequestBody(List<Message> messages, AiChatConfig config);

    protected abstract String extractResponseText(JsonObject json);

    protected String resolveUrl(AiChatConfig config) {
        return config.normalizedBaseUrl();
    }

    protected void applyHeaders(Request.Builder builder, AiChatConfig config) {
    }

    protected final void addHeaderIfPresent(Request.Builder builder, String headerName, String value) {
        if (value != null && !value.isBlank()) {
            builder.addHeader(headerName, value);
        }
    }

    protected final void addBearerAuthorization(Request.Builder builder, String apiKey) {
        addHeaderIfPresent(builder, "Authorization", apiKey == null || apiKey.isBlank() ? "" : "Bearer " + apiKey);
    }

    protected final JsonArray buildOpenAiMessages(List<Message> messages) {
        JsonArray messageArray = new JsonArray();
        for (Message message : messages) {
            JsonObject messageObject = new JsonObject();
            messageObject.addProperty("role", message.getRole().toString().toLowerCase());
            messageObject.addProperty("content", message.getContent());
            messageArray.add(messageObject);
        }
        return messageArray;
    }

    protected final JsonArray buildAnthropicMessages(List<Message> messages) {
        JsonArray messageArray = new JsonArray();
        for (Message message : messages) {
            if (message.getRole() == Message.Role.SYSTEM) {
                continue;
            }
            JsonObject messageObject = new JsonObject();
            messageObject.addProperty("role", message.getRole() == Message.Role.ASSISTANT ? "assistant" : "user");
            messageObject.add("content", createSingleTextBlockArray(message.getContent()));
            messageArray.add(messageObject);
        }
        return messageArray;
    }

    protected final JsonArray buildGeminiContents(List<Message> messages) {
        JsonArray contents = new JsonArray();
        for (Message message : messages) {
            if (message.getRole() == Message.Role.SYSTEM) {
                continue;
            }
            JsonObject content = new JsonObject();
            content.addProperty("role", message.getRole() == Message.Role.ASSISTANT ? "model" : "user");
            content.add("parts", createSingleTextPartArray(message.getContent()));
            contents.add(content);
        }
        return contents;
    }

    protected final JsonArray createSingleTextPartArray(String text) {
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", text);
        parts.add(textPart);
        return parts;
    }

    protected final JsonArray createSingleTextBlockArray(String text) {
        JsonArray contentArray = new JsonArray();
        JsonObject textBlock = new JsonObject();
        textBlock.addProperty("type", "text");
        textBlock.addProperty("text", text);
        contentArray.add(textBlock);
        return contentArray;
    }

    protected static String collectSystemPrompt(List<Message> messages) {
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
}

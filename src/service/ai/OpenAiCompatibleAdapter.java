package service.ai;

import com.google.gson.JsonObject;
import model.Message;
import okhttp3.Request;

import java.util.List;

public class OpenAiCompatibleAdapter extends AbstractAiChatProvider {
    @Override
    protected JsonObject buildRequestBody(List<Message> messages, AiChatConfig config) {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", config.normalizedModel());
        requestJson.add("messages", buildOpenAiMessages(messages));
        requestJson.addProperty("temperature", config.temperature());
        requestJson.addProperty("max_tokens", config.maxTokens());
        requestJson.addProperty("top_p", 0.9);
        return requestJson;
    }

    @Override
    protected void applyHeaders(Request.Builder builder, AiChatConfig config) {
        addBearerAuthorization(builder, config.normalizedApiKey());
    }

    @Override
    protected String extractResponseText(JsonObject json) {
        if (json.has("choices") && !json.getAsJsonArray("choices").isEmpty()) {
            JsonObject firstChoice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
            if (firstChoice.has("message")) {
                return AiJsonSupport.extractText(firstChoice.getAsJsonObject("message").get("content"));
            }
            if (firstChoice.has("text")) {
                return AiJsonSupport.extractText(firstChoice.get("text"));
            }
        }
        return "";
    }
}

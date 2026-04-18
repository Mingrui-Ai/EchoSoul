package service.ai;

import model.Message;
import okhttp3.Request;

import java.util.List;

public interface AiChatProvider {
    Request buildRequest(List<Message> messages, AiChatConfig config);

    String parseResponse(String responseBody);
}

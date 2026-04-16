package service;

import app.ChatbotUI;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import local.ChatBot;
import model.ChatSession;
import model.Message;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChatService {
    private static final ConfigManager CONFIG_MANAGER = ConfigManager.getInstance();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(CONFIG_MANAGER.getConnectTimeout(), TimeUnit.SECONDS)
            .readTimeout(CONFIG_MANAGER.getReadTimeout(), TimeUnit.SECONDS)
            .writeTimeout(CONFIG_MANAGER.getWriteTimeout(), TimeUnit.SECONDS)
            .build();

    private final ChatbotUI chatUI;
    private final List<Message> chatHistory;
    private final ConfigManager configManager;
    private final ExecutorService executorService;
    private final HistoryManager historyManager;

    private String currentPersonaName = "流萤";
    private String currentPersonaId = "liuying";
    private String currentSystemPrompt;
    private String currentGreeting;
    private String currentSessionFilename;

    private ChatBot localBot;

    public ChatService(ChatbotUI chatUI) {
        this.chatUI = chatUI;
        this.chatHistory = new ArrayList<>();
        this.configManager = CONFIG_MANAGER;
        this.executorService = Executors.newFixedThreadPool(2);
        this.historyManager = HistoryManager.getInstance();

        try {
            localBot = new ChatBot();
            localBot.loadAllResources();
        } catch (Throwable ignored) {
            localBot = null;
        }
    }

    private String personaPrefix() {
        String id = (currentPersonaId == null || currentPersonaId.isBlank()) ? sanitize(currentPersonaName) : currentPersonaId;
        return id + "__";
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "default";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    private String getApiKey() {
        return configManager.getApiKey();
    }

    private String getApiUrl() {
        return configManager.getApiUrl();
    }

    private double getTemperature() {
        return configManager.getTemperature();
    }

    private int getMaxTokens() {
        return configManager.getMaxTokens();
    }

    public synchronized void switchPersona(String personaId, String personaName, String newSystemPrompt, String greeting) {
        if (personaName != null && !personaName.isEmpty()) {
            currentPersonaName = personaName;
        }
        if (personaId != null && !personaId.isBlank()) {
            currentPersonaId = personaId;
        }

        this.currentSystemPrompt = (newSystemPrompt == null || newSystemPrompt.isBlank()) ? null : newSystemPrompt;
        this.currentGreeting = (greeting == null || greeting.isBlank()) ? null : greeting;

        try {
            chatUI.setCurrentContactDisplayName(currentPersonaName);
        } catch (Exception ignored) {
        }
        try {
            chatUI.onPersonaSwitched();
        } catch (Exception ignored) {
        }

        String prefix = personaPrefix();
        String latest = historyManager.findLatestFilenameByPrefix(prefix);
        if (latest != null) {
            loadExistingSessionInternal(latest);
        } else {
            newChatInternal(this.currentSystemPrompt);
        }

        try {
            chatUI.refreshSideBarSessions();
        } catch (Exception ignored) {
        }
    }

    private void newChatInternal(String newSystemPrompt) {
        chatHistory.clear();
        String systemPrompt = (newSystemPrompt == null || newSystemPrompt.isBlank()) ? this.currentSystemPrompt : newSystemPrompt;
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            chatHistory.add(new Message(Message.Role.SYSTEM, systemPrompt));
        }

        currentSessionFilename = historyManager.generateNewSessionFilename(personaPrefix());
        javafx.application.Platform.runLater(chatUI::clearChatDisplay);
        historyManager.saveChatHistoryAs(chatHistory, currentSessionFilename);

        String greeting = (this.currentGreeting != null && !this.currentGreeting.isBlank()) ? this.currentGreeting : null;
        if (greeting != null) {
            Message aiMessage = new Message(Message.Role.ASSISTANT, greeting);
            chatHistory.add(aiMessage);
            javafx.application.Platform.runLater(() -> chatUI.addAiMessage(greeting, aiMessage.getTimestamp()));
            historyManager.saveChatHistoryAs(chatHistory, currentSessionFilename);
        }

        try {
            chatUI.onNewChatStarted();
        } catch (Exception ignored) {
        }
    }

    private void loadExistingSessionInternal(String filename) {
        currentSessionFilename = filename;
        ChatSession session = historyManager.loadSession(filename);
        chatHistory.clear();

        if (session != null) {
            chatHistory.addAll(session.getMessages());
            boolean hasSystem = !chatHistory.isEmpty() && chatHistory.get(0).getRole() == Message.Role.SYSTEM;
            if (!hasSystem && currentSystemPrompt != null && !currentSystemPrompt.isBlank()) {
                chatHistory.add(0, new Message(Message.Role.SYSTEM, currentSystemPrompt));
            }
        } else {
            newChatInternal(this.currentSystemPrompt);
            try {
                chatUI.onHistoryLoaded();
            } catch (Exception ignored) {
            }
            return;
        }

        javafx.application.Platform.runLater(() -> {
            try {
                chatUI.clearChatDisplay();
                for (Message message : chatHistory) {
                    if (message.getRole() == Message.Role.USER) {
                        chatUI.addUserMessage(message.getContent(), message.getTimestamp());
                    } else if (message.getRole() == Message.Role.ASSISTANT) {
                        chatUI.addAiMessage(message.getContent(), message.getTimestamp());
                    }
                }
            } catch (Exception ignored) {
            }
        });

        try {
            chatUI.onHistoryLoaded();
        } catch (Exception ignored) {
        }
    }

    public synchronized void startNewChat(String optionalSystemPrompt) {
        newChatInternal(optionalSystemPrompt);
        try {
            chatUI.refreshSideBarSessions();
        } catch (Exception ignored) {
        }
    }

    public void handleUserMessage(String userMsg) {
        if ("localbot".equalsIgnoreCase(currentPersonaId)) {
            handleLocalOnlyMessage(userMsg);
            return;
        }

        String apiKey = getApiKey();
        if (apiKey.isEmpty() || "YOUR_API_KEY".equals(apiKey)) {
            if (localBot != null) {
                handleLocalOnlyMessage(userMsg);
            } else {
                chatUI.showAlert("API配置错误", "请在配置文件中设置有效的 DeepSeek API 密钥");
            }
            return;
        }

        Message userMessage = new Message(Message.Role.USER, userMsg);
        chatHistory.add(userMessage);
        javafx.application.Platform.runLater(() -> chatUI.addUserMessage(userMsg, userMessage.getTimestamp()));
        persistCurrentSession();

        callDeepSeekApiAsync();

        while (chatHistory.size() > configManager.getHistoryLimit()) {
            int removeIndex = 0;
            if (!chatHistory.isEmpty() && chatHistory.get(0).getRole() == Message.Role.SYSTEM) {
                removeIndex = 1;
            }
            if (chatHistory.size() > removeIndex) {
                chatHistory.remove(removeIndex);
            } else {
                break;
            }
        }
    }

    private void handleLocalOnlyMessage(String userMsg) {
        if (localBot == null) {
            chatUI.showAlert("离线词库不可用", "未能初始化本地词库，请检查资源文件");
            return;
        }

        Message userMessage = new Message(Message.Role.USER, userMsg);
        chatHistory.add(userMessage);
        javafx.application.Platform.runLater(() -> chatUI.addUserMessage(userMsg, userMessage.getTimestamp()));

        String localReply = safeLocalReply(userMsg);
        if (localReply != null && !localReply.isBlank()) {
            Message aiMessage = new Message(Message.Role.ASSISTANT, localReply);
            chatHistory.add(aiMessage);
            javafx.application.Platform.runLater(() -> chatUI.addAiMessage(localReply, aiMessage.getTimestamp()));
        }

        persistCurrentSession();
        try {
            chatUI.refreshSideBarSessions();
        } catch (Exception ignored) {
        }
    }

    public CompletableFuture<Void> callDeepSeekApiAsync() {
        chatUI.setSendingStatus(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return makeApiRequest();
            } catch (IOException ex) {
                throw new RuntimeException("API 请求失败: " + ex.getMessage(), ex);
            }
        }, executorService).thenAccept(responseBody -> {
            String aiResponse = parseDeepSeekResponse(responseBody);
            Message aiMessage = new Message(Message.Role.ASSISTANT, aiResponse);
            chatHistory.add(aiMessage);

            javafx.application.Platform.runLater(() -> {
                chatUI.addAiMessage(aiResponse, aiMessage.getTimestamp());
                chatUI.setSendingStatus(false);
            });

            persistCurrentSession();
            try {
                chatUI.refreshSideBarSessions();
            } catch (Exception ignored) {
            }
        }).exceptionally(error -> {
            String fallback = null;
            if (localBot != null && !chatHistory.isEmpty()) {
                String lastUser = null;
                for (int i = chatHistory.size() - 1; i >= 0; i--) {
                    if (chatHistory.get(i).getRole() == Message.Role.USER) {
                        lastUser = chatHistory.get(i).getContent();
                        break;
                    }
                }
                if (lastUser != null && !lastUser.isBlank()) {
                    fallback = safeLocalReply(lastUser);
                }
            }

            final String messageToShow = (fallback == null || fallback.isBlank()) ? handleApiError(error) : fallback;
            javafx.application.Platform.runLater(() -> {
                chatUI.addAiMessage(messageToShow, java.time.LocalDateTime.now());
                chatUI.setSendingStatus(false);
            });

            if (fallback != null && !fallback.isBlank()) {
                Message aiMessage = new Message(Message.Role.ASSISTANT, fallback);
                chatHistory.add(aiMessage);
                persistCurrentSession();
            }
            return null;
        });
    }

    private String safeLocalReply(String userMsg) {
        try {
            return localBot == null ? "" : localBot.getSmartResponse(userMsg);
        } catch (Throwable ex) {
            return "我先记下啦，这边网络出了点小状况，稍后我再认真回答你。";
        }
    }

    private void appendAiAndRender(String aiText) {
        Message aiMessage = new Message(Message.Role.ASSISTANT, aiText);
        chatHistory.add(aiMessage);
        javafx.application.Platform.runLater(() -> chatUI.addAiMessage(aiText, aiMessage.getTimestamp()));
        persistCurrentSession();
        try {
            chatUI.refreshSideBarSessions();
        } catch (Exception ignored) {
        }
    }

    private void persistCurrentSession() {
        try {
            if (currentSessionFilename == null) {
                currentSessionFilename = historyManager.generateNewSessionFilename(personaPrefix());
            }
            historyManager.saveChatHistoryAs(chatHistory, currentSessionFilename);
        } catch (Exception ex) {
            System.err.println("保存会话失败: " + ex.getMessage());
        }
    }

    private String makeApiRequest() throws IOException {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", "deepseek-chat");

        JsonArray messages = new JsonArray();
        for (Message message : chatHistory) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", message.getRole().toString().toLowerCase());
            msgObj.addProperty("content", message.getContent());
            messages.add(msgObj);
        }
        requestJson.add("messages", messages);

        requestJson.addProperty("temperature", getTemperature());
        requestJson.addProperty("max_tokens", getMaxTokens());
        requestJson.addProperty("top_p", 0.9);

        RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(getApiUrl())
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + getApiKey())
                .post(requestBody)
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (response.isSuccessful()) {
                return responseBody;
            }
            if (response.code() == 401) {
                throw new SecurityException("API 密钥无效或已过期");
            }
            if (response.code() == 429) {
                throw new RuntimeException("请求过于频繁，请稍后再试");
            }
            throw new RuntimeException("API 请求失败，状态码: " + response.code());
        }
    }

    private String parseDeepSeekResponse(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error")) {
                return "出错了：" + json.getAsJsonObject("error").get("message").getAsString();
            }
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        } catch (Exception e) {
            return "我好像没听清，能再说一遍吗？";
        }
    }

    private String handleApiError(Throwable error) {
        Throwable cause = error.getCause();
        if (cause instanceof TimeoutException) {
            return "请求超时了，要不稍后再试？";
        }
        if (cause instanceof SecurityException) {
            return cause.getMessage();
        }
        if (cause instanceof IOException) {
            return "网络好像不太稳定，先检查一下网络吧。";
        }
        return "抱歉，刚才出了点小问题：" + error.getMessage();
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public void clearChatHistory() {
        Message systemMsg = null;
        if (!chatHistory.isEmpty() && chatHistory.get(0).getRole() == Message.Role.SYSTEM) {
            systemMsg = chatHistory.get(0);
        }
        chatHistory.clear();
        if (systemMsg != null) {
            chatHistory.add(systemMsg);
        }
    }

    public long getLastChatEpochMillis(String personaId) {
        try {
            String id = (personaId == null || personaId.isBlank()) ? currentPersonaId : personaId;
            String prefix = (id == null || id.isBlank() ? "" : id) + "__";
            String latest = historyManager.findLatestFilenameByPrefix(prefix);
            if (latest == null) {
                return 0L;
            }

            Path path = Paths.get(historyManager.getMessageDirectoryPath(), latest);
            if (!Files.exists(path)) {
                return 0L;
            }
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    public void loadChatSession(ChatSession session) {
        if (session == null || session.getFilename() == null) {
            return;
        }
        loadExistingSessionInternal(session.getFilename());
    }

    public List<ChatSession> getAllSessions() {
        String prefix = personaPrefix();
        List<ChatSession> all = historyManager.loadAllSessions();
        List<ChatSession> filtered = new ArrayList<>();
        for (ChatSession session : all) {
            if (session.getFilename() != null && session.getFilename().startsWith(prefix)) {
                filtered.add(session);
            }
        }
        return filtered;
    }

    public boolean deleteSession(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        return historyManager.deleteSession(filename);
    }

    public synchronized int deleteAllSessionsForCurrentPersona() {
        int deleted = 0;
        try {
            List<ChatSession> sessions = getAllSessions();
            for (ChatSession session : sessions) {
                if (session != null && session.getFilename() != null) {
                    if (historyManager.deleteSession(session.getFilename())) {
                        deleted++;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        chatHistory.clear();
        currentSessionFilename = null;
        if (currentSystemPrompt != null && !currentSystemPrompt.isBlank()) {
            chatHistory.add(new Message(Message.Role.SYSTEM, currentSystemPrompt));
        }
        return deleted;
    }
}

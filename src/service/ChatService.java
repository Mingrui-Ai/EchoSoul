package service;

import app.ChatbotUI;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.concurrent.Task;
import local.ChatBot;
import model.ChatSession;
import model.Message;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import service.ai.AiChatConfig;
import service.ai.AiProviderAdapter;
import service.ai.AiProviderAdapterFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChatService {
    private static final ConfigManager CONFIG_MANAGER = ConfigManager.getInstance();
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
    private final ChatSearchEngine historySearchEngine;

    private String currentPersonaName = "流萤";
    private String currentPersonaId = "liuying";
    private String currentSystemPrompt;
    private String currentGreeting;
    private String currentSessionFilename;

    private ChatBot localBot;
    private volatile List<ChatSession> cachedSessions = List.of();
    private volatile Map<String, String> messageIdToSessionFilename = Map.of();

    public ChatService(ChatbotUI chatUI) {
        this.chatUI = chatUI;
        this.chatHistory = new ArrayList<>();
        this.configManager = CONFIG_MANAGER;
        this.executorService = Executors.newFixedThreadPool(2);
        this.historyManager = HistoryManager.getInstance();
        this.historySearchEngine = new ChatSearchEngine();

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

    public synchronized void switchPersona(String personaId, String personaName, String newSystemPrompt, String greeting) {
        if (personaName != null && !personaName.isEmpty()) {
            currentPersonaName = personaName;
        }
        if (personaId != null && !personaId.isBlank()) {
            currentPersonaId = personaId;
        }

        currentSystemPrompt = (newSystemPrompt == null || newSystemPrompt.isBlank()) ? null : newSystemPrompt;
        currentGreeting = (greeting == null || greeting.isBlank()) ? null : greeting;

        try {
            chatUI.setCurrentContactDisplayName(currentPersonaName);
        } catch (Exception ignored) {
        }
        try {
            chatUI.onPersonaSwitched();
        } catch (Exception ignored) {
        }

        String latest = historyManager.findLatestFilenameByPrefix(personaPrefix());
        if (latest != null) {
            loadExistingSessionInternal(latest);
        } else {
            newChatInternal(currentSystemPrompt);
        }

        try {
            chatUI.refreshSideBarSessions();
        } catch (Exception ignored) {
        }
    }

    private void newChatInternal(String newSystemPrompt) {
        chatHistory.clear();
        String systemPrompt = (newSystemPrompt == null || newSystemPrompt.isBlank()) ? currentSystemPrompt : newSystemPrompt;
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            chatHistory.add(new Message(Message.Role.SYSTEM, systemPrompt));
        }

        currentSessionFilename = historyManager.generateNewSessionFilename(personaPrefix());
        javafx.application.Platform.runLater(chatUI::clearChatDisplay);
        historyManager.saveChatHistoryAs(chatHistory, currentSessionFilename);

        if (currentGreeting != null && !currentGreeting.isBlank()) {
            Message aiMessage = new Message(Message.Role.ASSISTANT, currentGreeting);
            chatHistory.add(aiMessage);
            javafx.application.Platform.runLater(() -> chatUI.addAiMessage(currentGreeting, aiMessage.getTimestamp()));
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
            boolean hasSystem = !chatHistory.isEmpty() && chatHistory.getFirst().getRole() == Message.Role.SYSTEM;
            if (!hasSystem && currentSystemPrompt != null && !currentSystemPrompt.isBlank()) {
                chatHistory.addFirst(new Message(Message.Role.SYSTEM, currentSystemPrompt));
            }
        } else {
            newChatInternal(currentSystemPrompt);
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

        AiChatConfig aiConfig = configManager.getAiChatConfig();
        String validationError = validateAiConfig(aiConfig);
        if (validationError != null) {
            if (localBot != null) {
                handleLocalOnlyMessage(userMsg);
            } else {
                chatUI.showAlert("AI 配置错误", validationError);
            }
            return;
        }

        Message userMessage = new Message(Message.Role.USER, userMsg);
        chatHistory.add(userMessage);
        javafx.application.Platform.runLater(() -> chatUI.addUserMessage(userMsg, userMessage.getTimestamp()));
        persistCurrentSession();

        callAiApiAsync();
        trimHistory();
    }

    private void handleLocalOnlyMessage(String userMsg) {
        if (localBot == null) {
            chatUI.showAlert("离线词库不可用", "未能初始化本地词库，请检查资源文件。");
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

    public CompletableFuture<Void> callAiApiAsync() {
        chatUI.setSendingStatus(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return makeApiRequest();
            } catch (IOException ex) {
                throw new RuntimeException("API request failed: " + ex.getMessage(), ex);
            }
        }, executorService).thenAccept(aiResponse -> {
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
            if (localBot != null) {
                String lastUserMessage = findLatestUserMessage();
                if (lastUserMessage != null && !lastUserMessage.isBlank()) {
                    fallback = safeLocalReply(lastUserMessage);
                }
            }

            String messageToShow = (fallback == null || fallback.isBlank()) ? handleApiError(error) : fallback;
            LocalDateTime now = LocalDateTime.now();
            javafx.application.Platform.runLater(() -> {
                chatUI.addAiMessage(messageToShow, now);
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

    public CompletableFuture<Void> callDeepSeekApiAsync() {
        return callAiApiAsync();
    }

    private String findLatestUserMessage() {
        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            Message message = chatHistory.get(i);
            if (message.getRole() == Message.Role.USER) {
                return message.getContent();
            }
        }
        return null;
    }

    private void trimHistory() {
        while (chatHistory.size() > configManager.getHistoryLimit()) {
            int removeIndex = 0;
            if (!chatHistory.isEmpty() && chatHistory.getFirst().getRole() == Message.Role.SYSTEM) {
                removeIndex = 1;
            }
            if (chatHistory.size() > removeIndex) {
                chatHistory.remove(removeIndex);
            } else {
                break;
            }
        }
    }

    private String safeLocalReply(String userMsg) {
        try {
            return localBot == null ? "" : localBot.getSmartResponse(userMsg);
        } catch (Throwable ex) {
            return "我先记下来了，刚才出了一点小状况，稍后再好好回复你。";
        }
    }

    private void persistCurrentSession() {
        try {
            if (currentSessionFilename == null) {
                currentSessionFilename = historyManager.generateNewSessionFilename(personaPrefix());
            }
            historyManager.saveChatHistoryAs(chatHistory, currentSessionFilename);
        } catch (Exception ex) {
            System.err.println("Failed to save chat session: " + ex.getMessage());
        }
    }

    private String makeApiRequest() throws IOException {
        AiChatConfig aiConfig = configManager.getAiChatConfig();
        AiProviderAdapter adapter = AiProviderAdapterFactory.forProtocol(aiConfig.protocol());
        Request request = adapter.buildRequest(chatHistory, aiConfig);

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (response.isSuccessful()) {
                String aiResponse = adapter.parseResponse(responseBody).trim();
                if (!aiResponse.isBlank()) {
                    return aiResponse;
                }
                throw new RuntimeException("AI returned an empty response.");
            }

            String errorMessage = extractApiErrorMessage(responseBody);
            if (response.code() == 401 || response.code() == 403) {
                if (errorMessage.isBlank()) {
                    errorMessage = "API key is invalid or has expired.";
                }
                throw new SecurityException(errorMessage);
            }
            if (response.code() == 429) {
                if (errorMessage.isBlank()) {
                    errorMessage = "Request rate limit exceeded. Please try again later.";
                }
                throw new RuntimeException(errorMessage);
            }

            if (errorMessage.isBlank()) {
                errorMessage = "API request failed with HTTP " + response.code() + ".";
            } else {
                errorMessage = errorMessage + " (HTTP " + response.code() + ")";
            }
            throw new RuntimeException(errorMessage);
        }
    }

    private String validateAiConfig(AiChatConfig config) {
        if (config == null) {
            return "未找到 AI 配置。";
        }
        if (config.normalizedBaseUrl().isBlank()) {
            return "请先配置 API Base URL 或 Endpoint。";
        }
        if (config.normalizedModel().isBlank()) {
            return "请先配置模型名称。";
        }
        if (config.isApiKeyMissing()) {
            return "当前供应商需要 API Key，请先填写有效的 API Key。";
        }
        return null;
    }

    private String extractApiErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error")) {
                return AiProviderAdapter.extractText(json.get("error")).trim();
            }
            if (json.has("message")) {
                return AiProviderAdapter.extractText(json.get("message")).trim();
            }
            if (json.has("promptFeedback")) {
                return AiProviderAdapter.extractText(json.get("promptFeedback")).trim();
            }
        } catch (Exception ignored) {
        }
        return responseBody.trim();
    }

    private String handleApiError(Throwable error) {
        Throwable cause = unwrapCause(error);
        if (cause instanceof TimeoutException) {
            return "请求超时了，可以稍后再试试。";
        }
        if (cause instanceof SecurityException && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        if (cause instanceof IOException) {
            return "网络似乎不太稳定，可以先检查网络或 API 服务状态。";
        }
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return "抱歉，刚才调用 AI 时出了点小问题。";
    }

    private Throwable unwrapCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            if (current instanceof CompletionException || current.getClass() == RuntimeException.class) {
                current = current.getCause();
                continue;
            }
            break;
        }
        return current == null ? error : current;
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public void clearChatHistory() {
        Message systemMsg = null;
        if (!chatHistory.isEmpty() && chatHistory.getFirst().getRole() == Message.Role.SYSTEM) {
            systemMsg = chatHistory.getFirst();
        }
        chatHistory.clear();
        if (systemMsg != null) {
            chatHistory.add(systemMsg);
        }
    }

    public long getLastChatEpochMillis(String personaId) {
        try {
            String id = (personaId == null || personaId.isBlank()) ? currentPersonaId : personaId;
            String prefix = (id == null || id.isBlank()) ? "" : id + "__";
            String latest = historyManager.findLatestFilenameByPrefix(prefix);
            if (latest == null) {
                return 0L;
            }

            Path path = Paths.get(historyManager.getMessageDirectoryPath(), latest);
            if (!Files.exists(path)) {
                return 0L;
            }
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public void loadChatSession(ChatSession session) {
        if (session == null || session.getFilename() == null) {
            return;
        }
        loadExistingSessionInternal(session.getFilename());
    }

    public Task<List<ChatSession>> createRefreshSessionsTask() {
        String prefix = personaPrefix();
        return new Task<>() {
            @Override
            protected List<ChatSession> call() {
                List<ChatSession> filteredSessions = new ArrayList<>();
                List<Message> allMessages = new ArrayList<>();
                Map<String, String> nextMessageMap = new HashMap<>();

                for (ChatSession session : historyManager.loadAllSessions()) {
                    if (session.getFilename() == null || !session.getFilename().startsWith(prefix)) {
                        continue;
                    }
                    filteredSessions.add(session);
                    for (Message message : session.getMessages()) {
                        if (message == null || message.getId() == null || message.getId().isBlank()) {
                            continue;
                        }
                        allMessages.add(message);
                        nextMessageMap.put(message.getId(), session.getFilename());
                    }
                }

                historySearchEngine.buildIndex(allMessages);
                cachedSessions = List.copyOf(filteredSessions);
                messageIdToSessionFilename = Map.copyOf(nextMessageMap);
                return filteredSessions;
            }
        };
    }

    public Task<List<ChatSession>> createSearchSessionsTask(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<ChatSession> sessionsSnapshot = cachedSessions;
        Map<String, String> messageMapSnapshot = messageIdToSessionFilename;

        return new Task<>() {
            @Override
            protected List<ChatSession> call() {
                if (normalizedKeyword.isBlank()) {
                    return sessionsSnapshot;
                }

                List<String> matchedMessageIds = historySearchEngine.search(normalizedKeyword);
                if (matchedMessageIds.isEmpty()) {
                    return List.of();
                }

                LinkedHashSet<String> matchedSessionFilenames = new LinkedHashSet<>();
                for (String messageId : matchedMessageIds) {
                    String filename = messageMapSnapshot.get(messageId);
                    if (filename != null && !filename.isBlank()) {
                        matchedSessionFilenames.add(filename);
                    }
                }

                if (matchedSessionFilenames.isEmpty()) {
                    return List.of();
                }

                List<ChatSession> matchedSessions = new ArrayList<>();
                for (ChatSession session : sessionsSnapshot) {
                    if (session.getFilename() != null && matchedSessionFilenames.contains(session.getFilename())) {
                        matchedSessions.add(session);
                    }
                }
                return matchedSessions;
            }
        };
    }

    public List<ChatSession> getAllSessions() {
        String prefix = personaPrefix();
        List<ChatSession> filtered = new ArrayList<>();
        for (ChatSession session : historyManager.loadAllSessions()) {
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
        int deleted = historyManager.deleteAllSessionsByPrefix(personaPrefix());

        chatHistory.clear();
        currentSessionFilename = null;
        if (currentSystemPrompt != null && !currentSystemPrompt.isBlank()) {
            chatHistory.add(new Message(Message.Role.SYSTEM, currentSystemPrompt));
        }
        return deleted;
    }
}

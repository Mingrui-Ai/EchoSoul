package service;

import app.ChatbotUI;
import model.ChatSession;
import model.Message;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import local.ChatBot;

public class ChatService {
    private final ChatbotUI chatUI;
    private final List<Message> chatHistory;
    private final HttpClient client;
    private final ConfigManager configManager;
    private final ExecutorService executorService;
    private final HistoryManager historyManager;

    // 当前使用的 AI 名称与标识
    private String currentPersonaName = "流萤";
    private String currentPersonaId = "liuying"; // 默认 id
    // 当前 persona 的系统提示词（仅使用联系人自己的设定；若为空则不加入 system 消消息）
    private String currentSystemPrompt = null;
    // 当前 persona 的问候语（若存在）
    private String currentGreeting = null;

    // 当前进行中的会话文件名（以 persona 前缀开头），在一次聊天期间保持不变
    private String currentSessionFilename = null;

    private ChatBot localBot;

    private String personaPrefix() {
        String id = (currentPersonaId == null || currentPersonaId.isBlank()) ? sanitize(currentPersonaName) : currentPersonaId;
        return id + "__";
    }

    private static String sanitize(String s) {
        if (s == null) return "default";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    // 从配置管理器获取配置
    private String getApiKey() { return configManager.getApiKey(); }
    private String getApiUrl() { return configManager.getApiUrl(); }
    private double getTemperature() { return configManager.getTemperature(); }
    private int getMaxTokens() { return configManager.getMaxTokens(); }

    public ChatService(ChatbotUI chatUI) {
        this.chatUI = chatUI;
        this.chatHistory = new ArrayList<>();
        this.configManager = ConfigManager.getInstance();
        this.executorService = Executors.newFixedThreadPool(2); // 限制并发数
        this.historyManager = HistoryManager.getInstance();

        // 初始化HTTP客户端
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(configManager.getConnectTimeout()))
                .build();
        // 本地机器人初始化（作为离线/失败回退）
        try {
            localBot = new ChatBot();
            localBot.loadAllResources();
        } catch (Throwable ignored) {
            localBot = null;
        }
        // 不在构造阶段注入任何默认系统提示，完全由联系人设定或历史会话决定
    }

    /**
     * 切换 persona（带 personaId）。
     * 规则：若该 persona 有历史则加载最近一次会话；否则创建新的会话。无弹窗。
     * 仅保留联系人的系统设定，不再使用全局默认。
     */
    public synchronized void switchPersona(String personaId, String personaName, String newSystemPrompt, String greeting) {
        if (personaName != null && !personaName.isEmpty()) currentPersonaName = personaName;
        if (personaId != null && !personaId.isBlank()) currentPersonaId = personaId;
        this.currentSystemPrompt = (newSystemPrompt == null || newSystemPrompt.isBlank()) ? null : newSystemPrompt;
        this.currentGreeting = (greeting == null || greeting.isBlank()) ? null : greeting;

        // 更新 UI 顶部的联系人标题，并抑制历史播放
        try { chatUI.setCurrentContactDisplayName(currentPersonaName); } catch (Exception ignored) {}
        try { chatUI.onPersonaSwitched(); } catch (Exception ignored) {}

        String prefix = personaPrefix();
        String latest = historyManager.findLatestFilenameByPrefix(prefix);
        if (latest != null) {
            loadExistingSessionInternal(latest);
        } else {
            newChatInternal(this.currentSystemPrompt);
        }
        try { chatUI.refreshSideBarSessions(); } catch (Exception ignore) {}
    }

    /** 新建会话：清空历史，仅在联系人有系统设定时加入 system 消息，申请一个新的会话文件名，并立即保存空白会话 */
    private void newChatInternal(String newSystemPrompt) {
        chatHistory.clear();
        String sys = (newSystemPrompt == null || newSystemPrompt.isBlank()) ? this.currentSystemPrompt : newSystemPrompt;
        if (sys != null && !sys.isBlank()) {
            chatHistory.add(new Message(Message.Role.SYSTEM, sys));
        }
        currentSessionFilename = historyManager.generateNewSessionFilename(personaPrefix());
        // UI 清空
        javafx.application.Platform.runLater(chatUI::clearChatDisplay);
        // 立即保存一次（可能只有 system 或为空）
        historyManager.saveChatHistoryAs(chatHistory, currentSessionFilename);

        // 使用当前 persona 的 greeting（如果有）
        String greet = (this.currentGreeting != null && !this.currentGreeting.isBlank()) ? this.currentGreeting : null;
        if (greet != null) {
            Message aiMsg = new Message(Message.Role.ASSISTANT, greet);
            chatHistory.add(aiMsg);
            javafx.application.Platform.runLater(() -> chatUI.addAiMessage(greet, aiMsg.getTimestamp()));
            historyManager.saveChatHistoryAs(chatHistory, currentSessionFilename);
        }
        try { chatUI.onNewChatStarted(); } catch (Exception ignored) {}
    }

    /** 加载已存在会话并渲染到 UI，不弹窗；不再插入任何默认 system 提示 */
    private void loadExistingSessionInternal(String filename) {
        currentSessionFilename = filename;
        ChatSession session = historyManager.loadSession(filename);
        chatHistory.clear();
        if (session != null) {
            // 采用保存的历史
            chatHistory.addAll(session.getMessages());
            // 若历史缺少 SYSTEM，而当前 persona 有设定，则在最前补齐一次
            boolean hasSystem = !chatHistory.isEmpty() && chatHistory.get(0).getRole() == Message.Role.SYSTEM;
            if (!hasSystem && currentSystemPrompt != null && !currentSystemPrompt.isBlank()) {
                chatHistory.add(0, new Message(Message.Role.SYSTEM, currentSystemPrompt));
            }
        } else {
            // 无法读取则回落为新会话（仅使用联系人的设定）
            newChatInternal(this.currentSystemPrompt);
            try { chatUI.onHistoryLoaded(); } catch (Exception ignored) {}
            return;
        }
        // 在 UI 中重新渲染聊天记录（跳过 SYSTEM 消息）
        javafx.application.Platform.runLater(() -> {
            try {
                chatUI.clearChatDisplay();
                for (Message msg : chatHistory) {
                    if (msg.getRole() == Message.Role.USER) {
                        chatUI.addUserMessage(msg.getContent(), msg.getTimestamp());
                    } else if (msg.getRole() == Message.Role.ASSISTANT) {
                        chatUI.addAiMessage(msg.getContent(), msg.getTimestamp());
                    }
                }
            } catch (Exception ignored) {}
        });
        try { chatUI.onHistoryLoaded(); } catch (Exception ignored) {}
    }

    /** 对外：开始一个新会话（配合 UI 顶部 + 按钮） */
    public synchronized void startNewChat(String optionalSystemPrompt) {
        newChatInternal(optionalSystemPrompt);
        try { chatUI.refreshSideBarSessions(); } catch (Exception ignore) {}
    }

    public void handleUserMessage(String userMsg) {
        // 若为本地聊天 persona，则强制使用本地回复
        if ("localbot".equalsIgnoreCase(currentPersonaId)) {
            if (localBot != null) {
                Message userMessage = new Message(Message.Role.USER, userMsg);
                chatHistory.add(userMessage);
                // 先在 UI 显示用户消息
                javafx.application.Platform.runLater(() -> chatUI.addUserMessage(userMsg, userMessage.getTimestamp()));

                // 计算本地回复（不影响用户消息显示顺序）
                String localReply = safeLocalReply(userMsg);
                if (localReply != null && !localReply.isBlank()) {
                    Message aiMessage = new Message(Message.Role.ASSISTANT, localReply);
                    chatHistory.add(aiMessage);
                    javafx.application.Platform.runLater(() -> chatUI.addAiMessage(localReply, aiMessage.getTimestamp()));
                }
                // 保存（包含用户与可能的 AI 消息）
                persistCurrentSession();
                try { chatUI.refreshSideBarSessions(); } catch (Exception ignore) {}
            } else {
                chatUI.showAlert("离线词库不可用", "未能初始化本地词库，请检查资源文件");
            }
            return;
        }
        // 验证API密钥
        String apiKey = getApiKey();
        if (apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY")) {
            // 无密钥时使用本地回复（同样按正确顺序显示）
            if (localBot != null) {
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
                try { chatUI.refreshSideBarSessions(); } catch (Exception ignore) {}
                return;
            } else {
                chatUI.showAlert("API配置错误", "请在配置文件中设置有效的DeepSeek API密钥");
                return;
            }
        }

        // 添加用户消息到历史（在线路径保持原逻辑）
        Message userMessage = new Message(Message.Role.USER, userMsg);
        chatHistory.add(userMessage);
        javafx.application.Platform.runLater(() -> chatUI.addUserMessage(userMsg, userMessage.getTimestamp()));

        // 立刻覆盖保存到当前会话
        persistCurrentSession();

        // 异步调用API
        callDeepSeekApiAsync();

        // 限制聊天历史长度，若有 system 消息则保留第一条 system
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

    public CompletableFuture<Void> callDeepSeekApiAsync() {
        chatUI.setSendingStatus(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return makeApiRequest();
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException("API请求失败: " + ex.getMessage(), ex);
            }
        }, executorService).thenAccept(responseBody -> {
            String aiResponse = parseDeepSeekResponse(responseBody);
            Message aiMessage = new Message(Message.Role.ASSISTANT, aiResponse);
            chatHistory.add(aiMessage);

            // 在UI线程中更新界面
            javafx.application.Platform.runLater(() -> {
                chatUI.addAiMessage(aiResponse, aiMessage.getTimestamp());
                chatUI.setSendingStatus(false);
            });

            // 覆盖保存到当前会话文件
            persistCurrentSession();

            // 刷新 UI 右侧历史会话列表
            try { chatUI.refreshSideBarSessions(); } catch (Exception ignore) {}
        }).exceptionally(e -> {
            // API 失败：尝试使用本地回复回退
            String fallback = null;
            if (localBot != null && !chatHistory.isEmpty()) {
                // 最近一条必定是用户消息
                String lastUser = null;
                for (int i = chatHistory.size()-1; i>=0; i--) { if (chatHistory.get(i).getRole()== Message.Role.USER) { lastUser = chatHistory.get(i).getContent(); break; } }
                if (lastUser != null && !lastUser.isBlank()) fallback = safeLocalReply(lastUser);
            }
            final String toShow = (fallback == null || fallback.isBlank()) ? handleApiError(e) : fallback;
            javafx.application.Platform.runLater(() -> {
                // 使用当前时间展示回退的 AI 消息
                chatUI.addAiMessage(toShow, java.time.LocalDateTime.now());
                chatUI.setSendingStatus(false);
            });
            if (fallback != null && !fallback.isBlank()) {
                Message aiMsg = new Message(Message.Role.ASSISTANT, fallback);
                chatHistory.add(aiMsg);
                persistCurrentSession();
            }
            return null;
        });
    }

    private String safeLocalReply(String userMsg) {
        try {
            return localBot == null ? "" : localBot.getSmartResponse(userMsg);
        } catch (Throwable ex) {
            return "我先记下啦，这边网络出点小状况，稍后我再认真回答你。";
        }
    }

    private void appendAiAndRender(String aiText) {
        Message aiMessage = new Message(Message.Role.ASSISTANT, aiText);
        chatHistory.add(aiMessage);
        javafx.application.Platform.runLater(() -> chatUI.addAiMessage(aiText, aiMessage.getTimestamp()));
        persistCurrentSession();
        try { chatUI.refreshSideBarSessions(); } catch (Exception ignore) {}
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

    // === 调用 DeepSeek API 的实现 ===
    private String makeApiRequest() throws IOException, InterruptedException {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", "deepseek-chat");

        JsonArray messages = new JsonArray();
        for (Message msg : chatHistory) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.getRole().toString().toLowerCase());
            msgObj.addProperty("content", msg.getContent());
            messages.add(msgObj);
        }
        requestJson.add("messages", messages);

        requestJson.addProperty("temperature", getTemperature());
        requestJson.addProperty("max_tokens", getMaxTokens());
        requestJson.addProperty("top_p", 0.9);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getApiUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getApiKey())
                .timeout(Duration.ofSeconds(configManager.getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else if (response.statusCode() == 401) {
            throw new SecurityException("API密钥无效或已过期");
        } else if (response.statusCode() == 429) {
            throw new RuntimeException("请求过于频繁，请稍后再试");
        } else {
            throw new RuntimeException("API请求失败，状态码: " + response.statusCode());
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
            return "嗯？没听清呢，能再说一遍吗？";
        }
    }

    private String handleApiError(Throwable e) {
        Throwable cause = e.getCause();
        if (cause instanceof TimeoutException) {
            return "请求超时了呢~ 要不稍后再试？";
        } else if (cause instanceof SecurityException) {
            return cause.getMessage();
        } else if (cause instanceof IOException) {
            return "网络好像不太好呢~ 检查一下网络吧";
        } else {
            return "哎呀，出了点小问题: " + e.getMessage();
        }
    }

    public void shutdown() { executorService.shutdown(); }


    // 清空聊天历史（如果存在 system 消息则保留否则清空）
    public void clearChatHistory() {
        Message systemMsg = null;
        if (!chatHistory.isEmpty() && chatHistory.get(0).getRole() == Message.Role.SYSTEM) {
            systemMsg = chatHistory.get(0);
        }
        chatHistory.clear();
        if (systemMsg != null) chatHistory.add(systemMsg);
    }

    /** 返回指定 personaId 最近一次聊天的最后修改时间（毫秒）。无历史返回 0。 */
    public long getLastChatEpochMillis(String personaId) {
        try {
            String id = (personaId == null || personaId.isBlank()) ? currentPersonaId : personaId;
            String pfx = (id == null || id.isBlank() ? "" : id) + "__";
            String latest = historyManager.findLatestFilenameByPrefix(pfx);
            if (latest == null) return 0L;
            // 使用新的消息目录
            Path p = Paths.get(historyManager.getMessageDirectoryPath(), latest);
            if (!Files.exists(p)) return 0L;
            java.nio.file.attribute.FileTime ft = Files.getLastModifiedTime(p);
            return ft.toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    // 恢复：加载历史会话（供 UI 双击调用）
    public void loadChatSession(ChatSession session) {
        if (session == null || session.getFilename() == null) return;
        loadExistingSessionInternal(session.getFilename());
    }

    // 恢复：获取当前 persona 前缀的所有会话（供侧边栏及刷新使用）
    public List<ChatSession> getAllSessions() {
        String prefix = personaPrefix();
        List<ChatSession> all = historyManager.loadAllSessions();
        List<ChatSession> filtered = new ArrayList<>();
        for (ChatSession s : all) {
            if (s.getFilename() != null && s.getFilename().startsWith(prefix)) filtered.add(s);
        }
        return filtered;
    }

    // 恢复：删除指定会话文件（侧边栏垃圾桶按钮使用）
    public boolean deleteSession(String filename) {
        if (filename == null || filename.isBlank()) return false;
        return historyManager.deleteSession(filename);
    }

    public synchronized int deleteAllSessionsForCurrentPersona() {
        int deleted = 0;
        try {
            List<ChatSession> sessions = getAllSessions();
            for (ChatSession s : sessions) {
                if (s != null && s.getFilename() != null) {
                    if (historyManager.deleteSession(s.getFilename())) deleted++;
                }
            }
        } catch (Exception ignored) {}
        chatHistory.clear();
        currentSessionFilename = null; // 下一次用户输入才生成新的文件
        if (currentSystemPrompt != null && !currentSystemPrompt.isBlank()) {
            chatHistory.add(new Message(Message.Role.SYSTEM, currentSystemPrompt));
        }
        return deleted;
    }
}

package service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import model.Message;
import model.ChatSession;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class HistoryManager {
    private static HistoryManager instance;
    // 根 history 目录
    private final String historyDir;
    // 新增：消息目录（聊天记录）和外部联系人目录（新增联系人存放处）
    private final String messageDir;
    private final String outContactsDir; // 保留用于只读兼容旧版本，不再主动创建目录
    // 新增：统一联系人目录（替代原 outContacts，用于所有联系人存取）
    private final String contactsDir; // history/contacts
    private final Gson gson;

    private HistoryManager() {
        this.historyDir = getHistoryDirectory();
        this.messageDir = historyDir + File.separator + "message";
        this.outContactsDir = historyDir + File.separator + "outContacts";
        this.contactsDir = historyDir + File.separator + "contacts"; // 新联系人目录
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        createHistoryDirectories();
        // 迁移旧的会话数据（从 history 根目录移动到 message 子目录）
        migrateLegacySessions();
    }

    public static HistoryManager getInstance() {
        if (instance == null) {
            instance = new HistoryManager();
        }
        return instance;
    }

    private String getHistoryDirectory() {
        String projectDir = System.getProperty("user.dir");
        return projectDir + File.separator + "history";
    }

    // 创建所需的子目录
    private void createHistoryDirectories() {
        try {
            // 根目录
            Path dirPath = Paths.get(historyDir);
            if (!Files.exists(dirPath)) Files.createDirectories(dirPath);
            // 聊天记录目录
            Path msgPath = Paths.get(messageDir);
            if (!Files.exists(msgPath)) Files.createDirectories(msgPath);
            // 移除：不再自动创建 outContacts 目录，避免再次出现该目录
            // Path outPath = Paths.get(outContactsDir);
            // if (!Files.exists(outPath)) Files.createDirectories(outPath);
            // 新：统一联系人目录
            Path contactsPath = Paths.get(contactsDir);
            if (!Files.exists(contactsPath)) Files.createDirectories(contactsPath);
        } catch (IOException e) {
            System.err.println("创建历史记录目录失败: " + e.getMessage());
            // 失败则尝试回退
            fallbackToUserDirectory();
        }
    }

    private void fallbackToUserDirectory() {
        try {
            String userHome = System.getProperty("user.home");
            String fallbackBase = userHome + File.separator + ".aichat" + File.separator + "history";
            Files.createDirectories(Paths.get(fallbackBase));
            Files.createDirectories(Paths.get(fallbackBase, "message"));
            // 移除：不再创建 outContacts
            // Files.createDirectories(Paths.get(fallbackBase, "outContacts"));
            Files.createDirectories(Paths.get(fallbackBase, "contacts"));
            System.out.println("使用备用目录: " + fallbackBase);
        } catch (IOException ex) {
            System.err.println("创建备用目录也失败: " + ex.getMessage());
        }
    }

    // 对外提供目录（给其他服务使用）
    public String getHistoryDirectoryPath() { return historyDir; }
    public String getMessageDirectoryPath() { return messageDir; }
    public String getOutContactsDirectoryPath() { return outContactsDir; }
    public String getContactsDirectoryPath() { return contactsDir; }

    // 保存聊天记录（新结构：写入 message 子目录）
    // 已废弃：外部均使用 saveChatHistoryAs 覆盖保存，因此删除旧 saveChatHistory 以减少冗余
    // public void saveChatHistory(List<Message> messages, String sessionName) {
    //     try {
    //         String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    //         String filename = sessionName + "_" + timestamp + ".json";
    //         Path filePath = Paths.get(messageDir, filename);

    //         JsonObject historyJson = new JsonObject();
    //         historyJson.addProperty("sessionName", sessionName);
    //         historyJson.addProperty("saveTime", LocalDateTime.now().toString());
    //         historyJson.addProperty("messageCount", messages.size());

    //         JsonArray messagesArray = new JsonArray();
    //         for (Message msg : messages) {
    //             // 之前过滤了SYSTEM导致系统提示词丢失，这里取消过滤，完整保存
    //             messagesArray.add(msg.toJson());
    //         }
    //         historyJson.add("messages", messagesArray);

    //         Files.writeString(filePath, gson.toJson(historyJson));
    //         System.out.println("聊天记录已保存到: " + filePath);
    //     } catch (IOException e) {
    //         System.err.println("保存聊天记录失败: " + e.getMessage());
    //     }
    // }

    // 以指定文件名保存聊天记录（覆盖同名文件，用于自动保存/草稿）
    public void saveChatHistoryAs(List<Message> messages, String filename) {
        try {
            Path filePath = Paths.get(messageDir, filename);

            JsonObject historyJson = new JsonObject();
            historyJson.addProperty("sessionName", filename);
            historyJson.addProperty("saveTime", LocalDateTime.now().toString());
            historyJson.addProperty("messageCount", messages.size());

            JsonArray messagesArray = new JsonArray();
            for (Message msg : messages) {
                // 不再丢弃 SYSTEM 消息
                messagesArray.add(msg.toJson());
            }
            historyJson.add("messages", messagesArray);

            Files.writeString(filePath, gson.toJson(historyJson));
            System.out.println("自动保存聊天记录到: " + filePath);
        } catch (IOException e) {
            System.err.println("自动保存聊天记录失败: " + e.getMessage());
        }
    }

    // 加载所有历史会话（优先新目录 message；若不存在则兼容旧目录 history）
    public List<ChatSession> loadAllSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        // 先尝试 message 子目录
        loadSessionsFromDir(Paths.get(messageDir), sessions);
        // 兼容旧目录（避免丢失历史），但不重复添加
        if (sessions.isEmpty()) {
            loadSessionsFromDir(Paths.get(historyDir), sessions);
        }
        return sessions;
    }

    private void loadSessionsFromDir(Path dir, List<ChatSession> out) {
        try {
            if (!Files.exists(dir)) return;
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(path -> path.toString().endsWith(".json"))
                        .sorted((p1, p2) -> {
                            try {
                                return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                            } catch (IOException e) { return 0; }
                        })
                        .forEach(path -> {
                            try {
                                String content = Files.readString(path);
                                JsonObject json = gson.fromJson(content, JsonObject.class);

                                String sessionName = json.get("sessionName").getAsString();
                                LocalDateTime saveTime = LocalDateTime.parse(json.get("saveTime").getAsString());
                                int messageCount = json.get("messageCount").getAsInt();

                                JsonArray messagesArray = json.getAsJsonArray("messages");
                                List<Message> messages = new ArrayList<>();
                                for (int i = 0; i < messagesArray.size(); i++) {
                                    messages.add(Message.fromJson(messagesArray.get(i).getAsJsonObject()));
                                }
                                out.add(new ChatSession(sessionName, saveTime, messageCount, messages, path.getFileName().toString()));
                            } catch (IOException e) {
                                System.err.println("加载会话失败: " + path + ", " + e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            System.err.println("读取历史记录目录失败: " + e.getMessage());
        }
    }


    // 删除会话（新目录）
    public boolean deleteSession(String filename) {
        try {
            boolean deleted = Files.deleteIfExists(Paths.get(messageDir, filename));
            if (deleted) {
                System.out.println("已删除会话文件: " + filename);
            }
            return deleted;
        } catch (IOException e) {
            System.err.println("删除会话失败: " + e.getMessage());
            return false;
        }
    }

    // 获取会话文件内容（新目录）
    public ChatSession loadSession(String filename) {
        try {
            Path filePath = Paths.get(messageDir, filename);
            String content = Files.readString(filePath);
            JsonObject json = gson.fromJson(content, JsonObject.class);

            String sessionName = json.get("sessionName").getAsString();
            LocalDateTime saveTime = LocalDateTime.parse(json.get("saveTime").getAsString());
            int messageCount = json.get("messageCount").getAsInt();

            JsonArray messagesArray = json.getAsJsonArray("messages");
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < messagesArray.size(); i++) {
                messages.add(Message.fromJson(messagesArray.get(i).getAsJsonObject()));
            }

            return new ChatSession(sessionName, saveTime, messageCount, messages, filename);
        } catch (IOException e) {
            System.err.println("加载会话失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 根据文件名前缀（通常为 personaId__）查找最近一次会话文件名。
     * 若不存在，返回 null。
     */
    public String findLatestFilenameByPrefix(String prefix) {
        try {
            Path messagePath = Paths.get(messageDir);
            if (!Files.exists(messagePath)) return null;
            try (Stream<Path> stream = Files.list(messagePath)) {
                return stream
                        .filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> p.getFileName().toString().startsWith(prefix))
                        .sorted((p1, p2) -> {
                            try {
                                return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                            } catch (IOException e) { return 0; }
                        })
                        .map(p -> p.getFileName().toString())
                        .findFirst().orElse(null);
            }
        } catch (IOException e) {
            System.err.println("查找最新会话失败: " + e.getMessage());
            return null;
        }
    }

    /** 生成一个新的会话文件名（不会立即创建文件）。 */
    public String generateNewSessionFilename(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String base = (prefix == null ? "" : prefix);
        return base + "session_" + ts + ".json";
    }

    /**
     * 将旧结构下 (history 根目录) 的会话 *.json 文件迁移到新目录 history/message。
     * 规则：
     *  - 仅移动扩展名为 .json 的文件。
     *  - 如果新目录已存在同名文件则跳过该文件。
     *  - 移动失败会尝试复制，成功后删除旧文件。
     */
    private void migrateLegacySessions() {
        try {
            Path legacyDir = Paths.get(historyDir);
            Path newDir = Paths.get(messageDir);
            if (!Files.exists(legacyDir)) return;
            if (!Files.exists(newDir)) Files.createDirectories(newDir);
            int moved = 0;
            try (Stream<Path> stream = Files.list(legacyDir)) {
                for (Path p : stream.toList()) {
                    if (Files.isDirectory(p)) continue; // 跳过子目录
                    String name = p.getFileName().toString();
                    if (!name.endsWith(".json")) continue; // 只处理 json
                    Path target = newDir.resolve(name);
                    if (Files.exists(target)) {
                        // 已存在则跳过
                        continue;
                    }
                    try {
                        Files.move(p, target); // 尝试直接移动
                        moved++;
                    } catch (Exception moveEx) {
                        // 移动失败则尝试复制
                        try {
                            Files.copy(p, target);
                            Files.deleteIfExists(p);
                            moved++;
                        } catch (Exception copyEx) {
                            System.err.println("迁移会话文件失败: " + name + " => " + copyEx.getMessage());
                        }
                    }
                }
            }
            if (moved > 0) {
                System.out.println("旧会话迁移完成，移动数量: " + moved);
            }
        } catch (Exception e) {
            System.err.println("迁移旧会话数据异常: " + e.getMessage());
        }
    }

    /**
     * 删除所有以指定前缀开头的会话文件（例如 personaId + "__"）。返回删除数量。
     */
    public int deleteAllSessionsByPrefix(String prefix) {
        if (prefix == null) return 0;
        int deleted = 0;
        try {
            Path dir = Paths.get(messageDir);
            if (!Files.exists(dir)) return 0;
            try (Stream<Path> stream = Files.list(dir)) {
                for (Path p : stream.toList()) {
                    String name = p.getFileName().toString();
                    if (!name.endsWith(".json")) continue;
                    if (!name.startsWith(prefix)) continue;
                    try {
                        if (Files.deleteIfExists(p)) deleted++;
                    } catch (IOException ex) {
                        System.err.println("删除会话失败: " + name + ", " + ex.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("批量删除会话失败: " + e.getMessage());
        }
        return deleted;
    }
}

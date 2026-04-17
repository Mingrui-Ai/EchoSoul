package model;

import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.UUID;

public class Message {
    public enum Role {
        USER, ASSISTANT, SYSTEM
    }

    private final String id;
    private final Role role;
    private final String content;
    private final LocalDateTime timestamp;

    public Message(Role role, String content) {
        this(UUID.randomUUID().toString(), role, content, LocalDateTime.now());
    }

    // 用于反序列化的构造函数
    public Message(Role role, String content, LocalDateTime timestamp) {
        this(UUID.randomUUID().toString(), role, content, timestamp);
    }

    public Message(String id, Role role, String content, LocalDateTime timestamp) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp == null ? LocalDateTime.now() : timestamp;
    }

    // Getters
    public String getId() { return id; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public String getFormattedTime() {
        return timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    // 转换为JSON对象
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("role", role.toString());
        json.addProperty("content", content);
        json.addProperty("timestamp", timestamp.toString());
        return json;
    }
    // 从JSON对象创建Message
    public static Message fromJson(JsonObject json) {
        Role role = Role.valueOf(json.get("role").getAsString());
        String content = json.get("content").getAsString();
        LocalDateTime timestamp = LocalDateTime.parse(json.get("timestamp").getAsString());
        String id = json.has("id") && !json.get("id").isJsonNull()
                ? json.get("id").getAsString()
                : null;
        return new Message(id, role, content, timestamp);
    }
}


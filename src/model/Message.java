package model;

import java.time.LocalDateTime;

public class Message {
    public enum Role {
        USER, ASSISTANT, SYSTEM
    }

    private final Role role;
    private final String content;
    private final LocalDateTime timestamp;

    public Message(Role role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    // 用于反序列化的构造函数
    public Message(Role role, String content, LocalDateTime timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    // Getters
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public String getFormattedTime() {
        return timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    public String getFormattedDate() {
        return timestamp.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    // 转换为JSON对象
    public com.google.gson.JsonObject toJson() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("role", role.toString());
        json.addProperty("content", content);
        json.addProperty("timestamp", timestamp.toString());
        return json;
    }

    // 从JSON对象创建Message
    public static Message fromJson(com.google.gson.JsonObject json) {
        Role role = Role.valueOf(json.get("role").getAsString());
        String content = json.get("content").getAsString();
        LocalDateTime timestamp = LocalDateTime.parse(json.get("timestamp").getAsString());
        return new Message(role, content, timestamp);
    }
}


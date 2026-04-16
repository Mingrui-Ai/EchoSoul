package model;

import java.time.LocalDateTime;
import java.util.List;

public class ChatSession {
    private final String sessionName;
    private final LocalDateTime saveTime;
    private final int messageCount;
    private final List<Message> messages;
    private final String filename;

    public ChatSession(String sessionName, LocalDateTime saveTime, int messageCount,
                       List<Message> messages, String filename) {
        this.sessionName = sessionName;
        this.saveTime = saveTime;
        this.messageCount = messageCount;
        this.messages = messages;
        this.filename = filename;
    }

    // Getters
    public String getSessionName() { return sessionName; }
    public LocalDateTime getSaveTime() { return saveTime; }
    public int getMessageCount() { return messageCount; }
    public List<Message> getMessages() { return messages; }
    public String getFilename() { return filename; }

    public String getFormattedTime() {
        return saveTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    // 检查是否包含关键词
    public boolean containsKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase();
        return sessionName.toLowerCase().contains(lowerKeyword) ||
                messages.stream().anyMatch(msg ->
                        msg.getContent().toLowerCase().contains(lowerKeyword));
    }

    @Override
    public String toString() {
        return String.format("%s (%d条消息, %s)",
                sessionName, messageCount, getFormattedTime());
    }
}


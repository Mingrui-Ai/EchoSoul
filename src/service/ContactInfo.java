package service;

import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 持久化的联系人 / 人格 信息实体 */
public class ContactInfo {
    private String id;
    private String displayName;
    private String remark;
    private String avatarPath; // 资源路径或文件绝对路径
    private boolean ai;        // 是否 AI 联系人
    private String systemPrompt;
    private String greeting;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private boolean userDefined; // 是否用户自定义（内置的不保存到文件，或保存为 false）

    public ContactInfo() {}

    public ContactInfo(String id, String displayName, String remark, String avatarPath,
                       boolean ai, String systemPrompt, String greeting, boolean userDefined) {
        this.id = id;
        this.displayName = displayName;
        this.remark = remark;
        this.avatarPath = avatarPath;
        this.ai = ai;
        this.systemPrompt = systemPrompt;
        this.greeting = greeting;
        this.userDefined = userDefined;
        this.createTime = LocalDateTime.now();
        this.updateTime = this.createTime;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getRemark() { return remark; }
    public String getAvatarPath() { return avatarPath; }
    public boolean isAi() { return ai; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getGreeting() { return greeting; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public boolean isUserDefined() { return userDefined; }

    public void setDisplayName(String displayName) { this.displayName = displayName; touch(); }
    public void setRemark(String remark) { this.remark = remark; touch(); }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; touch(); }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; touch(); }
    public void setGreeting(String greeting) { this.greeting = greeting; touch(); }

    private void touch() { this.updateTime = LocalDateTime.now(); }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("displayName", displayName);
        obj.addProperty("remark", remark);
        obj.addProperty("avatarPath", avatarPath);
        obj.addProperty("ai", ai);
        obj.addProperty("systemPrompt", systemPrompt);
        obj.addProperty("greeting", greeting);
        obj.addProperty("createTime", createTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        obj.addProperty("updateTime", updateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        obj.addProperty("userDefined", userDefined);
        return obj;
    }

    public static ContactInfo fromJson(JsonObject obj) {
        ContactInfo ci = new ContactInfo();
        ci.id = getString(obj, "id", "");
        ci.displayName = getString(obj, "displayName", "");
        ci.remark = getString(obj, "remark", "");
        ci.avatarPath = getString(obj, "avatarPath", "");
        ci.ai = obj.has("ai") && obj.get("ai").getAsBoolean();
        ci.systemPrompt = getString(obj, "systemPrompt", "");
        ci.greeting = getString(obj, "greeting", "");
        ci.userDefined = obj.has("userDefined") && obj.get("userDefined").getAsBoolean();
        try {
            ci.createTime = obj.has("createTime") ? LocalDateTime.parse(obj.get("createTime").getAsString()) : LocalDateTime.now();
            ci.updateTime = obj.has("updateTime") ? LocalDateTime.parse(obj.get("updateTime").getAsString()) : ci.createTime;
        } catch (Exception ignored) { ci.createTime = LocalDateTime.now(); ci.updateTime = ci.createTime; }
        return ci;
    }

    private static String getString(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }
}


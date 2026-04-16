package service;

import java.io.*;
import java.util.Properties;
import java.util.*;

public class ConfigManager {
    private static final String CONFIG_FILE = "config.properties";
    private static final String KEY_PINNED = "contacts.pinned";
    private static final String KEY_CONTACT_REMARK_PREFIX = "contact.remark.";
    private static ConfigManager instance;
    private Properties properties;
    private File externalConfigFile;

    private ConfigManager() {
        loadProperties();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private void loadProperties() {
        properties = new Properties();
        // first try external config in working dir
        externalConfigFile = new File(System.getProperty("user.dir"), CONFIG_FILE);
        if (externalConfigFile.exists()) {
            try (InputStream input = new FileInputStream(externalConfigFile)) {
                if (input != null) {
                    properties.load(input);
                }
                return;
            } catch (IOException e) {
                System.err.println("加载外部配置失败: " + e.getMessage());
            }
        }

        // fallback: load from classpath resource
        try (InputStream input = getClass().getResourceAsStream("/" + CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
            } else {
                System.err.println("配置文件未找到: " + CONFIG_FILE + "，将使用默认配置");
            }
        } catch (IOException e) {
            System.err.println("加载配置文件失败: " + e.getMessage());
        }
    }

    // persist properties to external config file (user.dir/config.properties)
    public synchronized void save() {
        if (externalConfigFile == null) externalConfigFile = new File(System.getProperty("user.dir"), CONFIG_FILE);
        try (OutputStream out = new FileOutputStream(externalConfigFile)) {
            properties.store(out, "AI Chat Configuration");
        } catch (IOException e) {
            System.err.println("保存配置失败: " + e.getMessage());
        }
    }

    // DEEPSEEK API配置 (保留原方法)
    public String getApiKey() {
        return properties.getProperty("deepseek.api.key", "");
    }

    public void setDeepseekApiKey(String key) {
        if (key != null) properties.setProperty("deepseek.api.key", key);
        save();
    }

    public String getApiUrl() {
        return properties.getProperty("deepseek.api.url", "https://api.deepseek.com/v1/chat/completions");
    }

    // Baidu credentials (可读写)
    public String getBaiduAppId() {
        return properties.getProperty("baidu.app.id", "");
    }

    public String getBaiduApiKey() {
        return properties.getProperty("baidu.api.key", "");
    }

    public String getBaiduSecretKey() {
        return properties.getProperty("baidu.secret.key", "");
    }

    public void setBaiduCredentials(String appId, String apiKey, String secretKey) {
        if (appId != null) properties.setProperty("baidu.app.id", appId);
        if (apiKey != null) properties.setProperty("baidu.api.key", apiKey);
        if (secretKey != null) properties.setProperty("baidu.secret.key", secretKey);
        save();
    }

    // 模型参数
    public double getTemperature() {
        return Double.parseDouble(properties.getProperty("temperature", "0.7"));
    }

    public int getMaxTokens() {
        return Integer.parseInt(properties.getProperty("max_tokens", "150"));
    }

    // 超时设置
    public int getConnectTimeout() {
        return Integer.parseInt(properties.getProperty("connect.timeout", "10"));
    }

    public int getReadTimeout() {
        return Integer.parseInt(properties.getProperty("read.timeout", "30"));
    }

    // 应用设置
    public int getHistoryLimit() {
        return Integer.parseInt(properties.getProperty("history.limit", "20"));
    }

    public String getAppName() {
        return properties.getProperty("app.name", "EchoSoul");
    }

    public String getAppVersion() {
        return properties.getProperty("app.version", "1.0");
    }

    // TTS 自动播放配置（默认开启）
    public boolean isAutoPlayEnabled() {
        return Boolean.parseBoolean(properties.getProperty("tts.autoplay", "true"));
    }

    public void setAutoPlayEnabled(boolean enabled) {
        properties.setProperty("tts.autoplay", Boolean.toString(enabled));
        save();
    }

    // 侧边栏折叠状态（默认折叠：true）
    public boolean isSidebarCollapsed() {
        return Boolean.parseBoolean(properties.getProperty("sidebar.collapsed", "true"));
    }

    public void setSidebarCollapsed(boolean collapsed) {
        properties.setProperty("sidebar.collapsed", Boolean.toString(collapsed));
        save();
    }

    // 置顶联系人存储：使用逗号分隔的 id 列表
    public synchronized Set<String> getPinnedContacts() {
        String v = properties.getProperty(KEY_PINNED, "");
        Set<String> set = new LinkedHashSet<>();
        if (v != null && !v.isBlank()) {
            for (String s : v.split(",")) {
                String id = s.trim();
                if (!id.isEmpty()) set.add(id);
            }
        }
        return set;
    }

    public synchronized void setPinnedContacts(Set<String> pinned) {
        if (pinned == null || pinned.isEmpty()) {
            properties.remove(KEY_PINNED);
        } else {
            String joined = String.join(",", pinned);
            properties.setProperty(KEY_PINNED, joined);
        }
        save();
    }

    public boolean isPinned(String contactId) {
        if (contactId == null || contactId.isBlank()) return false;
        return getPinnedContacts().contains(contactId);
    }

    public synchronized void setPinned(String contactId, boolean pinned) {
        if (contactId == null || contactId.isBlank()) return;
        Set<String> set = getPinnedContacts();
        if (pinned) set.add(contactId); else set.remove(contactId);
        setPinnedContacts(set);
    }

    public String getContactRemark(String contactId) {
        if (contactId == null || contactId.isBlank()) return "";
        return properties.getProperty(KEY_CONTACT_REMARK_PREFIX + contactId, "");
    }

    public synchronized void setContactRemark(String contactId, String remark) {
        if (contactId == null || contactId.isBlank()) return;
        String key = KEY_CONTACT_REMARK_PREFIX + contactId;
        if (remark == null || remark.isBlank()) {
            properties.remove(key);
        } else {
            properties.setProperty(key, remark);
        }
        save();
    }

    public String getUserAvatarPath() {
        return properties.getProperty("user.avatar.path", "/images/user_avatar.png");
    }

    public void setUserAvatarPath(String path) {
        if (path == null || path.isBlank()) return;
        properties.setProperty("user.avatar.path", path);
        save();
    }
}

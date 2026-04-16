package service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public class ConfigManager {
    private static final String CONFIG_FILE = "config.properties";
    private static final String CONFIG_TEMPLATE_RESOURCE = "config.example.properties";
    private static final String KEY_PINNED = "contacts.pinned";
    private static final String KEY_CONTACT_REMARK_PREFIX = "contact.remark.";

    private static volatile ConfigManager instance;

    private final Properties properties = new Properties();
    private final Path externalConfigPath;

    private ConfigManager() {
        this.externalConfigPath = Path.of(System.getProperty("user.dir")).resolve(CONFIG_FILE);
        loadProperties();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }

    private synchronized void loadProperties() {
        properties.clear();
        ensureExternalConfigExists();

        if (!Files.exists(externalConfigPath)) {
            loadTemplateDefaults();
            return;
        }

        try (Reader reader = Files.newBufferedReader(externalConfigPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            System.err.println("Failed to load external config: " + e.getMessage());
            loadTemplateDefaults();
        }
    }

    private void ensureExternalConfigExists() {
        if (Files.exists(externalConfigPath)) {
            return;
        }

        try {
            Path parent = externalConfigPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (InputStream templateStream = getTemplateStream()) {
                if (templateStream == null) {
                    System.err.println("Missing bundled config template: " + CONFIG_TEMPLATE_RESOURCE);
                    return;
                }
                Files.copy(templateStream, externalConfigPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("Failed to create external config template: " + e.getMessage());
        }
    }

    private void loadTemplateDefaults() {
        try (InputStream templateStream = getTemplateStream()) {
            if (templateStream == null) {
                System.err.println("Missing bundled config template: " + CONFIG_TEMPLATE_RESOURCE);
                return;
            }
            properties.load(templateStream);
        } catch (IOException e) {
            System.err.println("Failed to load bundled config template: " + e.getMessage());
        }
    }

    private InputStream getTemplateStream() {
        return ConfigManager.class.getClassLoader().getResourceAsStream(CONFIG_TEMPLATE_RESOURCE);
    }

    public synchronized void save() {
        try {
            Path parent = externalConfigPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (Writer writer = Files.newBufferedWriter(externalConfigPath, StandardCharsets.UTF_8)) {
                properties.store(writer, "EchoSoul external configuration");
            }
        } catch (IOException e) {
            System.err.println("Failed to save external config: " + e.getMessage());
        }
    }

    public Path getExternalConfigPath() {
        return externalConfigPath;
    }

    public synchronized String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public synchronized void setProperty(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }

        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
        save();
    }

    private synchronized void setPropertiesAndSave(String[][] entries) {
        for (String[] entry : entries) {
            if (entry == null || entry.length != 2) {
                continue;
            }

            String key = entry[0];
            String value = entry[1];
            if (key == null || key.isBlank()) {
                continue;
            }

            if (value == null) {
                properties.remove(key);
            } else {
                properties.setProperty(key, value);
            }
        }
        save();
    }

    public String getApiKey() {
        return properties.getProperty("deepseek.api.key", "");
    }

    public void setDeepseekApiKey(String key) {
        setProperty("deepseek.api.key", key);
    }

    public String getApiUrl() {
        return properties.getProperty("deepseek.api.url", "https://api.deepseek.com/v1/chat/completions");
    }

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
        setPropertiesAndSave(new String[][]{
                {"baidu.app.id", appId},
                {"baidu.api.key", apiKey},
                {"baidu.secret.key", secretKey}
        });
    }

    public double getTemperature() {
        return Double.parseDouble(properties.getProperty("temperature", "0.7"));
    }

    public int getMaxTokens() {
        return Integer.parseInt(properties.getProperty("max_tokens", "150"));
    }

    public int getConnectTimeout() {
        return Integer.parseInt(properties.getProperty("connect.timeout", "15"));
    }

    public int getReadTimeout() {
        return Integer.parseInt(properties.getProperty("read.timeout", "60"));
    }

    public int getWriteTimeout() {
        return Integer.parseInt(properties.getProperty("write.timeout", "60"));
    }

    public int getHistoryLimit() {
        return Integer.parseInt(properties.getProperty("history.limit", "20"));
    }

    public String getAppName() {
        return properties.getProperty("app.name", "EchoSoul");
    }

    public String getAppVersion() {
        return properties.getProperty("app.version", "1.0");
    }

    public boolean isAutoPlayEnabled() {
        return Boolean.parseBoolean(properties.getProperty("tts.autoplay", "true"));
    }

    public void setAutoPlayEnabled(boolean enabled) {
        setProperty("tts.autoplay", Boolean.toString(enabled));
    }

    public boolean isSidebarCollapsed() {
        return Boolean.parseBoolean(properties.getProperty("sidebar.collapsed", "true"));
    }

    public void setSidebarCollapsed(boolean collapsed) {
        setProperty("sidebar.collapsed", Boolean.toString(collapsed));
    }

    public synchronized Set<String> getPinnedContacts() {
        String value = properties.getProperty(KEY_PINNED, "");
        Set<String> result = new LinkedHashSet<>();
        if (value != null && !value.isBlank()) {
            for (String rawId : value.split(",")) {
                String contactId = rawId.trim();
                if (!contactId.isEmpty()) {
                    result.add(contactId);
                }
            }
        }
        return result;
    }

    public synchronized void setPinnedContacts(Set<String> pinned) {
        if (pinned == null || pinned.isEmpty()) {
            properties.remove(KEY_PINNED);
        } else {
            properties.setProperty(KEY_PINNED, String.join(",", pinned));
        }
        save();
    }

    public boolean isPinned(String contactId) {
        if (contactId == null || contactId.isBlank()) {
            return false;
        }
        return getPinnedContacts().contains(contactId);
    }

    public synchronized void setPinned(String contactId, boolean pinned) {
        if (contactId == null || contactId.isBlank()) {
            return;
        }

        Set<String> current = getPinnedContacts();
        if (pinned) {
            current.add(contactId);
        } else {
            current.remove(contactId);
        }
        setPinnedContacts(current);
    }

    public String getContactRemark(String contactId) {
        if (contactId == null || contactId.isBlank()) {
            return "";
        }
        return properties.getProperty(KEY_CONTACT_REMARK_PREFIX + contactId, "");
    }

    public synchronized void setContactRemark(String contactId, String remark) {
        if (contactId == null || contactId.isBlank()) {
            return;
        }

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
        if (path == null || path.isBlank()) {
            return;
        }
        setProperty("user.avatar.path", path);
    }
}

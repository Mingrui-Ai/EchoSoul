package service;

import service.ai.AiChatConfig;
import service.ai.AiProtocol;
import service.ai.AiProviderPreset;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private synchronized String getFirstConfiguredValue(String... keys) {
        for (String key : keys) {
            String value = trimToNull(properties.getProperty(key));
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue)).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private double getDoubleProperty(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, Double.toString(defaultValue)).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%s", value);
    }

    public AiProviderPreset getAiProviderPreset() {
        return AiProviderPreset.fromConfigValue(
                getFirstConfiguredValue("ai.provider.preset", "ai.provider")
        );
    }

    public AiProtocol getAiProtocol() {
        String configured = getFirstConfiguredValue("ai.protocol");
        if (!configured.isBlank()) {
            return AiProtocol.fromConfigValue(configured);
        }
        return getAiProviderPreset().getProtocol();
    }

    public String getAiApiKey() {
        return getFirstConfiguredValue("ai.api.key", "deepseek.api.key");
    }

    public void setAiApiKey(String apiKey) {
        setProperty("ai.api.key", trimToNull(apiKey));
    }

    public String getAiBaseUrl() {
        String configured = getFirstConfiguredValue("ai.base.url", "ai.api.url", "deepseek.api.url");
        if (!configured.isBlank()) {
            return configured;
        }
        return getAiProviderPreset().getDefaultBaseUrl();
    }

    public String getAiModel() {
        String configured = getFirstConfiguredValue("ai.model");
        if (!configured.isBlank()) {
            return configured;
        }
        return getAiProviderPreset().getDefaultModel();
    }

    public AiChatConfig getAiChatConfig() {
        AiProviderPreset preset = getAiProviderPreset();
        AiProtocol protocol = getAiProtocol();
        return new AiChatConfig(
                preset,
                protocol,
                getAiApiKey(),
                getAiBaseUrl(),
                getAiModel(),
                getTemperature(),
                getMaxTokens(),
                getConnectTimeout(),
                getReadTimeout(),
                getWriteTimeout()
        );
    }

    public void setAiSettings(
            AiProviderPreset preset,
            AiProtocol protocol,
            String apiKey,
            String baseUrl,
            String model,
            double temperature,
            int maxTokens,
            int connectTimeout,
            int readTimeout,
            int writeTimeout
    ) {
        AiProviderPreset effectivePreset = preset == null ? AiProviderPreset.DEEPSEEK : preset;
        AiProtocol effectiveProtocol = protocol == null ? effectivePreset.getProtocol() : protocol;

        setPropertiesAndSave(new String[][]{
                {"ai.provider.preset", effectivePreset.name()},
                {"ai.protocol", effectiveProtocol.name()},
                {"ai.api.key", trimToNull(apiKey)},
                {"ai.base.url", trimToNull(baseUrl)},
                {"ai.model", trimToNull(model)},
                {"temperature", formatDouble(temperature)},
                {"max_tokens", Integer.toString(maxTokens)},
                {"connect.timeout", Integer.toString(connectTimeout)},
                {"read.timeout", Integer.toString(readTimeout)},
                {"write.timeout", Integer.toString(writeTimeout)}
        });
    }

    public String getApiKey() {
        return getAiApiKey();
    }

    public void setDeepseekApiKey(String key) {
        setAiApiKey(key);
    }

    public String getApiUrl() {
        return getAiBaseUrl();
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
                {"baidu.app.id", trimToNull(appId)},
                {"baidu.api.key", trimToNull(apiKey)},
                {"baidu.secret.key", trimToNull(secretKey)}
        });
    }

    public double getTemperature() {
        return getDoubleProperty("temperature", 0.7);
    }

    public int getMaxTokens() {
        return getIntProperty("max_tokens", 150);
    }

    public int getConnectTimeout() {
        return getIntProperty("connect.timeout", 15);
    }

    public int getReadTimeout() {
        return getIntProperty("read.timeout", 60);
    }

    public int getWriteTimeout() {
        return getIntProperty("write.timeout", 60);
    }

    public int getHistoryLimit() {
        return getIntProperty("history.limit", 20);
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

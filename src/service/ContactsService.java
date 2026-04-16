package service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 联系人持久化服务：负责加载、保存、添加、删除、列出联系人。
 * 新存储位置：history/contacts/{id}.json
 * 向后兼容：
 *  1. 若存在 history/contacts.json 或 history/outContacts/contacts.json 迁移到目录式存储。
 *  2. 若存在 history/outContacts/contacts/{id}.json 也会被读取并迁移到新目录。
 */
public class ContactsService {
    private static final String LEGACY_FILENAME = "contacts.json"; // 用于旧单文件结构
    private static final String PER_CONTACT_DIR = "contacts";
    private static ContactsService instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path legacyFilePath; // history/contacts.json（最旧结构）
    private final Path legacyOutContactsFilePath; // history/outContacts/contacts.json（过渡结构单文件）
    private final Path legacyOutContactsDirPath; // history/outContacts/contacts/（过渡结构目录）
    private final Path perContactDirPath; // history/contacts/（新结构目录）

    private ContactsService() {
        HistoryManager hm = HistoryManager.getInstance();
        String contactsRoot = hm.getContactsDirectoryPath(); // 新根目录 history/contacts
        String outContactsRoot = hm.getOutContactsDirectoryPath(); // 旧根目录 history/outContacts
        this.legacyFilePath = Paths.get(hm.getHistoryDirectoryPath(), LEGACY_FILENAME);
        this.legacyOutContactsFilePath = Paths.get(outContactsRoot, LEGACY_FILENAME);
        this.legacyOutContactsDirPath = Paths.get(outContactsRoot, PER_CONTACT_DIR);
        this.perContactDirPath = Paths.get(contactsRoot); // 新目录直接作为 contacts 根
        try { Files.createDirectories(this.perContactDirPath); } catch (Exception ignored) {}
        migrateLegacyContacts();
    }

    public static ContactsService getInstance() {
        if (instance == null) instance = new ContactsService();
        return instance;
    }

    /**
     * 从目录式存储加载全部联系人；若目录为空且存在旧文件，则先迁移再加载。
     */
    public synchronized List<ContactInfo> loadAll() {
        try {
            List<ContactInfo> list = new ArrayList<>();
            if (Files.exists(perContactDirPath) && Files.isDirectory(perContactDirPath)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(perContactDirPath, "*.json")) {
                    for (Path p : stream) {
                        try (FileReader fr = new FileReader(p.toFile())) {
                            JsonObject obj = gson.fromJson(fr, JsonObject.class);
                            if (obj != null) {
                                ContactInfo ci = ContactInfo.fromJson(obj);
                                // 修复头像路径
                                ci.setAvatarPath(normalizeAvatarPath(ci.getAvatarPath()));
                                list.add(ci);
                            }
                        } catch (Exception ex) {
                            System.err.println("读取联系人文件失败: " + p + " => " + ex.getMessage());
                        }
                    }
                }
                return list;
            }
            // 回退（极少情况）：目录不存在时尝试旧文件
            if (Files.exists(legacyFilePath)) {
                try (FileReader fr = new FileReader(legacyFilePath.toFile())) {
                    JsonObject root = gson.fromJson(fr, JsonObject.class);
                    JsonArray arr = root != null && root.has("contacts") ? root.getAsJsonArray("contacts") : new JsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        ContactInfo ci = ContactInfo.fromJson(arr.get(i).getAsJsonObject());
                        ci.setAvatarPath(normalizeAvatarPath(ci.getAvatarPath()));
                        list.add(ci);
                    }
                } catch (Exception e) {
                    System.err.println("读取旧联系人文件失败: " + e.getMessage());
                }
            }
            return list;
        } catch (Exception e) {
            System.err.println("读取联系人失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 将联系人写入其独立文件 {id}.json。
     */
    private void saveOne(ContactInfo c) {
        if (c == null || c.getId() == null || c.getId().isBlank()) return;
        try {
            Path file = perContactDirPath.resolve(c.getId() + ".json");
            try (FileWriter fw = new FileWriter(file.toFile())) {
                gson.toJson(c.toJson(), fw);
            }
        } catch (Exception e) {
            System.err.println("保存联系人失败: " + c.getId() + ", " + e.getMessage());
        }
    }

    /**
     * 添加新联系人：检查 id 与名称（忽略大小写、去空格）唯一性；写入独立文件。
     */
    public synchronized boolean add(ContactInfo c) {
        List<ContactInfo> all = loadAll();
        String newId = c.getId() == null ? "" : c.getId().trim();
        String newName = normalizeName(c.getDisplayName());
        for (ContactInfo x : all) {
            String xid = x.getId() == null ? "" : x.getId().trim();
            String xname = normalizeName(x.getDisplayName());
            if (!xid.isEmpty() && xid.equalsIgnoreCase(newId)) {
                System.err.println("重复的联系人 ID: " + newId);
                return false;
            }
            if (!xname.isEmpty() && !newName.isEmpty() && xname.equalsIgnoreCase(newName)) {
                System.err.println("重复的联系人名称: " + c.getDisplayName());
                return false;
            }
        }
        // 保存前规范化头像路径
        c.setAvatarPath(normalizeAvatarPath(c.getAvatarPath()));
        saveOne(c);
        return true;
    }

    private String normalizeName(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace('\u00A0', ' '); // nbsp
        t = t.replaceAll("\\s+", " ");
        return t;
    }

    public synchronized void deleteById(String id) {
        try {
            if (id == null || id.isBlank()) return;
            Path file = perContactDirPath.resolve(id + ".json");
            Files.deleteIfExists(file);
            // 同时删除该联系人的聊天记录（按前缀 id__）
            try {
                HistoryManager hm = HistoryManager.getInstance();
                int count = hm.deleteAllSessionsByPrefix(id + "__");
                if (count > 0) System.out.println("已删除关联聊天记录数量: " + count);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            System.err.println("删除联系人失败: " + e.getMessage());
        }
    }

    /** 更新联系人头像（仅针对已持久化的自定义联系人）。成功返回 true，失败或未找到返回 false */
    public synchronized boolean updateAvatar(String id, String newAvatarPath) {
        if (id == null || id.isBlank()) return false;
        List<ContactInfo> all = loadAll();
        for (ContactInfo ci : all) {
            if (ci.getId().equalsIgnoreCase(id)) {
                ci.setAvatarPath(normalizeAvatarPath(newAvatarPath));
                saveOne(ci);
                return true;
            }
        }
        return false;
    }

    /**
     * 迁移旧版 history/outContacts/contacts.json 到 history/outContacts/contacts/{id}.json
     * 若新目录已有内容则跳过迁移；迁移成功后保留旧文件作为备份（不删除）。
     */
    private void migrateLegacyContacts() {
        try {
            // 如果新目录已有文件则视为已迁移
            boolean newDirHasFiles = false;
            if (Files.exists(perContactDirPath)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(perContactDirPath, "*.json")) {
                    for (Path ignored : stream) { newDirHasFiles = true; break; }
                } catch (Exception ignored) {}
            }
            if (newDirHasFiles) return;
            List<ContactInfo> allLegacy = new ArrayList<>();
            // 读取最旧单文件 history/contacts.json
            if (Files.exists(legacyFilePath)) {
                try (FileReader fr = new FileReader(legacyFilePath.toFile())) {
                    JsonObject root = gson.fromJson(fr, JsonObject.class);
                    JsonArray arr = root != null && root.has("contacts") ? root.getAsJsonArray("contacts") : new JsonArray();
                    for (int i = 0; i < arr.size(); i++) allLegacy.add(ContactInfo.fromJson(arr.get(i).getAsJsonObject()));
                } catch (Exception e) { System.err.println("读取 legacy contacts.json 失败: " + e.getMessage()); }
            }
            // 读取过渡单文件 history/outContacts/contacts.json
            if (Files.exists(legacyOutContactsFilePath)) {
                try (FileReader fr = new FileReader(legacyOutContactsFilePath.toFile())) {
                    JsonObject root = gson.fromJson(fr, JsonObject.class);
                    JsonArray arr = root != null && root.has("contacts") ? root.getAsJsonArray("contacts") : new JsonArray();
                    for (int i = 0; i < arr.size(); i++) allLegacy.add(ContactInfo.fromJson(arr.get(i).getAsJsonObject()));
                } catch (Exception e) { System.err.println("读取 outContacts/contacts.json 失败: " + e.getMessage()); }
            }
            // 读取过渡目录 history/outContacts/contacts/*.json
            if (Files.exists(legacyOutContactsDirPath) && Files.isDirectory(legacyOutContactsDirPath)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(legacyOutContactsDirPath, "*.json")) {
                    for (Path p : stream) {
                        try (FileReader fr = new FileReader(p.toFile())) {
                            JsonObject obj = gson.fromJson(fr, JsonObject.class);
                            if (obj != null) allLegacy.add(ContactInfo.fromJson(obj));
                        } catch (Exception ex) { System.err.println("读取过渡联系人文件失败: " + p + " => " + ex.getMessage()); }
                    }
                } catch (Exception ignored) {}
            }
            // 去重（按 id 或名称规范化后）
            Map<String, ContactInfo> dedup = new LinkedHashMap<>();
            for (ContactInfo ci : allLegacy) {
                String key = (ci.getId() != null && !ci.getId().isBlank()) ? ci.getId().trim().toLowerCase() : normalizeName(ci.getDisplayName()).toLowerCase();
                if (!dedup.containsKey(key)) dedup.put(key, ci);
            }
            int migrated = 0;
            for (ContactInfo ci : dedup.values()) {
                ci.setAvatarPath(normalizeAvatarPath(ci.getAvatarPath()));
                saveOne(ci);
                migrated++;
            }
            if (migrated > 0) System.out.println("已迁移旧联系人到新目录 history/contacts 数量: " + migrated);
            // === 新增：迁移后清理 legacy outContacts 目录与文件（若已无保留价值） ===
            try {
                // 删除过渡单文件（若存在且已迁移）
                if (migrated > 0 && Files.exists(legacyOutContactsFilePath)) {
                    try { Files.deleteIfExists(legacyOutContactsFilePath); } catch (Exception ignored) {}
                }
                // 判断过渡目录是否为空，空则删除
                if (Files.exists(legacyOutContactsDirPath) && Files.isDirectory(legacyOutContactsDirPath)) {
                    boolean empty = true;
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(legacyOutContactsDirPath)) {
                        for (Path p : ds) { empty = false; break; }
                    } catch (Exception ignored) {}
                    if (empty) {
                        try { Files.delete(legacyOutContactsDirPath); } catch (Exception ignored) {}
                        // 同时尝试删除其父目录 outContacts（若已空）
                        Path parent = legacyOutContactsDirPath.getParent();
                        if (parent != null && Files.exists(parent)) {
                            boolean parentEmpty = true;
                            try (DirectoryStream<Path> ds2 = Files.newDirectoryStream(parent)) {
                                for (Path p : ds2) { parentEmpty = false; break; }
                            } catch (Exception ignored) {}
                            if (parentEmpty) {
                                try { Files.delete(parent); } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            } catch (Exception cleanupEx) {
                System.err.println("清理 legacy outContacts 目录失败: " + cleanupEx.getMessage());
            }
        } catch (Exception e) {
            System.err.println("迁移旧联系人文件失败: " + e.getMessage());
        }
    }

    // === 工具：修复头像路径 ===
    private String normalizeAvatarPath(String original) {
        if (original == null) return null;
        String p = original.trim();
        if (p.isEmpty()) return p;
        // 修正目录拼写
        p = p.replace("\\resourcse\\", "\\resources\\").replace("/resourcse/", "/resources/");
        if (p.startsWith("images/")) return p; // 相对类路径，交给 UI 以类路径读取
        if (p.startsWith("/images/")) return p; // 绝对类路径
        // 若为绝对文件路径，存在则返回
        try {
            java.io.File f = new java.io.File(p);
            if (f.exists()) return p;
            // 找不到时按文件名到 images 目录兜底
            String name = f.getName();
            java.io.File candidate = new java.io.File(getProjectImagesDir(), name);
            if (candidate.exists()) return candidate.getAbsolutePath();
        } catch (Exception ignored) {}
        return p;
    }

    private String getProjectImagesDir() {
        String projectDir = System.getProperty("user.dir");
        java.io.File res = new java.io.File(projectDir + java.io.File.separator + "resources" + java.io.File.separator + "images");
        if (res.exists() && res.isDirectory()) return res.getAbsolutePath();
        java.io.File old = new java.io.File(projectDir + java.io.File.separator + "resourcse" + java.io.File.separator + "images");
        return old.getAbsolutePath();
    }
}

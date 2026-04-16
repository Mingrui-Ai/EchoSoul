package service;

import javafx.scene.image.Image;

/**
 * 负责统一的资源目录路径解析与图片加载，兼容旧拼写(resourcse)与新目录(resources)。
 */
public class UIResourceService {
    public String getImagesDirPath() {
        String projectDir = System.getProperty("user.dir");
        java.io.File newDir = new java.io.File(projectDir + java.io.File.separator + "resources" + java.io.File.separator + "images");
        if (newDir.exists() && newDir.isDirectory()) return newDir.getAbsolutePath();
        java.io.File oldDir = new java.io.File(projectDir + java.io.File.separator + "resourcse" + java.io.File.separator + "images");
        if (oldDir.exists() && oldDir.isDirectory()) return oldDir.getAbsolutePath();
        if (!newDir.exists()) {
            boolean ok = newDir.mkdirs();
            if (!ok) {
                // 目录创建失败则仍返回期望路径，调用处可提示
            }
        }
        return newDir.getAbsolutePath();
    }
    public String getDatabaseDirPath() {
        String projectDir = System.getProperty("user.dir");
        java.io.File newDir = new java.io.File(projectDir + java.io.File.separator + "resources" + java.io.File.separator + "database");
        if (newDir.exists() && newDir.isDirectory()) return newDir.getAbsolutePath();
        java.io.File oldDir = new java.io.File(projectDir + java.io.File.separator + "resourcse" + java.io.File.separator + "database");
        return oldDir.getAbsolutePath();
    }
    public String normalizeImagePath(String original) {
        if (original == null) return null;
        String p = original.trim();
        if (p.isEmpty()) return p;
        p = p.replace("\\resourcse\\", "\\resources\\").replace("/resourcse/", "/resources/");
        if (p.startsWith("images/")) return "/" + p;
        if (p.startsWith("/images/")) return p;
        try {
            java.io.File f = new java.io.File(p);
            if (f.exists()) return p;
            String name = f.getName();
            java.io.File candidate = new java.io.File(getImagesDirPath(), name);
            if (candidate.exists()) return candidate.getAbsolutePath();
        } catch (Exception ignored) {}
        return p;
    }
    public Image loadImageFlexible(String path, Class<?> resourceAnchor) {
        try {
            String p = normalizeImagePath(path);
            if (p == null || p.isBlank()) {
                var is = resourceAnchor.getResourceAsStream("/images/ai_avatar.png");
                return is != null ? new Image(is) : null;
            }
            if (p.startsWith("/images/")) {
                var is = resourceAnchor.getResourceAsStream(p);
                if (is != null) return new Image(is);
            } else if (p.startsWith("images/")) {
                var is = resourceAnchor.getResourceAsStream("/" + p);
                if (is != null) return new Image(is);
            }
            java.io.File f = new java.io.File(p);
            if (f.exists()) try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) { return new Image(fis); }
            String name = new java.io.File(p).getName();
            java.io.File candidate = new java.io.File(getImagesDirPath(), name);
            if (candidate.exists()) try (java.io.FileInputStream fis = new java.io.FileInputStream(candidate)) { return new Image(fis); }
        } catch (Exception ignored) {}
        return null;
    }
    public Image loadPreviewImage(String path, double size, Class<?> resourceAnchor) {
        try {
            if (path.startsWith("/")) {
                var is = resourceAnchor.getResourceAsStream(path); if (is != null) return new Image(is, size, size, true, true);
            } else {
                java.io.File f = new java.io.File(path);
                if (f.exists()) try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) { return new Image(fis, size, size, true, true); } catch (Exception ignored) {}
            }
        } catch (Exception ignored2) {}
        javafx.scene.canvas.Canvas cv = new javafx.scene.canvas.Canvas(size, size);
        javafx.scene.canvas.GraphicsContext g = cv.getGraphicsContext2D();
        g.setFill(javafx.scene.paint.Color.web("#ececec")); g.fillRect(0,0,size,size);
        g.setStroke(javafx.scene.paint.Color.web("#bbbbbb")); g.strokeRect(0.5,0.5,size-1,size-1);
        return cv.snapshot(null, null);
    }
}

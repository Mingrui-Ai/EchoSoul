package service;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.TilePane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Node;
import java.util.List;
import java.util.function.Function;

/**
 * 图片画廊复用：上传、删除、选择。依赖 UIResourceService 进行图片加载与路径规范化。
 */
public class GalleryService {
    private final UIResourceService resourceService = new UIResourceService();

    public ScrollPane buildPreviewGallery(List<String> paths,
                                          int thumbSize,
                                          int columns,
                                          boolean selectable,
                                          String preselectPath,
                                          boolean manageEnabled,
                                          boolean cropOnUpload,
                                          String imagesDirPath,
                                          String[] selectedOut,
                                          Class<?> resourceAnchor,
                                          ScrollPane reuse) {
        TilePane tile = new TilePane();
        tile.setPadding(new Insets(10));
        tile.setHgap(12);
        tile.setVgap(12);
        tile.setPrefColumns(columns);
        final String normalStyle = "-fx-border-color:#ddd; -fx-border-radius:6; -fx-padding:6; -fx-background-color:#ffffff;";
        final String selectedStyle = "-fx-border-color:#4caf50; -fx-border-width:2; -fx-border-radius:6; -fx-padding:6; -fx-background-color:#e8f5e9;";
        Function<String, VBox> makeCell = (String path) -> {
            Image img = resourceService.loadPreviewImage(path, thumbSize, resourceAnchor);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(thumbSize); iv.setFitHeight(thumbSize); iv.setPreserveRatio(true);
            VBox box = new VBox(4, iv);
            box.setAlignment(Pos.CENTER);
            box.setStyle(normalStyle);
            box.setUserData(path);
            if (selectable) {
                box.setOnMouseClicked(event -> {
                    for (Node nd : tile.getChildren()) if (nd != box) nd.setStyle(normalStyle);
                    box.setStyle(selectedStyle);
                    if (selectedOut != null && selectedOut.length > 0) selectedOut[0] = path;
                });
            }
            return box;
        };
        for (String path : paths) tile.getChildren().add(makeCell.apply(path));
        if (selectable && preselectPath != null) {
            for (Node nd : tile.getChildren()) {
                Object ud = nd.getUserData();
                if (ud != null && preselectPath.equals(ud.toString())) {
                    nd.setStyle(selectedStyle);
                    if (selectedOut != null && selectedOut.length > 0) selectedOut[0] = ud.toString();
                    break;
                }
            }
        }
        VBox container = new VBox(8); container.setPadding(new Insets(8));
        if (manageEnabled) {
            HBox toolbar = new HBox(8); toolbar.setAlignment(Pos.CENTER_LEFT);
            Button uploadBtn = new Button("上传图片");
            Button deleteBtn = new Button("删除所选");
            toolbar.getChildren().addAll(uploadBtn, deleteBtn);
            uploadBtn.setOnAction(event -> uploadImage(paths, tile, imagesDirPath, cropOnUpload, makeCell));
            deleteBtn.setOnAction(event -> deleteSelected(paths, tile, selectedOut));
            container.getChildren().add(toolbar);
        }
        container.getChildren().add(tile);
        ScrollPane sp = reuse == null ? new ScrollPane(container) : reuse; sp.setContent(container);
        sp.setFitToWidth(true); sp.setPrefSize(640, 480);
        return sp;
    }

    private void uploadImage(List<String> paths, TilePane tile, String imagesDirPath, boolean cropOnUpload, Function<String, VBox> makeCell) {
        try {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("选择图片");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            java.io.File f = fc.showOpenDialog(null);
            if (f == null || !f.exists()) return;
            if (imagesDirPath == null || imagesDirPath.isBlank()) return;
            java.io.File imagesDir = new java.io.File(imagesDirPath);
            if (!imagesDir.exists() && !imagesDir.mkdirs()) return;
            String newName = buildTargetFileName(f.getName(), cropOnUpload);
            java.io.File dest = new java.io.File(imagesDir, newName);
            boolean ok = processUploadFile(f, dest, cropOnUpload);
            if (!ok) return;
            String newPath = dest.getAbsolutePath();
            paths.add(newPath);
            tile.getChildren().add(makeCell.apply(newPath));
        } catch (Exception ignored) {}
    }
    // 构造目标文件名（时间戳+安全化基名+裁剪标记）
    private String buildTargetFileName(String originalName, boolean crop) {
        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        String ext = dot >= 0 ? originalName.substring(dot + 1) : "png";
        String safeBase = base.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (crop) return System.currentTimeMillis() + "_" + safeBase + "_sq.png";
        return System.currentTimeMillis() + "_" + safeBase + "." + ext;
    }
    // 处理上传：裁剪为正方形（优先 AWT，失败则 JavaFX），失败则原样复制
    private boolean processUploadFile(java.io.File srcFile, java.io.File destFile, boolean crop) {
        try {
            if (!crop) {
                java.nio.file.Files.copy(srcFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            try {
                java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(srcFile);
                if (src != null) {
                    java.awt.image.BufferedImage square = cropSquare(src);
                    javax.imageio.ImageIO.write(square, "png", destFile);
                    return true;
                }
            } catch (Exception ignored) {}
            try {
                if (fxCropToSquare(srcFile, destFile)) return true;
            } catch (Exception ignoredFx) {}
            java.nio.file.Files.copy(srcFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    // AWT 正方形裁剪实现
    private java.awt.image.BufferedImage cropSquare(java.awt.image.BufferedImage src) {
        int w = src.getWidth(); int h = src.getHeight(); int size = Math.min(w, h);
        int x = (w - size) / 2; int y = (h - size) / 2;
        java.awt.image.BufferedImage square = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = square.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, size, size, x, y, x + size, y + size, null);
        g2.dispose();
        return square;
    }
    // JavaFX 裁剪兜底
    private boolean fxCropToSquare(java.io.File srcFile, java.io.File destFile) {
        try {
            javafx.scene.image.Image img = new javafx.scene.image.Image(srcFile.toURI().toString());
            int w = (int)Math.max(1, img.getWidth());
            int h = (int)Math.max(1, img.getHeight());
            int size = Math.min(w, h);
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(size, size);
            javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
            double sx = (w - size) / 2.0; double sy = (h - size) / 2.0;
            gc.drawImage(img, -sx, -sy, w, h);
            javafx.scene.image.WritableImage wi = new javafx.scene.image.WritableImage(size, size);
            canvas.snapshot(null, wi);
            javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(wi, null), "png", destFile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    private void deleteSelected(List<String> paths, TilePane tile, String[] selectedOut) {
        if (selectedOut == null || selectedOut.length == 0 || selectedOut[0] == null || selectedOut[0].isBlank()) return;
        String sel = selectedOut[0];
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(sel);
            if (p.toFile().exists()) java.nio.file.Files.delete(p);
            for (int i = 0; i < tile.getChildren().size(); i++) {
                Node nd = tile.getChildren().get(i); Object ud = nd.getUserData();
                if (ud != null && sel.equals(ud.toString())) { tile.getChildren().remove(i); break; }
            }
            paths.removeIf(s -> s.equals(sel)); selectedOut[0] = null;
        } catch (Exception ignored) {}
    }
}

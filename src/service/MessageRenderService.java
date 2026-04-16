package service;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.Node;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * 把 ChatbotUI 中的消息渲染与复制功能抽取，方便后续重用与维护。
 */
public class MessageRenderService {
    private final UIResourceService resourceService = new UIResourceService();
    private final int MAX_BUBBLE_WIDTH;

    public MessageRenderService(int maxBubbleWidth) {
        this.MAX_BUBBLE_WIDTH = maxBubbleWidth;
    }

    public Node buildAiMessage(String content, String avatarPath, LocalDateTime ts, Consumer<String> copyHandler) {
        ImageView avatar = buildAvatar(avatarPath, "/images/ai_avatar.png");
        LocalDateTime time = ts == null ? LocalDateTime.now() : ts;
        String shortTime = time.format(DateTimeFormatter.ofPattern("HH:mm"));
        String fullTime = time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        VBox msgBox = new VBox(2);
        Label timeLabel = new Label(shortTime);
        timeLabel.setFont(Font.font("Microsoft YaHei", 10));
        timeLabel.setTextFill(Color.GRAY);
        timeLabel.setTooltip(new Tooltip(fullTime));
        Label msgLabel = new Label(content);
        msgLabel.setWrapText(true);
        msgLabel.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 14));
        msgLabel.setTextFill(Color.BLACK);
        msgLabel.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 15 15 15 0; -fx-border-color: #dde; -fx-border-radius: 15 15 15 0; -fx-padding: 8 12;");
        msgLabel.setMaxWidth(MAX_BUBBLE_WIDTH);
        msgLabel.setMinHeight(Region.USE_PREF_SIZE);
        addCopyContextMenu(msgLabel, content, copyHandler);
        msgBox.getChildren().addAll(timeLabel, msgLabel);
        HBox row = new HBox(8, avatar, msgBox);
        row.setAlignment(Pos.TOP_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }
    public Node buildUserMessage(String content, LocalDateTime ts, Consumer<String> copyHandler) {
        String userAvatarPath = ConfigManager.getInstance().getUserAvatarPath();
        ImageView avatar = buildAvatar(userAvatarPath, "/images/user_avatar.png");
        LocalDateTime time = ts == null ? LocalDateTime.now() : ts;
        String shortTime = time.format(DateTimeFormatter.ofPattern("HH:mm"));
        String fullTime = time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        VBox msgBox = new VBox(2);
        Label timeLabel = new Label(shortTime);
        timeLabel.setFont(Font.font("Microsoft YaHei", 10));
        timeLabel.setTextFill(Color.GRAY);
        timeLabel.setAlignment(Pos.TOP_RIGHT);
        timeLabel.setTooltip(new Tooltip(fullTime));
        Label msgLabel = new Label(content);
        msgLabel.setWrapText(true);
        msgLabel.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 14));
        msgLabel.setTextFill(Color.WHITE);
        msgLabel.setStyle("-fx-background-color: #1976d2; -fx-background-radius: 15 15 0 15; -fx-padding: 8 12;");
        msgLabel.setMaxWidth(MAX_BUBBLE_WIDTH);
        msgLabel.setMinHeight(Region.USE_PREF_SIZE);
        addCopyContextMenu(msgLabel, content, copyHandler);
        msgBox.getChildren().addAll(timeLabel, msgLabel);
        msgBox.setAlignment(Pos.TOP_RIGHT);
        HBox row = new HBox(8, msgBox, avatar);
        row.setAlignment(Pos.TOP_RIGHT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }
    private ImageView buildAvatar(String avatarPath, String fallback) {
        ImageView iv = new ImageView();
        String path = avatarPath == null || avatarPath.isBlank() ? fallback : avatarPath;
        Image img = resourceService.loadImageFlexible(path, getClass());
        if (img != null) iv.setImage(img); else { iv.setFitWidth(36); iv.setFitHeight(36); iv.setStyle("-fx-background-color:#cccccc; -fx-background-radius:50%;"); }
        iv.setFitWidth(36); iv.setFitHeight(36); iv.setPreserveRatio(true);
        return iv;
    }
    private void addCopyContextMenu(Label label, String message, Consumer<String> copyHandler) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem copyItem = new javafx.scene.control.MenuItem("复制消息");
        copyItem.setOnAction(e -> { if (copyHandler != null) copyHandler.accept(message); });
        menu.getItems().add(copyItem);
        label.setContextMenu(menu);
    }
}

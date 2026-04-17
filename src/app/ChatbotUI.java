package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import model.ChatSession;
import model.Message;
import service.ChatService;
import service.ConfigManager;
import service.ContactsService;
import service.ContactInfo;
import service.UIResourceService;
import service.GalleryService;
import service.MessageRenderService;
import service.ai.AiProviderPreset;
import speech.BaiduSpeechService;
import speech.TextToSpeech;
import speech.VoskSpeechService;

import java.awt.Graphics2D;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javax.swing.ImageIcon;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class ChatbotUI extends Application {
    private ChatService chatService;
    private VBox chatContentContainer;
    private ScrollPane scrollPane;
    private static final int MAX_BUBBLE_WIDTH = 400;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;

    private static final Logger LOGGER = Logger.getLogger(ChatbotUI.class.getName());
    // Service delegates
    private final UIResourceService uiResourceService = new UIResourceService();
    private final GalleryService galleryService = new GalleryService();
    private final MessageRenderService messageRenderService = new MessageRenderService(MAX_BUBBLE_WIDTH);

    // 顶部标题区和布局引用
    private HBox titleBar;
    private BorderPane mainLayout;
    private Scene scene;

    // 侧边栏
    private VBox sideBar;
    private StackPane rootPane; // overlay parent to host floating collapse handle when sidebar is collapsed
    private Button sideBarHandle;
    private ListView<ChatSession> sideSessionList;
    private TextField historySearchField;
    // history toggle button (clock) shown in top-right next to MenuBar
    private Button historyToggleBtn;
    // history sidebar footer (clear button container)
    private HBox sideBarFooter;
    private final AtomicLong historyRefreshVersion = new AtomicLong();
    private final AtomicLong historySearchVersion = new AtomicLong();

    // 右侧联系人 pane
    private VBox contactsPane;
    private ListView<Contact> contactsListView;
    private final ObservableList<Contact> contactsData = FXCollections.observableArrayList();

    // 主题
    private final boolean darkMode = false;

    // TTS / 语言选择
    // 默认程序启动即自动播放 AI 回复
    private boolean autoPlayEnabled = true;
    private final String selectedLanguage = "auto"; // "auto", "zh-CN", "en-US", ...
    private TextToSpeech tts;
    private BaiduSpeechService baiduService; // 如果可用，优先使用百度语音服务
    private VoskSpeechService voskService; // 离线识别备选

    // 当前选中的 AI 联系人头像路径（用于气泡内头像与联系人列表一致）
    private String currentPersonaAvatarPath = "/images/ai_avatar.png"; // 默认流萤

    // 新增：对话区上方左侧显示联系人名称
    private Label contactTitleLabel;
    // 联系人信息侧栏相关字段
    private VBox contactInfoSidebar;
    private boolean contactInfoVisible = false;
    private String currentContactId = null;
    private ImageView infoAvatarView; private Label infoNameLabel; private Label infoRemarkLabel; private Label infoPinnedLabel; private TextArea infoPromptArea; private Label infoGreetingLabel; private Button infoPinBtn; private Button infoDeleteBtn;

    // 语音播放开关按钮
    private Button voiceToggleBtn;
    // 在启动 / 切换联系人 / 加载历史阶段抑制自动播放，仅当用户产生新一轮对话回复时才播放
    private boolean suppressPlaybackForHistory = true; // 初始为 true，直到第一次用户发送消息

    // 统一的菜单栏创建方法（保留原有）
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        // ensure no extra padding or border from MenuBar (transparent — topBox provides background)
        menuBar.setStyle("-fx-padding: 0; -fx-background-color: transparent; -fx-border-width: 0; -fx-background-insets: 0;");

        // 文件菜单（删除清空聊天项，仅保留退出）
        Menu fileMenu = new Menu("文件");
        MenuItem exitItem = new MenuItem("退出");
        exitItem.setOnAction(e -> System.exit(0));
        MenuItem viewDbItem = new MenuItem("数据库"); viewDbItem.setOnAction(e -> showDatabaseViewer());
        MenuItem viewImagesItem = new MenuItem("图片"); viewImagesItem.setOnAction(e -> showImagesViewer());
        // Removed backup builtin menu item (no built-ins anymore)
        fileMenu.getItems().addAll(viewDbItem, viewImagesItem, new SeparatorMenuItem(), exitItem);

        // 设置菜单
        Menu settingsMenu = new Menu("设置");
        MenuItem apiSettingsItem = new MenuItem("API设置");
        apiSettingsItem.setOnAction(e -> showSettingsDialog());
        MenuItem userSettingsItem = new MenuItem("用户设置");
        userSettingsItem.setOnAction(e -> showUserSettingsDialog());
        settingsMenu.getItems().addAll(apiSettingsItem, userSettingsItem);

        // 帮助菜单
        Menu helpMenu = new Menu("帮助");
        MenuItem docItem = new MenuItem("使用说明");
        docItem.setOnAction(e -> showHelpDoc());
        helpMenu.getItems().addAll(docItem);

        menuBar.getMenus().addAll(fileMenu, settingsMenu, helpMenu);
        return menuBar;
    }

    //帮助文档预览
    private void showHelpDoc() {
        String content;
        try (var in = getClass().getResourceAsStream("/help/help.txt")) {
            if (in == null) {
                content = "未找到帮助文件。\n请在 `/resources/help/help.txt` 创建该文件。";
            } else {
                content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "读取帮助文档失败", ex);
            content = "读取帮助文档失败：" + ex.getMessage();
        }

        TextArea area = new TextArea(content);
        area.setEditable(false);
        area.setWrapText(true);
        area.setFont(Font.font("Microsoft YaHei", 13));

        // 关键设置：让TextArea填充所有可用空间
        area.setPrefRowCount(30);  // 设置足够的行数
        area.setPrefColumnCount(40);
        area.setMinHeight(Region.USE_PREF_SIZE);
        area.setMaxHeight(Double.MAX_VALUE);

        ScrollPane sp = new ScrollPane(area);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);  // 关键：让ScrollPane适应内容高度
        sp.setPrefViewportHeight(600);  // 设置视口高度
        sp.setMinHeight(200);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("使用说明");
        alert.setHeaderText("帮助文档预览");
        alert.getDialogPane().setContent(sp);
        alert.setResizable(true);  // 关键：允许调整大小
        alert.showAndWait();
    }

    private void clearChat() {
        if (chatContentContainer != null) chatContentContainer.getChildren().clear();
        if (chatService != null) chatService.clearChatHistory();
        addWelcomeMessage();
    }

    private void showSettingsDialog() {
        ConfigManager cfg = ConfigManager.getInstance();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("API 设置");

        AiProviderPreset initialPreset = cfg.getAiProviderPreset();

        ComboBox<AiProviderPreset> providerBox = new ComboBox<>();
        providerBox.getItems().setAll(AiProviderPreset.values());
        providerBox.getSelectionModel().select(initialPreset);
        providerBox.setMaxWidth(Double.MAX_VALUE);

        Label protocolValue = new Label();
        protocolValue.setTextFill(Color.GRAY);

        PasswordField apiKeyField = new PasswordField();
        apiKeyField.setText(cfg.getAiApiKey());

        TextField baseUrlField = new TextField(cfg.getAiBaseUrl());
        TextField modelField = new TextField(cfg.getAiModel());

        Label presetHintLabel = new Label();
        presetHintLabel.setWrapText(true);
        presetHintLabel.setTextFill(Color.GRAY);

        Runnable syncPreset = () -> {
            AiProviderPreset selectedPreset = providerBox.getValue();
            if (selectedPreset == null) {
                return;
            }
            protocolValue.setText(selectedPreset.getProtocol().getDisplayName());
            presetHintLabel.setText(selectedPreset.getDescription());
        };
        syncPreset.run();
        providerBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            protocolValue.setText(newValue.getProtocol().getDisplayName());
            presetHintLabel.setText(newValue.getDescription());
            if (oldValue != null) {
                if (baseUrlField.getText() == null
                        || baseUrlField.getText().isBlank()
                        || baseUrlField.getText().trim().equals(oldValue.getDefaultBaseUrl())) {
                    baseUrlField.setText(newValue.getDefaultBaseUrl());
                }
                if (modelField.getText() == null
                        || modelField.getText().isBlank()
                        || modelField.getText().trim().equals(oldValue.getDefaultModel())) {
                    modelField.setText(newValue.getDefaultModel());
                }
            }
        });

        Label baiduAppIdLabel = new Label("Baidu App ID:");
        TextField baiduAppIdField = new TextField(cfg.getBaiduAppId());
        Label baiduApiKeyLabel = new Label("Baidu API Key:");
        TextField baiduApiKeyField = new TextField(cfg.getBaiduApiKey());
        Label baiduSecretLabel = new Label("Baidu Secret Key:");
        TextField baiduSecretField = new TextField(cfg.getBaiduSecretKey());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;
        grid.add(new Label("AI Provider:"), 0, row);
        grid.add(providerBox, 1, row++);
        grid.add(new Label("Protocol:"), 0, row);
        grid.add(protocolValue, 1, row++);
        grid.add(new Label("API Key:"), 0, row);
        grid.add(apiKeyField, 1, row++);
        grid.add(new Label("Base URL:"), 0, row);
        grid.add(baseUrlField, 1, row++);
        grid.add(new Label("Model:"), 0, row);
        grid.add(modelField, 1, row++);
        grid.add(new Label("Provider Note:"), 0, row);
        grid.add(presetHintLabel, 1, row++);

        Separator separator = new Separator();
        grid.add(separator, 0, row++, 2, 1);

        grid.add(baiduAppIdLabel, 0, row);
        grid.add(baiduAppIdField, 1, row++);
        grid.add(baiduApiKeyLabel, 0, row);
        grid.add(baiduApiKeyField, 1, row++);
        grid.add(baiduSecretLabel, 0, row);
        grid.add(baiduSecretField, 1, row);

        GridPane.setHgrow(providerBox, Priority.ALWAYS);
        GridPane.setHgrow(apiKeyField, Priority.ALWAYS);
        GridPane.setHgrow(baseUrlField, Priority.ALWAYS);
        GridPane.setHgrow(modelField, Priority.ALWAYS);
        GridPane.setHgrow(presetHintLabel, Priority.ALWAYS);
        GridPane.setHgrow(baiduAppIdField, Priority.ALWAYS);
        GridPane.setHgrow(baiduApiKeyField, Priority.ALWAYS);
        GridPane.setHgrow(baiduSecretField, Priority.ALWAYS);

        VBox root = new VBox(12, grid);
        root.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> opt = dialog.showAndWait();
        if (opt.isPresent() && opt.get() == ButtonType.OK) {
            AiProviderPreset selectedPreset = providerBox.getValue() == null ? AiProviderPreset.DEEPSEEK : providerBox.getValue();
            cfg.setAiSettings(
                    selectedPreset,
                    selectedPreset.getProtocol(),
                    apiKeyField.getText().trim(),
                    baseUrlField.getText().trim(),
                    modelField.getText().trim(),
                    cfg.getTemperature(),
                    cfg.getMaxTokens(),
                    cfg.getConnectTimeout(),
                    cfg.getReadTimeout(),
                    cfg.getWriteTimeout()
            );
            cfg.setBaiduCredentials(baiduAppIdField.getText().trim(), baiduApiKeyField.getText().trim(), baiduSecretField.getText().trim());
            showAlert("设置已保存", "AI 与百度语音配置已保存到配置文件");

            try {
                baiduService = new BaiduSpeechService();
            } catch (Throwable ex) {
                showAlert("警告", "无法初始化百度语音服务: " + ex.getMessage());
                baiduService = null;
            }
        }
    }

    // 新增：用户设置对话框（头像单独管理）
    private void showUserSettingsDialog() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("用户设置");
        VBox root = new VBox(12); root.setPadding(new Insets(16));
        Label avatarHeader = new Label("用户头像"); avatarHeader.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        ImageView preview = new ImageView(); preview.setFitWidth(72); preview.setFitHeight(72); preview.setPreserveRatio(true);
        String current = ConfigManager.getInstance().getUserAvatarPath();
        Image curImg = uiResourceService.loadImageFlexible(current, getClass());
        if (curImg != null) preview.setImage(curImg); else preview.setStyle("-fx-background-color:#ddd; -fx-background-radius:50%;");
        Button chooseBtn = new Button("选择头像"); chooseBtn.setStyle("-fx-background-color:#1976d2; -fx-text-fill:white; -fx-background-radius:6;");
        chooseBtn.setOnAction(e -> showUserAvatarDialog(preview));
        HBox avatarRow = new HBox(12, preview, chooseBtn); avatarRow.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().addAll(avatarHeader, avatarRow);
        dlg.getDialogPane().setContent(root);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dlg.showAndWait();
    }

    // 规范化用户头像路径为 images 目录下绝对路径（用于预选与高亮）
    private String normalizeUserAvatarForPreselect(String path) {
        if (path == null || path.isBlank()) return path;
        if (path.startsWith("/images/") || path.startsWith("images/")) {
            String name = new java.io.File(path).getName();
            java.io.File dir = new java.io.File(uiResourceService.getImagesDirPath());
            if (dir.exists() && dir.isDirectory()) {
                java.io.File candidate = new java.io.File(dir, name);
                if (candidate.exists()) return candidate.getAbsolutePath();
            }
        }
        return path;
    }

    // 修复：用户头像选择时点击无高亮反馈（保证预选与点击样式正确）
    private void showUserAvatarDialog(ImageView previewToUpdate) {
        String imagesDirPath = uiResourceService.getImagesDirPath();
        java.io.File imgDir = new java.io.File(imagesDirPath);
        java.util.List<String> paths = new java.util.ArrayList<>();
        if (imgDir.exists() && imgDir.isDirectory()) {
            java.io.File[] imgs = imgDir.listFiles((dir, name) -> name.matches("(?i).+\\.(png|jpg|jpeg|gif)"));
            if (imgs != null) for (java.io.File f : imgs) paths.add(f.getAbsolutePath());
        }
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("选择用户头像");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        String[] selOut = new String[1];
        String currentUserAvatar = normalizeUserAvatarForPreselect(ConfigManager.getInstance().getUserAvatarPath());
        // 选择界面：使用 galleryService，但为了更明显的选中效果传入 preselect 并修改样式（在 galleryService 中有 selectedStyle）
        ScrollPane gallery = galleryService.buildPreviewGallery(paths, 72, 6, true, currentUserAvatar, true, true, imagesDirPath, selOut, getClass(), null);
        // 添加说明标签
        VBox wrapper = new VBox(8, new Label("单击图片以选中，上传自动裁剪为正方形"), gallery);
        wrapper.setPadding(new Insets(8));
        dlg.getDialogPane().setContent(wrapper);
        java.util.Optional<ButtonType> res = dlg.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            if (selOut[0] == null || selOut[0].isBlank()) { showAlert("提示", "未选择头像"); return; }
            ConfigManager.getInstance().setUserAvatarPath(selOut[0]);
            Image newImg = uiResourceService.loadImageFlexible(selOut[0], getClass());
            if (newImg != null && previewToUpdate != null) previewToUpdate.setImage(newImg);
            refreshUserMessageAvatars();
            showAlert("成功", "用户头像已更新");
        }
    }

    // 刷新所有已显示的用户消息头像
    private void refreshUserMessageAvatars() {
        if (chatContentContainer == null) return;
        String userAvatarPath = ConfigManager.getInstance().getUserAvatarPath();
        javafx.scene.image.Image newAvatar = uiResourceService.loadImageFlexible(userAvatarPath, getClass());
        if (newAvatar == null) return;
        for (javafx.scene.Node node : chatContentContainer.getChildren()) {
            if (node instanceof HBox row) {
                // 用户消息布局是 [msgBox, avatar]，AI 消息布局是 [avatar, msgBox]
                if (row.getChildren().size() >= 2 && row.getChildren().get(1) instanceof ImageView iv) {
                    if (!row.getChildren().isEmpty() && row.getChildren().getFirst() instanceof VBox vb && !vb.getChildren().isEmpty()) {
                        if (vb.getChildren().get(1) instanceof Label lbl) {
                            String style = lbl.getStyle()==null?"":lbl.getStyle();
                            if (style.contains("#1976d2")) {
                                iv.setImage(newAvatar);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void start(Stage primaryStage) {
        Platform.runLater(() -> {
            chatService = new ChatService(this);

            // 初始化 TTS
            tts = new TextToSpeech();

            // 读取配置中的 TTS 自动播放设置（默认 true）
            autoPlayEnabled = ConfigManager.getInstance().isAutoPlayEnabled();

            // 尝试初始化百度语音服务（如果缺少依赖或配置，将降级为本地/模拟实现）
            try {
                baiduService = new BaiduSpeechService();
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING, "BaiduSpeechService 初始化失败，已降级为模拟语音功能", ex);
                baiduService = null;
            }

            // 初始化 Vosk 离线识别（如果可用）
            try {
                voskService = new VoskSpeechService();
                if (!voskService.isAvailable()) voskService = null;
            } catch (Throwable ex) {
                LOGGER.log(Level.FINE, "Vosk 初始化失败或不可用", ex);
                voskService = null;
            }

            mainLayout = new BorderPane();
            // wrap mainLayout with a StackPane to allow overlaying a floating collapse handle
            rootPane = new StackPane();
            rootPane.getChildren().add(mainLayout);

            mainLayout.setPadding(new Insets(0));
            // Use same background as chat content to avoid visible seam between top area and chat
            mainLayout.setBackground(new Background(new BackgroundFill(
                    Color.rgb(245, 245, 245), CornerRadii.EMPTY, Insets.EMPTY
            )));

            // 顶部：菜单 + 右上角历史切换按钮（时钟）
            titleBar = createTitleBar();
            VBox topBox = new VBox();
            topBox.setSpacing(0);
            topBox.setPadding(new Insets(0));
            topBox.setBackground(new Background(new BackgroundFill(Color.rgb(245,245,245), CornerRadii.EMPTY, Insets.EMPTY)));
            MenuBar mb = createMenuBar();
            mb.setStyle("-fx-padding: 0; -fx-background-color: transparent; -fx-border-width: 0; -fx-background-insets: 0;");

            // create history toggle button in the top row (next to MenuBar)
            historyToggleBtn = new Button("🕒");
            historyToggleBtn.setPrefSize(34, 28);
            historyToggleBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 14;");
            historyToggleBtn.setTooltip(new Tooltip("切换历史会话面板"));
            historyToggleBtn.setOnAction(e -> toggleSidebar());
            // apply unified function style
            applyFunctionButtonStyle(historyToggleBtn);

            // 新建聊天按钮（放在时钟左边，使用更醒目的加号）
            Button newChatBtn = new Button("➕");
            newChatBtn.setPrefSize(34, 28);
            newChatBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 16;");
            newChatBtn.setTooltip(new Tooltip("开始新的聊天（本联系人）"));
            newChatBtn.setOnAction(e -> {
                suppressPlaybackForHistory = true; // 新建聊天时抑制历史消息播报
                try { chatService.startNewChat(null); } catch (Exception ex) { showAlert("错误", "新建会话失败: " + ex.getMessage()); }
            });
            applyFunctionButtonStyle(newChatBtn);
            // 添加语音播放切换按钮（位于加号左侧）
            voiceToggleBtn = new Button(autoPlayEnabled ? "🔈" : "🔇");
            voiceToggleBtn.setPrefSize(38, 28);
            updateVoiceToggleVisual();
            voiceToggleBtn.setTooltip(new Tooltip("切换回复自动语音播报"));
            voiceToggleBtn.setOnAction(ev -> toggleVoicePlayback());
            applyFunctionButtonStyle(voiceToggleBtn);
            HBox topRow = new HBox();
            topRow.setSpacing(0);
            topRow.setPadding(new Insets(0));
            HBox.setHgrow(mb, Priority.ALWAYS);
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            // 新增：联系人信息按钮放在右上角同一排
            Button contactInfoBtnTop = new Button("☰");
            contactInfoBtnTop.setPrefSize(34, 28);
            contactInfoBtnTop.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 14;");
            contactInfoBtnTop.setTooltip(new Tooltip("联系人详细信息"));
            contactInfoBtnTop.setOnAction(e -> toggleContactInfoSidebar());
            topRow.getChildren().addAll(mb, gap, voiceToggleBtn, newChatBtn, historyToggleBtn, contactInfoBtnTop);

            mainLayout.setTop(topRow);

            // 将联系人面板放在左侧，历史侧边栏放到右侧（按用户更正“说错了，是左侧”）
            mainLayout.setLeft(createContactsPane());

            // 中间聊天区域
            scrollPane = createScrollPane();
            // 对话区顶部的联系人标题（左对齐）
            HBox chatHeader = createChatHeader();
            // Build a center column so that the input box sits directly under the chat area
            // and the status bar sits immediately below the input (all within the center region).
            VBox centerColumn = new VBox(0);
            centerColumn.setPadding(new Insets(0));
            centerColumn.setSpacing(0);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);
            HBox inputArea = createInputArea();
            HBox statusBar = createStatusBar();
            centerColumn.getChildren().addAll(chatHeader, scrollPane, inputArea, statusBar);
            mainLayout.setCenter(centerColumn);
            // 现在联系人已加载、chatContentContainer 已初始化，添加欢迎消息以使用正确头像
            // 切换到当前默认联系人并加载其上次会话；若没有则是一个新会话（在服务层完成）。
            try {
                Contact defaultC = contactsListView.getItems().stream().filter(c -> "liuying".equals(c.getId())).findFirst().orElse(
                        contactsListView.getItems().isEmpty() ? null : contactsListView.getItems().getFirst()
                );
                if (defaultC != null) {
                    // 初始化标题显示为默认联系人名
                    setCurrentContactDisplayName(defaultC.getDisplayName());
                    currentPersonaAvatarPath = defaultC.getAvatarPath();
                    currentContactId = defaultC.getId();
                    chatService.switchPersona(defaultC.getId(), defaultC.getDisplayName(), defaultC.getSystemPrompt(), defaultC.getGreeting());
                    refreshContactInfoSidebar();
                } else {
                    // 若没有联系人，标题留空或默认
                    setCurrentContactDisplayName("");
                    addWelcomeMessage();
                }
            } catch (Exception __ignore) { setCurrentContactDisplayName(""); addWelcomeMessage(); }

            // 右侧放置历史会话侧边栏（默认按配置折叠/展开）
            sideBar = createSideBar();
            if (ConfigManager.getInstance().isSidebarCollapsed()) {
                mainLayout.setRight(null);
            } else {
                mainLayout.setRight(sideBar);
            }

            // initialize historyToggleBtn visual state to match persisted collapsed state
            try {
                boolean collapsed = ConfigManager.getInstance().isSidebarCollapsed();
                if (historyToggleBtn != null) {
                    if (collapsed) historyToggleBtn.setStyle("-fx-background-color: transparent; -fx-font-size:14;");
                    else historyToggleBtn.setStyle("-fx-background-color: #e8f0ff; -fx-font-size:14; -fx-border-color:#cfe0ff; -fx-border-radius:4;");
                }
            } catch (Exception ignored) {}

            // 窗口配置：在窗口装饰栏显示应用名
            primaryStage.setTitle("EchoSoul");
            // Set initial window size to golden ratio (width / height = 1.618...)
            double initialWidth = 1000;
            double golden = (1.0 + Math.sqrt(5.0)) / 2.0;
            double initialHeight = Math.round(initialWidth / golden);
            primaryStage.setWidth(initialWidth);
            primaryStage.setHeight(initialHeight);
            // reasonable minimums preserving aspect
            primaryStage.setMinWidth(700);
            primaryStage.setMinHeight( (int)Math.round(700 / golden) );

            primaryStage.setOnCloseRequest(e -> {
                if (chatService != null) chatService.shutdown();
                Platform.exit();
                System.exit(0);
            });

            // create scene with explicit background fill matching main layout to avoid 1px seam
            scene = new Scene(rootPane, Color.rgb(245,245,245));
            applyTheme(); // 应用初始主题

            primaryStage.setScene(scene);
    // 设置窗口/任务栏图标
            try {
                primaryStage.getIcons().clear();
                Image appIcon = loadWindowIcon();
                if (appIcon != null) {
                    primaryStage.getIcons().add(appIcon);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "加载应用图标失败", ex);
            }

            primaryStage.show();
        });
    }

    // Toggle the sidebar collapsed/expanded state (show/hide the right-side history panel)
    private void toggleSidebar() {
        try {
            sideBarCollapsed = !sideBarCollapsed;
            ConfigManager.getInstance().setSidebarCollapsed(sideBarCollapsed);
            final Background expandedBg = new Background(new BackgroundFill(Color.web("#fafafa"), CornerRadii.EMPTY, Insets.EMPTY));
            final Background collapsedBg = new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY));
            final String collapseBtnCollapsedStyle = "-fx-cursor: hand; -fx-padding: 2; -fx-background-color: transparent; -fx-border-width: 0;";

            if (sideBarCollapsed) {
                // hide: remove from layout
                if (mainLayout != null) mainLayout.setRight(null);
                if (sideBar != null) {
                    sideBar.setBackground(collapsedBg);
                    sideBar.setPrefWidth(SIDE_BAR_COLLAPSED_WIDTH);
                    sideBar.setMinWidth(SIDE_BAR_COLLAPSED_WIDTH);
                    if (historySearchField != null) { historySearchField.setVisible(false); historySearchField.setManaged(false); }
                    if (sideSessionList != null) { sideSessionList.setVisible(false); sideSessionList.setManaged(false); }
                    if (sideBarFooter != null) { sideBarFooter.setVisible(false); sideBarFooter.setManaged(false); }
                    // ensure a handle exists so the user can expand from the collapsed state
                    if (sideBarHandle == null) {
                        sideBarHandle = new Button("▶");
                        sideBarHandle.setPrefSize(24, 24);
                        sideBarHandle.setStyle(collapseBtnCollapsedStyle);
                        sideBarHandle.setTooltip(new Tooltip("展开侧边栏"));
                        sideBarHandle.setOnAction(ev -> toggleSidebar());
                        HBox topBox = new HBox(sideBarHandle);
                        topBox.setPadding(new Insets(6));
                        topBox.setAlignment(Pos.TOP_CENTER);
                        topBox.setUserData("collapseHandleBox");
                        sideBar.getChildren().addFirst(topBox);
                    } else if (sideBarHandle.getParent() == null) {
                        HBox topBox = new HBox(sideBarHandle);
                        topBox.setPadding(new Insets(6));
                        topBox.setAlignment(Pos.TOP_CENTER);
                        topBox.setUserData("collapseHandleBox");
                        sideBar.getChildren().addFirst(topBox);
                        sideBarHandle.setText("▶");
                        sideBarHandle.setStyle(collapseBtnCollapsedStyle);
                    }
                }
                if (historyToggleBtn != null) historyToggleBtn.setStyle("-fx-background-color: transparent; -fx-font-size:14;");
            } else {
                // show and refresh sessions: ensure sideBar visual state is expanded
                if (sideBar == null) sideBar = createSideBar();
                // 清理可能遗留的 handle wrapper
                sideBar.getChildren().removeIf(node -> "collapseHandleBox".equals(node.getUserData()));
                if (!sideBar.getChildren().isEmpty() && sideBar.getChildren().getFirst() instanceof HBox && sideBar.getChildren().getFirst() != sideSessionList) {
                    sideBar.getChildren().removeFirst();
                }
                sideBar.setBackground(expandedBg);
                sideBar.setPrefWidth(SIDE_BAR_EXPANDED_WIDTH);
                sideBar.setMinWidth(SIDE_BAR_EXPANDED_WIDTH);
                if (historySearchField != null) {
                    historySearchField.setVisible(true);
                    historySearchField.setManaged(true);
                }
                if (sideSessionList != null) {
                    sideSessionList.setVisible(true);
                    sideSessionList.setManaged(true);
                    sideSessionList.setStyle("-fx-background-color: white; -fx-control-inner-background: white; -fx-text-fill: black;");
                }
                if (sideBarFooter != null) { sideBarFooter.setVisible(true); sideBarFooter.setManaged(true); }
                if (sideBarHandle != null) {
                    try {
                        Parent p = sideBarHandle.getParent();
                        if (p instanceof Pane pane) pane.getChildren().remove(sideBarHandle);
                    } catch (Exception ignored) {}
                    sideBar.getChildren().removeIf(node -> "collapseHandleBox".equals(node.getUserData()));
                    sideBarHandle = null;
                }
                if (mainLayout != null) mainLayout.setRight(sideBar);
                refreshSideBarSessions();
                if (historyToggleBtn != null) historyToggleBtn.setStyle("-fx-background-color: #e8f0ff; -fx-font-size:14; -fx-border-color:#cfe0ff; -fx-border-radius:4;");
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "切换侧边栏状态失败", ex);
        }
    }

    // 创建状态栏
    private HBox createStatusBar() {
        HBox statusBar = new HBox(6);
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setPrefHeight(28);
        statusBar.setStyle("-fx-background-color: transparent; -fx-border-width: 1 0 0 0; -fx-border-color: #eee;");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(16, 16);

        statusLabel = new Label("就绪");
        statusLabel.setFont(Font.font("Microsoft YaHei", 12));
        statusLabel.setTextFill(Color.GRAY);

        // 连接状态指示
        Label connectionStatus = new Label("●");
        connectionStatus.setTextFill(Color.GREEN);
        connectionStatus.setFont(Font.font(14));

        statusBar.getChildren().addAll(progressIndicator, statusLabel, new Separator(), connectionStatus);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        return statusBar;
    }

    // 创建中间聊天滚动区域
    private ScrollPane createScrollPane() {
        chatContentContainer = new VBox(0);
        chatContentContainer.setPadding(new Insets(0));
        chatContentContainer.setBackground(new Background(new BackgroundFill(
                Color.rgb(245, 245, 245), CornerRadii.EMPTY, Insets.EMPTY
        )));
        chatContentContainer.setPrefWidth(Double.MAX_VALUE);
        ScrollPane sp = new ScrollPane(chatContentContainer);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPadding(new Insets(0));
        sp.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-width: 0;");
        // 新增：内容高度变化时自动滚动到底部
        chatContentContainer.heightProperty().addListener((obs, oldH, newH) -> sp.setVvalue(1.0));
        return sp;
    }

    // 底部输入区
    private HBox createInputArea() {
        HBox inputArea = new HBox(6);
        inputArea.setPadding(new Insets(4, 10, 2, 10));
        inputArea.setStyle("-fx-background-color: transparent; -fx-border-width: 1 0 0 0; -fx-border-color: #eee;");
        inputArea.setAlignment(Pos.CENTER);
        inputArea.setPrefHeight(48);

        TextField inputField = new TextField();
        inputField.setPromptText("请输入你的问题...");
        inputField.setFont(Font.font("Microsoft YaHei", 14));
        inputField.setPrefHeight(40);
        inputField.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 12; " +
                        "-fx-border-radius: 12; " +
                        "-fx-border-color: #ddd; " +
                        "-fx-padding: 6 10;"
        );
        HBox.setHgrow(inputField, Priority.ALWAYS);

        inputField.setOnKeyPressed(e -> {
            if (e.getCode().equals(javafx.scene.input.KeyCode.ENTER) && !e.isShiftDown()) {
                e.consume();
                sendMessage(inputField);
            }
        });

        Button sendBtn = new Button("✈");
        sendBtn.setFont(Font.font(14));
        sendBtn.setPrefSize(44, 40);
        // explicit visible brand color so the button is always visible
        sendBtn.setStyle("-fx-background-radius: 12; -fx-background-color: #1976d2; -fx-text-fill: white; -fx-border-color: transparent;");
        sendBtn.setOnAction(e -> sendMessage(inputField));

        Button micBtn = new Button("🎤");
        micBtn.setTooltip(new Tooltip("语音输入"));
        micBtn.setPrefSize(40, 40);
        // make mic button clearly visible with subtle border
        micBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #ddd; -fx-border-radius: 12; -fx-background-radius: 12;");
        micBtn.setOnAction(e -> {
            try {
                if (baiduService != null && baiduService.isAvailable()) {
                    if (!baiduService.isRecording()) {
                        baiduService.startRecording();
                        micBtn.setText("■");
                        micBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-border-radius: 12;");
                    } else {
                        micBtn.setText("🎤");
                        micBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #ddd; -fx-border-radius: 12; -fx-background-radius: 12;");
                        baiduService.stopAndRecognize(recognized -> {
                            if (recognized != null && !recognized.trim().isEmpty()) {
                                Platform.runLater(() -> {
                                    String existing = inputField.getText();
                                    if (existing == null || existing.isEmpty()) inputField.setText(recognized);
                                    else inputField.setText(existing + " " + recognized);
                                    inputField.requestFocus();
                                    sendMessage(inputField);
                                });
                            }
                        });
                    }
                } else if (voskService != null && voskService.isAvailable()) {
                    if (!voskService.isRecording()) {
                        voskService.startRecording();
                        micBtn.setText("■");
                        micBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-border-radius: 12;");
                    } else {
                        micBtn.setText("🎤");
                        micBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #ddd; -fx-border-radius: 12; -fx-background-radius: 12;");
                        voskService.stopAndRecognize(result -> {
                            if (result != null && !result.trim().isEmpty()) {
                                Platform.runLater(() -> {
                                    String existing = inputField.getText();
                                    if (existing == null || existing.isEmpty()) inputField.setText(result);
                                    else inputField.setText(existing + " " + result);
                                    inputField.requestFocus();
                                    sendMessage(inputField);
                                });
                            }
                        });
                    }
                } else {
                    showAlert("提示", "语音识别不可用（请检查百度或离线模型配置）");
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "启动语音识别失败", ex);
                showAlert("错误", "启动语音识别失败: " + ex.getMessage());
            }
        });

        inputArea.getChildren().addAll(inputField, sendBtn, micBtn);
        return inputArea;
    }

    // 发送消息的简单实现
    private void sendMessage(TextField inputField) {
        String userMsg = inputField.getText() == null ? "" : inputField.getText().trim();
        if (userMsg.isEmpty()) return;
        try {
            // 不在此处直接渲染消息，交由 ChatService 在写入历史后带时间戳渲染
            suppressPlaybackForHistory = false; // 用户开始新一轮对话，允许后续 AI 回复播放
            inputField.clear();
            if (chatService != null) chatService.handleUserMessage(userMsg);
            else showAlert("错误", "聊天服务未初始化");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "消息发送过程中出现错误", e);
            showAlert("发送失败", "消息发送过程中出现错误: " + e.getMessage());
        }
    }

    // 简易提示框
    public void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // 主题应用（保留简易实现）
    private void applyTheme() {
        if (scene == null) return;
        if (darkMode) {
            scene.getRoot().setStyle("-title-bg: #2b2b2b; -root-bg: #1f1f1f;");
            scene.setFill(Color.web("#1f1f1f"));
            if (mainLayout != null) mainLayout.setBackground(new Background(new BackgroundFill(Color.web("#1f1f1f"), CornerRadii.EMPTY, Insets.EMPTY)));
        } else {
            scene.getRoot().setStyle("-title-bg: #f5f5f5; -root-bg: #f5f5f5;");
            scene.setFill(Color.web("#f5f5f5"));
            if (mainLayout != null) mainLayout.setBackground(new Background(new BackgroundFill(Color.web("#f5f5f5"), CornerRadii.EMPTY, Insets.EMPTY)));
        }
    }

    // 创建标题栏，左侧 Logo + 名称，右侧功能按钮（设置、帮助、历史、主题切换）
    private HBox createTitleBar() {
        HBox titleBar = new HBox();
        titleBar.setPadding(new Insets(0));
        titleBar.setStyle("-fx-background-color: transparent;");
        titleBar.setAlignment(Pos.CENTER_LEFT);

        // 左侧：logo + 应用名
        HBox left = new HBox(10);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setPadding(new Insets(0)); // 移除内边距

        ImageView logoView = new ImageView();
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/images/logo.png");
            if (is != null) {
                Image logo = new Image(is);
                logoView.setImage(logo);
                logoView.setFitWidth(36);
                logoView.setFitHeight(36);
                logoView.setPreserveRatio(true);
            } else {
                // 无 logo 时用占位
                logoView.setFitWidth(36);
                logoView.setFitHeight(36);
                logoView.setStyle("-fx-background-color: #cccccc; -fx-background-radius: 8;");
            }
        } catch (Exception ignored) {
            logoView.setFitWidth(36);
            logoView.setFitHeight(36);
            logoView.setStyle("-fx-background-color: #cccccc; -fx-background-radius: 8;");
        }

        // 不直接在左上角显示应用名称，只显示 Logo；在 Logo 上添加悬停提示以便查看名称
        Tooltip.install(logoView, new Tooltip("EchoSoul"));

        left.getChildren().addAll(logoView);

        // 右侧：功能按钮（包含历史切换按钮）
        HBox right = new HBox(8);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setPadding(new Insets(0)); // 移除内边距

        // 历史记录切换按钮（时钟图标）
        Button historyBtn = new Button("🕒");
        historyBtn.setPrefSize(34, 28);
        historyBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 14;");
        historyBtn.setTooltip(new Tooltip("切换历史会话面板"));
        historyBtn.setOnAction(e -> {
            try {
                toggleSidebar();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "切换历史面板失败", ex);
            }
        });
        applyFunctionButtonStyle(historyBtn);

        // 新建聊天按钮（在时钟左侧）
        Button newChatBtn = new Button("+");
        newChatBtn.setPrefSize(34, 28);
        newChatBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 16;");
        newChatBtn.setTooltip(new Tooltip("开始新的聊天（本联系人）"));
        newChatBtn.setOnAction(e -> {
            try { chatService.startNewChat(null); } catch (Exception ex) { showAlert("错误", "新建会话失败: " + ex.getMessage()); }
        });
        applyFunctionButtonStyle(newChatBtn);

        // 如果需要将来扩展右侧按钮，可以在此添加
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        right.getChildren().addAll(newChatBtn, historyBtn);

        titleBar.getChildren().addAll(left, spacer, right);
        // 使用透明背景，移除所有内边距
        titleBar.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-width: 0;");
        return titleBar;
    }

    // 添加欢迎消息
    public void addWelcomeMessage() {
        Platform.runLater(() -> {
            try {
                ImageView aiAvatar = createAIAvatar();

                String welcome = "你好呀！我是流萤，很高兴认识你～\n这是一个多行测试消息。";
                Label msgLabel = new Label(welcome);
                msgLabel.setWrapText(true);
                msgLabel.setFont(Font.font("Microsoft YaHei", 14));
                msgLabel.setTextFill(Color.BLACK);
                // explicit AI bubble style (light gray) for consistent rendering
                msgLabel.setStyle(
                        "-fx-background-color: #f0f0f0; " +
                                "-fx-background-radius: 15 15 15 0; " +
                                "-fx-border-color: #dde; " +
                                "-fx-border-radius: 15 15 15 0; " +
                                "-fx-padding: 8 12;"
                );

                msgLabel.setMaxWidth(MAX_BUBBLE_WIDTH);
                msgLabel.setMinHeight(Region.USE_PREF_SIZE);

                addCopyFunctionality(msgLabel, welcome);

                HBox contentBox = new HBox(8, aiAvatar, msgLabel);
                contentBox.setAlignment(Pos.TOP_LEFT);
                contentBox.setMaxWidth(Double.MAX_VALUE);
                // ensure no extra margin for the first element
                chatContentContainer.getChildren().add(contentBox);
                VBox.setMargin(contentBox, new Insets(0));

                scrollToBottom();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "添加欢迎消息时发生异常", e);
            }
        });
    }

    // Replace direct calls to internal image loaders with service delegates
    // For AI avatar creation
    private ImageView createAIAvatar() {
        ImageView avatar = new ImageView();
        String path = currentPersonaAvatarPath == null || currentPersonaAvatarPath.isBlank() ? "/images/ai_avatar.png" : currentPersonaAvatarPath;
        Image img = uiResourceService.loadImageFlexible(path, getClass());
        if (img != null) avatar.setImage(img); else { avatar.setFitWidth(36); avatar.setFitHeight(36); avatar.setStyle("-fx-background-color: #a9c6ed; -fx-background-radius: 50%;"); }
        avatar.setFitWidth(36); avatar.setFitHeight(36); avatar.setPreserveRatio(true);
        return avatar;
    }

    private ImageView createUserAvatar() {
        ImageView avatar = new ImageView();
        Image img = uiResourceService.loadImageFlexible("/images/user_avatar.png", getClass());
        if (img != null) avatar.setImage(img); else { avatar.setFitWidth(36); avatar.setFitHeight(36); avatar.setStyle("-fx-background-color: #ffb6c1; -fx-background-radius: 50%;"); }
        avatar.setFitWidth(36); avatar.setFitHeight(36); avatar.setPreserveRatio(true);
        return avatar;
    }

    private void addCopyFunctionality(Label label, String message) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("复制消息");
        copyItem.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(message);
            clipboard.setContent(content);
        });
        contextMenu.getItems().add(copyItem);
        label.setContextMenu(contextMenu);
    }

    // 添加用户消息（带短时间戳，鼠标悬停显示完整时间）
    public void addUserMessage(String msg) {
        addUserMessage(msg, java.time.LocalDateTime.now());
    }
    public void addUserMessage(String msg, java.time.LocalDateTime timestamp) {
        Platform.runLater(() -> {
            try {
                javafx.scene.Node node = messageRenderService.buildUserMessage(msg, timestamp, copied -> {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(copied);
                    clipboard.setContent(content);
                });
                chatContentContainer.getChildren().add(node);
                chatContentContainer.layout();
                scrollToBottom();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "添加用户消息时发生错误", e);
                showAlert("错误", "添加用户消息时发生错误: " + e.getMessage());
            }
        });
    }

    // 添加AI消息（带时间戳），自动播放可选
    public void addAiMessage(String aiMsg) {
        addAiMessage(aiMsg, java.time.LocalDateTime.now());
    }
    public void addAiMessage(String aiMsg, java.time.LocalDateTime timestamp) {
        Platform.runLater(() -> {
            try {
                javafx.scene.Node node = messageRenderService.buildAiMessage(aiMsg, currentPersonaAvatarPath, timestamp, copied -> {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(copied);
                    clipboard.setContent(content);
                });
                chatContentContainer.getChildren().add(node);
                chatContentContainer.layout();
                scrollToBottom();
                if (autoPlayEnabled && !suppressPlaybackForHistory) {
                    if (baiduService != null && baiduService.isAvailable()) {
                        baiduService.speakAndPlay(aiMsg);
                    } else if (tts != null) {
                        tts.speak(aiMsg, selectedLanguage);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "添加AI消息时发生错误", e);
                showAlert("错误", "添加AI消息时发生错误: " + e.getMessage());
            }
        });
    }

    // 被外部服务调用以更新发送/加载状态（保留 public 以供 ChatService 调用）
    public void setSendingStatus(boolean sending) {
        Platform.runLater(() -> {
            try {
                if (progressIndicator != null) progressIndicator.setVisible(sending);
                if (statusLabel != null) statusLabel.setText(sending ? "发送中..." : "就绪");
            } catch (Exception ex) {
                // 不要抛出 UI 调用的异常到调用方
            }
        });
    }

    // 导出会话（保留）
    private void exportSession(ChatSession session) {
        StringBuilder content = new StringBuilder();
        content.append("会话名称: ").append(session.getSessionName()).append("\n");
        content.append("保存时间: ").append(session.getFormattedTime()).append("\n");
        content.append("消息数量: ").append(session.getMessageCount()).append("\n\n");

        for (Message msg : session.getMessages()) {
            String role = msg.getRole() == Message.Role.USER ? "用户" : "AI助手";
            content.append("[").append(role).append("] ")
                    .append(msg.getFormattedTime()).append("\n")
                    .append(msg.getContent()).append("\n\n");
        }

        TextArea textArea = new TextArea(content.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);

        ScrollPane scrollPane = new ScrollPane(textArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(500, 400);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("会话内容");
        alert.setHeaderText("会话导出");
        alert.getDialogPane().setContent(scrollPane);
        alert.showAndWait();
    }

    // 清空聊天显示
    public void clearChatDisplay() {
        if (chatContentContainer != null) chatContentContainer.getChildren().clear();
    }


    // 侧边栏创建（会话列表 + 折叠按钮）
    private static final double SIDE_BAR_EXPANDED_WIDTH = 220;
    // very thin collapsed width so no visible white column remains
    private static final double SIDE_BAR_COLLAPSED_WIDTH = 8;
    private boolean sideBarCollapsed = false;

    private VBox createSideBar() {
        ConfigManager cfg = ConfigManager.getInstance();
        sideBarCollapsed = cfg.isSidebarCollapsed();
        VBox vb = new VBox(0);
        vb.setPadding(new Insets(0));
        vb.setPrefWidth(sideBarCollapsed ? SIDE_BAR_COLLAPSED_WIDTH : SIDE_BAR_EXPANDED_WIDTH);
        vb.setMinWidth(sideBarCollapsed ? SIDE_BAR_COLLAPSED_WIDTH : SIDE_BAR_EXPANDED_WIDTH);
        final Background expandedBg = new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY));
        final Background collapsedBg = new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY));
        final Border expandedBorder = new Border(new BorderStroke(Color.web("#e6e6e6"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(0,1,0,0)));
        vb.setBackground(sideBarCollapsed ? collapsedBg : expandedBg);
        vb.setBorder(sideBarCollapsed ? null : expandedBorder);

        sideBarHandle = null;
        final String collapseBtnCollapsedStyle = "-fx-cursor: hand; -fx-padding: 2; -fx-background-color: transparent; -fx-border-width: 0;";
        HBox btnBox = null;
        if (sideBarCollapsed) {
            sideBarHandle = new Button("▶");
            sideBarHandle.setPrefSize(24, 24);
            sideBarHandle.setStyle(collapseBtnCollapsedStyle);
            sideBarHandle.setTooltip(new Tooltip("展开侧边栏"));
            btnBox = new HBox();
            btnBox.setPadding(new Insets(6));
            btnBox.getChildren().add(sideBarHandle);
            btnBox.setAlignment(Pos.TOP_CENTER);
            btnBox.setUserData("collapseHandleBox");
        }

        historySearchField = new TextField();
        historySearchField.setPromptText("搜索历史消息...");
        historySearchField.setPrefHeight(34);
        historySearchField.setStyle("-fx-background-color: white; -fx-border-color: #e4e7ec; -fx-border-radius: 8; -fx-background-radius: 8;");
        historySearchField.textProperty().addListener((obs, oldValue, newValue) -> triggerHistorySearch());
        VBox.setMargin(historySearchField, new Insets(10, 10, 8, 10));

        sideSessionList = new ListView<>();
        sideSessionList.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(sideSessionList, Priority.ALWAYS);
        sideSessionList.setStyle("-fx-background-color: white; -fx-control-inner-background: white; -fx-text-fill: black;");

        // 复用原有 cellFactory
        sideSessionList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChatSession session, boolean empty) {
                super.updateItem(session, empty);
                if (empty || session == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // 第一条用户消息，若没有则显示“新对话”
                    String firstUser = null;
                    for (Message m : session.getMessages()) {
                        if (m.getRole() == Message.Role.USER) { firstUser = m.getContent(); break; }
                    }
                    String title = (firstUser == null || firstUser.isBlank()) ? "新对话" : firstUser.trim();
                    if (title.length() > 40) title = title.substring(0, 40) + "...";
                    String timeStr = session.getFormattedTime();
                    Label titleLabel = new Label(title);
                    titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
                    titleLabel.setTextFill(Color.BLACK);
                    Label timeLabel = new Label(timeStr);
                    timeLabel.setFont(Font.font("Microsoft YaHei", 10));
                    timeLabel.setTextFill(Color.GRAY);
                    VBox content = new VBox(3, titleLabel, timeLabel);

                    Button trashBtn = new Button("🗑");
                    trashBtn.setTooltip(new Tooltip("删除此会话"));
                    trashBtn.setPrefSize(28, 28);
                    trashBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                    trashBtn.setOnAction(evt -> {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("确认删除");
                        confirm.setHeaderText(null);
                        confirm.setContentText("确认删除会话? 此操作不可恢复。");
                        Optional<ButtonType> res = confirm.showAndWait();
                        if (res.isPresent() && res.get() == ButtonType.OK) {
                            boolean ok = chatService.deleteSession(session.getFilename());
                            if (ok) { showAlert("删除成功", "会话已删除"); refreshSideBarSessions(); }
                            else { showAlert("删除失败", "删除会话时出现错误"); }
                        }
                    });
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    HBox row = new HBox(8, content, spacer, trashBtn);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                }
            }
        });

        // 双击加载会话（保留）
        sideSessionList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ChatSession sel = sideSessionList.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    chatService.loadChatSession(sel);
                    showAlert("已加载", "已加载会话：" + sel.getSessionName());
                }
            }
        });

        // clicking handle toggles sidebar; use centralized method to also move handle into overlay
        if (sideBarHandle != null) sideBarHandle.setOnAction(ev -> toggleSidebar());

        // 底部清空并新建按钮
        sideBarFooter = new HBox();
        sideBarFooter.setPadding(new Insets(8));
        sideBarFooter.setAlignment(Pos.CENTER);
        sideBarFooter.setStyle("-fx-background-color: #ffffff; -fx-border-color: #eee; -fx-border-width: 1 0 0 0;");
        Button clearAndNewBtn = new Button("清空并新建");
        clearAndNewBtn.setTooltip(new Tooltip("清空当前对话并开始新的对话"));
        clearAndNewBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");
        clearAndNewBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("确认"); confirm.setHeaderText(null);
            confirm.setContentText("清空当前联系人全部历史并开始新对话？");
            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                try {
                    int deleted = chatService.deleteAllSessionsForCurrentPersona();
                    chatService.startNewChat(null);
                    showAlert("已重置", "已删除 " + deleted + " 条历史，对话已重置");
                } catch (Exception ex) {
                    showAlert("错误", "重置会话失败: " + ex.getMessage());
                }
            }
        });
        sideBarFooter.getChildren().add(clearAndNewBtn);

        // 初始化折叠状态
        if (sideBarCollapsed) {
            historySearchField.setVisible(false);
            historySearchField.setManaged(false);
            sideSessionList.setVisible(false);
            sideSessionList.setManaged(false);
            sideBarFooter.setVisible(false);
            sideBarFooter.setManaged(false);
            vb.setBackground(collapsedBg);
            vb.setPrefWidth(SIDE_BAR_COLLAPSED_WIDTH);
            vb.setMinWidth(SIDE_BAR_COLLAPSED_WIDTH);
            sideBarHandle.setStyle(collapseBtnCollapsedStyle);
        }

        if (btnBox != null) vb.getChildren().add(btnBox);
        vb.getChildren().add(historySearchField);
        vb.getChildren().add(sideSessionList);
        vb.getChildren().add(sideBarFooter);
        return vb;
    }

    // 新增：右侧联系人面板，包含搜索框、按字母排序的联系人列表、管理按钮
    private VBox createContactsPane() {
        contactsPane = new VBox(6);
        contactsPane.setPadding(new Insets(6));
        contactsPane.setPrefWidth(260);
        contactsPane.setStyle("-fx-background-color: #fafafa; -fx-border-color: #eee; -fx-border-width: 0 0 0 1;");
        TextField searchField = new TextField();
        searchField.setPromptText("搜索联系人...");
        searchField.setPrefHeight(34);
        searchField.setFont(Font.font("Microsoft YaHei", 13));
        Button manageBtn = new Button("➕");
        manageBtn.setTooltip(new Tooltip("新增 AI 联系人"));
        manageBtn.setPrefHeight(34);
        manageBtn.setOnAction(e -> showAddContactDialog());
        applyFunctionButtonStyle(manageBtn);
        HBox topRow = new HBox(8, searchField, manageBtn);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        contactsListView = new ListView<>();
        contactsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Contact c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    ImageView avatar = new ImageView();
                    boolean loaded = false;
                    String ap = c.getAvatarPath();
                    Image img = uiResourceService.loadImageFlexible(ap, getClass());
                    if (img != null) {
                        avatar.setImage(img);
                        loaded = true;
                    }
                    if (!loaded) {
                        try {
                            java.io.InputStream def = getClass().getResourceAsStream("/images/user_avatar.png");
                            if (def != null) {
                                avatar.setImage(new Image(def));
                                loaded = true;
                            }
                        } catch (Exception ignored3) {}
                    }
                    if (!loaded) {
                        avatar.setFitWidth(36);
                        avatar.setFitHeight(36);
                        avatar.setStyle("-fx-background-color: #dddddd; -fx-background-radius: 50%;");
                    }
                    avatar.setFitWidth(36);
                    avatar.setFitHeight(36);
                    avatar.setPreserveRatio(true);

                    VBox v = new VBox(2);
                    Label name = new Label(c.getDisplayName());
                    name.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
                    Label remark = new Label(c.getRemark() == null || c.getRemark().isEmpty() ? "" : c.getRemark());
                    remark.setFont(Font.font("Microsoft YaHei", 11));
                    remark.setTextFill(Color.GRAY);
                    v.getChildren().addAll(name, remark);

                    HBox row = new HBox(8, avatar, v);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                }
            }
        });
        VBox.setVgrow(contactsListView, Priority.ALWAYS);

        // 双击联系人切换人格（若联系人是 AI）
        contactsListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Contact sel = contactsListView.getSelectionModel().getSelectedItem();
                if (sel != null && sel.isAi()) {
                    String personaPrompt = sel.getSystemPrompt();
                    String greeting = sel.getGreeting();
                    try {
                        suppressPlaybackForHistory = true; // 切换联系人后加载历史时不播报
                        if (chatService != null) {
                            currentPersonaAvatarPath = sel.getAvatarPath();
                            currentContactId = sel.getId();
                            chatService.switchPersona(sel.getId(), sel.getDisplayName(), personaPrompt, greeting);
                            refreshContactInfoSidebar();
                        } else {
                            showAlert("提示", "聊天服务未初始化");
                        }
                    } catch (Exception ex) {
                        showAlert("错误", "切换联系人失败: " + ex.getMessage());
                    }
                } else if (sel != null) {
                    showAlert("提示", "选中联系人不是 AI 聊天对象");
                }
            }
        });

        // populate initial contacts (AI is one contact)
        loadInitialContacts();

        // search
        searchField.textProperty().addListener((obs, oldV, newV) -> refreshContacts(newV == null ? "" : newV.trim()));

        VBox.setVgrow(contactsListView, Priority.ALWAYS);
        contactsPane.getChildren().addAll(topRow, contactsListView);
        return contactsPane;
    }

    private void loadInitialContacts() {
        contactsData.clear();
        ConfigManager cfg = ConfigManager.getInstance();
        // Removed built-in personas loop; now only load persisted contacts
        try {
            List<ContactInfo> userContacts = ContactsService.getInstance().loadAll();
            for (ContactInfo ci : userContacts) {
                String savedRemark = cfg.getContactRemark(ci.getId());
                String remark = (savedRemark != null && !savedRemark.isBlank()) ? savedRemark : ci.getRemark();
                contactsData.add(new Contact(
                        ci.getId(), ci.getDisplayName(), remark, ci.getAvatarPath(), ci.isAi(), ci.getSystemPrompt(), ci.getGreeting()
                ));
                if (currentPersonaAvatarPath == null && ci.getAvatarPath() != null && !ci.getAvatarPath().isBlank()) {
                    currentPersonaAvatarPath = ci.getAvatarPath();
                }
            }
        } catch (Exception e) { System.err.println("加载联系人失败: " + e.getMessage()); }
        sortAndApplyContacts();
    }

    private void sortAndApplyContacts() {
        contactsData.sort((a,b)->{
            ConfigManager cfg = ConfigManager.getInstance();
            boolean ap = cfg.isPinned(a.getId()); boolean bp = cfg.isPinned(b.getId());
            if (ap && !bp) return -1; if (bp && !ap) return 1;
            long ta=0L,tb=0L; try { if (chatService!=null){ ta=chatService.getLastChatEpochMillis(a.getId()); tb=chatService.getLastChatEpochMillis(b.getId()); } } catch(Exception ignored) {}
            if (ta!=tb) return Long.compare(tb, ta); // desc 最近聊天优先
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });
        contactsListView.getItems().setAll(contactsData);
        refreshContactInfoSidebar();
    }

    private void refreshContacts() { refreshContacts(""); }
    private void refreshContacts(String filter) {
        if (filter == null) filter = "";
        String f = filter.toLowerCase();
        List<Contact> filtered = new ArrayList<>();
        for (Contact c : contactsData) {
            if (c.getDisplayName().toLowerCase().contains(f) || (c.getRemark() != null && c.getRemark().toLowerCase().contains(f))) {
                filtered.add(c);
            }
        }
        filtered.sort(Comparator.comparing(c -> c.getDisplayName().toLowerCase()));
        contactsListView.getItems().setAll(filtered);
    }

    // 简单的管理对话框（批量标签/权限/删除 - 实现为示例操作）
    private void manageContacts() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("联系人管理");

        VBox root = new VBox(8);
        root.setPadding(new Insets(8));

        ListView<Contact> lv = new ListView<>();
        lv.getItems().setAll(contactsData);
        lv.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lv.setCellFactory(contactsListView.getCellFactory());
        VBox.setVgrow(lv, Priority.ALWAYS);

        HBox actions = new HBox(8);
        Button tagBtn = new Button("批量打标签");
        Button permBtn = new Button("修改权限");
        Button delBtn = new Button("删除选中");

        tagBtn.setOnAction(e -> showAlert("批量打标签", "示例：已对选中联系人打标签（可扩展）"));
        permBtn.setOnAction(e -> showAlert("修改权限", "示例：已修改权限（可扩展）"));
        delBtn.setOnAction(e -> {
            List<Contact> sel = new ArrayList<>(lv.getSelectionModel().getSelectedItems());
            if (sel.isEmpty()) { showAlert("提示", "请选择要删除的联系人"); return; }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("确认删除");
            confirm.setHeaderText(null);
            confirm.setContentText("确认删除所选联系人？此操作不可恢复。\n数量: " + sel.size());
            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                for (Contact c : sel) {
                    try { ContactsService.getInstance().deleteById(c.getId()); } catch (Exception ignored) {}
                    try { ConfigManager.getInstance().setPinned(c.getId(), false); } catch (Exception ignored) {}
                    try { ConfigManager.getInstance().setContactRemark(c.getId(), ""); } catch (Exception ignored) {}
                }
                contactsData.removeAll(sel);
                sortAndApplyContacts();
                // 刷新历史会话列表以反映删除
                try { refreshSideBarSessions(); } catch (Exception ignored) {}
                showAlert("删除成功", "已删除选中联系人");
                dialog.close();
            }
        });

        actions.getChildren().addAll(tagBtn, permBtn, delBtn);
        root.getChildren().addAll(new Label("选择联系人进行批量操作："), lv, actions);

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // Contact 简单内置类，便于 UI 展示和排序
    private static class Contact {
        private final String id;
        private final String displayName;
        private final String remark;
        private final String avatarPath;
        private final boolean isAi;
        private final String systemPrompt;
        private final String greeting;

        public Contact(String id, String displayName, String remark, String avatarPath, boolean isAi) {
            this.id = id;
            this.displayName = displayName;
            this.remark = remark;
            this.avatarPath = avatarPath;
            this.isAi = isAi;
            this.systemPrompt = null;
            this.greeting = null;
        }
        public Contact(String id, String displayName, String remark, String avatarPath, boolean isAi, String systemPrompt, String greeting) {
            this.id = id;
            this.displayName = displayName;
            this.remark = remark;
            this.avatarPath = avatarPath;
            this.isAi = isAi;
            this.systemPrompt = systemPrompt;
            this.greeting = greeting;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getRemark() { return remark; }
        public String getAvatarPath() { return avatarPath; }
        public boolean isAi() { return isAi; }
        public String getSystemPrompt() { return systemPrompt; }
        public String getGreeting() { return greeting; }

        @Override
        public String toString() { return displayName + (remark == null || remark.isEmpty() ? "" : " ("+remark+")"); }
    }

    // 刷新左侧侧边栏显示的历史会话
    private void applySideBarSessions(List<ChatSession> sessions) {
        if (sideSessionList == null) {
            return;
        }
        sideSessionList.getItems().setAll(sessions == null ? List.of() : sessions);
    }

    private void startBackgroundTask(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void triggerHistorySearch() {
        Platform.runLater(() -> {
            if (sideSessionList == null || chatService == null) {
                return;
            }

            String keyword = historySearchField == null ? "" : historySearchField.getText();
            long version = historySearchVersion.incrementAndGet();
            Task<List<ChatSession>> searchTask = chatService.createSearchSessionsTask(keyword);
            searchTask.setOnSucceeded(event -> {
                if (version != historySearchVersion.get()) {
                    return;
                }
                applySideBarSessions(searchTask.getValue());
            });
            searchTask.setOnFailed(event ->
                    LOGGER.log(Level.WARNING, "Failed to search history messages", searchTask.getException()));
            startBackgroundTask(searchTask, "history-search-" + version);
        });
    }

    public void refreshSideBarSessions() {
        Platform.runLater(() -> {
            if (sideSessionList == null || chatService == null) {
                return;
            }

            long version = historyRefreshVersion.incrementAndGet();
            Task<List<ChatSession>> refreshTask = chatService.createRefreshSessionsTask();
            refreshTask.setOnSucceeded(event -> {
                if (version != historyRefreshVersion.get()) {
                    return;
                }
                sortAndApplyContacts();
                String keyword = historySearchField == null ? "" : historySearchField.getText();
                if (keyword == null || keyword.isBlank()) {
                    applySideBarSessions(refreshTask.getValue());
                } else {
                    triggerHistorySearch();
                }
            });
            refreshTask.setOnFailed(event ->
                    LOGGER.log(Level.WARNING, "Failed to refresh history sidebar", refreshTask.getException()));
            startBackgroundTask(refreshTask, "history-index-build-" + version);
        });
    }

    // 新增：创建对话区上方的联系人标题栏（左对齐）
    private HBox createChatHeader() {
        contactTitleLabel = new Label("");
        contactTitleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 15));
        contactTitleLabel.setTextFill(Color.web("#333"));
        contactTitleLabel.setPadding(new Insets(6, 10, 4, 10));
        HBox box = new HBox(contactTitleLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0));
        box.setStyle("-fx-background-color: transparent;");
        return box;
    }

    // 新增：供服务层/联系人切换时更新当前联系人显示名
    public void setCurrentContactDisplayName(String name) {
        Platform.runLater(() -> {
            if (contactTitleLabel != null) {
                contactTitleLabel.setText(name == null ? "" : name.trim());
            }
        });
    }

    private void showAddContactDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("新增 AI 联系人");
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        ImageView avatarPreview = new ImageView();
        avatarPreview.setFitWidth(64); avatarPreview.setFitHeight(64); avatarPreview.setPreserveRatio(true);
        try { var is = getClass().getResourceAsStream("/images/ai_avatar.png"); if (is != null) avatarPreview.setImage(new Image(is)); } catch (Exception ignored) {}
        final String[] chosenAvatarPath = {"/images/ai_avatar.png"};

        Button chooseAvatarBtn = new Button("选择头像");
        chooseAvatarBtn.setOnAction(e -> {
            java.util.List<String> choices = new java.util.ArrayList<>();
            String imagesDirPath = uiResourceService.getImagesDirPath();
            java.io.File imgDir = new java.io.File(imagesDirPath);
            if (imgDir.exists() && imgDir.isDirectory()) {
                java.io.File[] imgs = imgDir.listFiles((dir, name) -> name.matches("(?i).+\\.(png|jpg|jpeg|gif)"));
                if (imgs != null) for (java.io.File f : imgs) choices.add(f.getAbsolutePath());
            }
            Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("选择头像"); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            String[] selectedPath = new String[1];
            ScrollPane sp = galleryService.buildPreviewGallery(choices, 64, 5, true, chosenAvatarPath[0], true, true, imagesDirPath, selectedPath, getClass(), null);
            dlg.getDialogPane().setContent(sp);
            var r = dlg.showAndWait();
            if (r.isPresent() && r.get() == ButtonType.OK && selectedPath[0] != null) {
                chosenAvatarPath[0] = selectedPath[0];
                Image imgSel = uiResourceService.loadImageFlexible(chosenAvatarPath[0], getClass());
                if (imgSel != null) avatarPreview.setImage(imgSel);
            }
        });
        HBox avatarRow = new HBox(10, avatarPreview, chooseAvatarBtn); avatarRow.setAlignment(Pos.CENTER_LEFT);

        TextField nameField = new TextField(); nameField.setPromptText("名称（必填）");
        TextArea promptArea = new TextArea(); promptArea.setPromptText("系统提示词 / 设定描述（可多行）"); promptArea.setPrefRowCount(5);
        TextField greetingField = new TextField(); greetingField.setPromptText("问候语（可选）");
        ComboBox<String> apiMode = new ComboBox<>(); apiMode.getItems().addAll("程序内置", "自定义密钥"); apiMode.getSelectionModel().selectFirst();
        TextField apiKeyField = new TextField(); apiKeyField.setPromptText("输入自定义 API Key"); apiKeyField.setDisable(true);
        apiMode.valueProperty().addListener((obs, o, n) -> apiKeyField.setDisable(!"自定义密钥".equals(n)));
        HBox apiRow = new HBox(10, new Label("API模式:"), apiMode, apiKeyField); apiRow.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().addAll(new Label("头像"), avatarRow, new Label("名称"), nameField, new Label("设定描述"), promptArea, new Label("问候语"), greetingField, apiRow);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        var res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            String displayName = nameField.getText() == null ? "" : nameField.getText().trim();
            if (displayName.isEmpty()) { showAlert("错误", "名称不能为空"); return; }
            String systemPrompt = promptArea.getText() == null ? "" : promptArea.getText().trim();
            String greeting = greetingField.getText() == null ? "" : greetingField.getText().trim();
            // 改进 ID 生成：如果 displayName 转换前为空（可能是纯中文导致正则过滤），使用 uuid 前缀
            String baseId = displayName.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
            if (baseId.isEmpty()) baseId = "c_" + java.util.UUID.randomUUID().toString().substring(0,8);
            // 生成唯一 id（检查 UI 列表 + 持久化列表）
            String id;
            {
                String candidate = baseId;
                int seq = 2;
                java.util.List<service.ContactInfo> persisted = service.ContactsService.getInstance().loadAll();
                while (true) {
                    boolean conflict = false;
                    for (Contact c : contactsData) { if (c.getId().equalsIgnoreCase(candidate)) { conflict = true; break; } }
                    if (!conflict) {
                        for (service.ContactInfo ci : persisted) { if (ci.getId().equalsIgnoreCase(candidate)) { conflict = true; break; } }
                    }
                    if (!conflict) { id = candidate; break; }
                    candidate = baseId + "_" + (seq++);
                }
            }
            if (!apiKeyField.isDisabled()) { String newKey = apiKeyField.getText().trim(); if (!newKey.isEmpty()) ConfigManager.getInstance().setDeepseekApiKey(newKey); }
            if (systemPrompt.isEmpty()) systemPrompt = "你是" + displayName + "，一个温和、乐于助人的 AI，会用简洁自然的中文回复用户。";
            if (greeting.isEmpty()) greeting = "你好，我是" + displayName + "，很高兴认识你。";
            ContactInfo newInfo = new ContactInfo(id, displayName, "", chosenAvatarPath[0], true, systemPrompt, greeting, true);
            boolean saved = ContactsService.getInstance().add(newInfo);
            if (!saved) { showAlert("错误", "已存在同名或 ID 冲突的联系人"); return; }
            contactsData.add(new Contact(id, displayName, "", chosenAvatarPath[0], true, systemPrompt, greeting));
            sortAndApplyContacts();
            showAlert("成功", "已添加新 AI 联系人: " + displayName);
        }
    }

    private void toggleVoicePlayback() {
        autoPlayEnabled = !autoPlayEnabled;
        ConfigManager.getInstance().setAutoPlayEnabled(autoPlayEnabled); // 保留持久化，仅由此按钮控制
        updateVoiceToggleVisual();
    }
    private void updateVoiceToggleVisual() {
        if (voiceToggleBtn == null) return;
        if (autoPlayEnabled) {
            voiceToggleBtn.setText("🔈");
            voiceToggleBtn.setStyle("-fx-background-color: #1976d2; -fx-text-fill: #000000; -fx-font-size:14; -fx-background-radius:6; -fx-cursor: hand;");
            voiceToggleBtn.setTooltip(new Tooltip("语音播报已开启，点击关闭"));
        } else {
            voiceToggleBtn.setText("🔇");
            voiceToggleBtn.setStyle("-fx-background-color: #f5f5f5; -fx-text-fill: #000000; -fx-font-size:14; -fx-background-radius:6; -fx-cursor: hand;");
            voiceToggleBtn.setTooltip(new Tooltip("语音播报已关闭，点击开启"));
        }
    }

    // 统一功能按钮样式（以时钟按钮样式为标准）
    private void applyFunctionButtonStyle(Button btn) {
        if (btn == null) return;
        btn.setPrefSize(34, 28);
        // transparent background, pointer cursor and consistent font size
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 14;");
        btn.setPadding(Insets.EMPTY);
    }

    public void onHistoryLoaded() { suppressPlaybackForHistory = true; }
    public void onPersonaSwitched() { suppressPlaybackForHistory = true; refreshContactInfoSidebar(); }
    public void onNewChatStarted() { suppressPlaybackForHistory = true; }
    // 联系人信息侧栏功能实现
    private void toggleContactInfoSidebar() {
        if (contactInfoVisible) {
            contactInfoVisible = false;
            boolean collapsed = ConfigManager.getInstance().isSidebarCollapsed();
            if (collapsed) {
                mainLayout.setRight(null);
            } else {
                if (sideBar == null) sideBar = createSideBar();
                mainLayout.setRight(sideBar);
            }
        } else {
            if (contactInfoSidebar == null) contactInfoSidebar = buildContactInfoSidebar();
            mainLayout.setRight(contactInfoSidebar);
            contactInfoVisible = true;
            refreshContactInfoSidebar();
        }
    }
    private VBox buildContactInfoSidebar() {
        VBox vb = new VBox(10);
        vb.setPadding(new Insets(12));
        vb.setPrefWidth(260);
        vb.setStyle("-fx-background-color:#ffffff; -fx-border-color:#e6e6e6; -fx-border-width:0 0 0 1;");
        Label header = new Label("联系人信息"); header.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        infoAvatarView = new ImageView(); infoAvatarView.setFitWidth(72); infoAvatarView.setFitHeight(72); infoAvatarView.setPreserveRatio(true);
        infoNameLabel = new Label(); infoNameLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        infoRemarkLabel = new Label(); infoRemarkLabel.setFont(Font.font("Microsoft YaHei", 12)); infoRemarkLabel.setTextFill(Color.GRAY);
        TextField remarkEditField = new TextField(); remarkEditField.setPromptText("添加备注...");
        Button saveRemarkBtn = new Button("保存备注"); saveRemarkBtn.setStyle("-fx-background-color:#4caf50; -fx-text-fill:white; -fx-background-radius:6;");
        HBox remarkRow = new HBox(8, remarkEditField, saveRemarkBtn); remarkRow.setAlignment(Pos.CENTER_LEFT);
        infoPinnedLabel = new Label(); infoPinnedLabel.setFont(Font.font("Microsoft YaHei", 11)); infoPinnedLabel.setTextFill(Color.DARKGREEN);
        infoPromptArea = new TextArea(); infoPromptArea.setEditable(false); infoPromptArea.setWrapText(true); infoPromptArea.setPrefRowCount(6); infoPromptArea.setStyle("-fx-font-size:12; -fx-background-color:#f9f9f9; -fx-border-color:#ddd;");
        infoGreetingLabel = new Label(); infoGreetingLabel.setFont(Font.font("Microsoft YaHei", 12)); infoGreetingLabel.setWrapText(true);
        infoPinBtn = new Button("置顶"); infoPinBtn.setStyle("-fx-background-color:#1976d2; -fx-text-fill:white; -fx-background-radius:6;");
        infoDeleteBtn = new Button("删除"); infoDeleteBtn.setStyle("-fx-background-color:#f44336; -fx-text-fill:white; -fx-background-radius:6;");
        Button changeAvatarBtn = new Button("更换头像");
        changeAvatarBtn.setStyle("-fx-background-color:#1976d2; -fx-text-fill:white; -fx-background-radius:6;");
        changeAvatarBtn.setOnAction(e -> chooseAvatarFromImages());
        HBox actions = new HBox(10, infoPinBtn, infoDeleteBtn, changeAvatarBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        vb.getChildren().addAll(header, infoAvatarView, infoNameLabel, infoRemarkLabel, remarkRow, infoPinnedLabel, new Label("系统提示词"), infoPromptArea, new Label("问候语"), infoGreetingLabel, actions);
        // 备注保存逻辑
        saveRemarkBtn.setOnAction(e -> {
            Contact c = getCurrentContact(); if (c == null) return;
            String text = remarkEditField.getText()==null?"":remarkEditField.getText().trim();
            ConfigManager.getInstance().setContactRemark(c.getId(), text);
            infoRemarkLabel.setText(text);
            // 用新的备注替换列表中的 Contact 实例
            for (int i=0;i<contactsData.size();i++) {
                Contact old = contactsData.get(i);
                if (old.getId().equals(c.getId())) {
                    Contact updated = new Contact(old.getId(), old.getDisplayName(), text, old.getAvatarPath(), old.isAi(), old.getSystemPrompt(), old.getGreeting());
                    contactsData.set(i, updated);
                    break;
                }
            }
            sortAndApplyContacts(); // 刷新展示
            showAlert("已保存", "备注已保存");
        });
        // 新增：置顶/取消置顶
        infoPinBtn.setOnAction(e -> {
            Contact c = getCurrentContact(); if (c == null) return;
            boolean pinned = ConfigManager.getInstance().isPinned(c.getId());
            ConfigManager.getInstance().setPinned(c.getId(), !pinned);
            sortAndApplyContacts();
            refreshContactInfoSidebar();
        });
        // 新增：删除联系人
        infoDeleteBtn.setOnAction(e -> {
            Contact c = getCurrentContact(); if (c == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("确认删除"); confirm.setHeaderText(null);
            confirm.setContentText("确定要删除联系人：" + c.getDisplayName() + "？此操作不可恢复");
            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                try {
                    // 删除持久化文件（对内置联系人无影响）
                    try { ContactsService.getInstance().deleteById(c.getId()); } catch (Exception ignored) {}
                    // 清理置顶与备注
                    try { ConfigManager.getInstance().setPinned(c.getId(), false); } catch (Exception ignored) {}
                    try { ConfigManager.getInstance().setContactRemark(c.getId(), ""); } catch (Exception ignored) {}
                    // 从内存数据移除
                    contactsData.removeIf(x -> x.getId().equals(c.getId()));

                    // 若删除的是当前联系人
                    if (c.getId().equals(currentContactId)) {
                        // 如果已经没有联系人了，清空聊天并显示欢迎
                        if (contactsData.isEmpty()) {
                            currentContactId = null;
                            currentPersonaAvatarPath = "/images/ai_avatar.png";
                            setCurrentContactDisplayName("");
                            clearChatDisplay();
                            addWelcomeMessage();
                        }
                        // 否则在后面通过 applyCurrentContactAfterChange 自动切换到下一个
                    }

                    sortAndApplyContacts();
                    // 如果还有联系人，自动选中并切换到下一个联系人
                    if (!contactsData.isEmpty()) {
                        applyCurrentContactAfterChange();
                    } else {
                        refreshContactInfoSidebar();
                    }
                    try { refreshSideBarSessions(); } catch (Exception ignored) {}
                    showAlert("已删除", "联系人已删除");
                } catch (Exception ex) {
                    showAlert("删除失败", ex.getMessage());
                }
            }
        });
        return vb;
    }
    private Contact getCurrentContact() { if (currentContactId == null) return null; for (Contact c : contactsData) if (c.getId().equals(currentContactId)) return c; return null; }
    private void refreshContactInfoSidebar() {
        if (!contactInfoVisible || contactInfoSidebar == null) return;
        Contact c = getCurrentContact();
        if (c == null) {
            infoNameLabel.setText("(无联系人)");
            infoRemarkLabel.setText("");
            infoPinnedLabel.setText("");
            infoPromptArea.setText("");
            infoGreetingLabel.setText("");
            infoAvatarView.setImage(null);
            return;
        }
        infoNameLabel.setText(c.getDisplayName());
        infoRemarkLabel.setText(c.getRemark() == null ? "" : c.getRemark());
        boolean pinned = ConfigManager.getInstance().isPinned(c.getId());
        infoPinnedLabel.setText(pinned ? "已置顶" : "未置顶");
        infoPinBtn.setText(pinned ? "取消置顶" : "置顶");
        infoPromptArea.setText(c.getSystemPrompt() == null ? "" : c.getSystemPrompt());
        infoGreetingLabel.setText(c.getGreeting() == null ? "" : c.getGreeting());
        String ap = c.getAvatarPath();
        Image img = uiResourceService.loadImageFlexible(ap, getClass());
        infoAvatarView.setImage(img);
    }

    // 文件数据库管理器：左侧文件（搜索/上传/删除），右侧内容预览（搜索）
    private void showDatabaseViewer() {
        try {
            java.io.File dbDir = new java.io.File(uiResourceService.getDatabaseDirPath());
            if (!dbDir.exists()) { boolean created = dbDir.mkdirs(); if (!created && !dbDir.exists()) { LOGGER.fine("数据库目录创建失败: " + dbDir.getAbsolutePath()); } }

            // 左侧：文件搜索 + 列表 + 上传/删除
            TextField fileSearchField = new TextField();
            fileSearchField.setPromptText("搜索文件名...");
            ListView<java.io.File> filesList = new ListView<>();
            filesList.setPrefWidth(280);
            filesList.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(java.io.File f, boolean empty) {
                    super.updateItem(f, empty);
                    setText(empty || f == null ? null : f.getName());
                }
            });

            // 收集、过滤文件（仅文本类）
            java.util.List<java.io.File> allFiles = new java.util.ArrayList<>();
            Runnable reloadFiles = () -> {
                allFiles.clear();
                java.io.File[] arr = dbDir.listFiles((dir, name) -> name.matches("(?i).+\\.(txt|md|csv|log)"));
                if (arr != null) allFiles.addAll(java.util.Arrays.asList(arr));
                allFiles.sort(java.util.Comparator.comparing(java.io.File::getName, String.CASE_INSENSITIVE_ORDER));
                String kw = fileSearchField.getText() == null ? "" : fileSearchField.getText().trim().toLowerCase();
                if (kw.isEmpty()) {
                    filesList.getItems().setAll(allFiles);
                } else {
                    java.util.List<java.io.File> filtered = new java.util.ArrayList<>();
                    for (java.io.File f : allFiles) if (f.getName().toLowerCase().contains(kw)) filtered.add(f);
                    filesList.getItems().setAll(filtered);
                }
            };

            fileSearchField.textProperty().addListener((obs, ov, nv) -> reloadFiles.run());

            Button uploadBtn = new Button("上传");
            uploadBtn.setOnAction(e -> {
                try {
                    javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                    fc.setTitle("选择要上传的文件");
                    fc.getExtensionFilters().addAll(
                            new javafx.stage.FileChooser.ExtensionFilter("文本文件", "*.txt", "*.md", "*.csv", "*.log"),
                            new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*")
                    );
                    java.util.List<java.io.File> chosen = fc.showOpenMultipleDialog(scene == null ? null : scene.getWindow());
                    if (chosen == null || chosen.isEmpty()) return;
                    for (java.io.File src : chosen) {
                        String base = src.getName();
                        java.io.File dest = new java.io.File(dbDir, base);
                        // 若重名，生成不冲突的文件名
                        if (dest.exists()) {
                            String prefix; String ext;
                            int dot = base.lastIndexOf('.');
                            if (dot > 0) { prefix = base.substring(0, dot); ext = base.substring(dot); }
                            else { prefix = base; ext = ""; }
                            int seq = 1;
                            while (dest.exists()) {
                                dest = new java.io.File(dbDir, prefix + " (" + (seq++) + ")" + ext);
                            }
                        }
                        java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    reloadFiles.run();
                    if (!filesList.getItems().isEmpty()) filesList.getSelectionModel().selectLast();
                } catch (Exception ex) { showAlert("错误", "上传失败: " + ex.getMessage()); }
            });

            Button deleteBtn = new Button("删除");
            deleteBtn.setOnAction(e -> {
                java.io.File sel = filesList.getSelectionModel().getSelectedItem();
                if (sel == null) { showAlert("提示", "请选择要删除的文件"); return; }
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("确认删除"); confirm.setHeaderText(null);
                confirm.setContentText("确定删除文件: " + sel.getName() + " ? 此操作不可恢复");
                var r = confirm.showAndWait();
                if (r.isPresent() && r.get() == ButtonType.OK) {
                    try {
                        java.nio.file.Files.deleteIfExists(sel.toPath());
                        reloadFiles.run();
                    } catch (Exception ex) { showAlert("错误", "删除失败: " + ex.getMessage()); }
                }
            });

            Button refreshBtn = new Button("刷新");
            refreshBtn.setOnAction(e -> reloadFiles.run());

            VBox leftPane = new VBox(8, fileSearchField, filesList, new HBox(8, uploadBtn, deleteBtn, refreshBtn));
            leftPane.setPadding(new Insets(10));
            VBox.setVgrow(filesList, Priority.ALWAYS);

            // 右侧：文件名 + 内容搜索 + 预览
            Label fileNameLabel = new Label("(未选择文件)");
            fileNameLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            TextArea previewArea = new TextArea();
            previewArea.setEditable(false);
            previewArea.setWrapText(true);
            previewArea.setStyle("-fx-font-family: 'Consolas','Microsoft YaHei UI','Monospaced'; -fx-font-size:12;");

            TextField contentSearchField = new TextField();
            contentSearchField.setPromptText("在内容中查找...");
            Button prevBtn = new Button("上一个");
            Button nextBtn = new Button("下一个");
            Label searchStat = new Label("");
            HBox contentSearchBar = new HBox(8, contentSearchField, prevBtn, nextBtn, searchStat);
            contentSearchBar.setAlignment(Pos.CENTER_LEFT);

            final java.util.List<Integer> matchPositions = new java.util.ArrayList<>();
            final int[] currentMatchIndex = new int[]{-1};

            Runnable applySelection = () -> {
                if (matchPositions.isEmpty() || currentMatchIndex[0] < 0) {
                    searchStat.setText("0 / 0");
                    return;
                }
                int pos = matchPositions.get(currentMatchIndex[0]);
                int len = contentSearchField.getText() == null ? 0 : contentSearchField.getText().length();
                try {
                    previewArea.selectRange(pos, pos + len);
                    // 让选区可见
                    previewArea.requestFocus();
                } catch (Exception ignored) {}
                searchStat.setText((currentMatchIndex[0] + 1) + " / " + matchPositions.size());
            };

            Runnable recomputeMatches = () -> {
                matchPositions.clear();
                currentMatchIndex[0] = -1;
                String q = contentSearchField.getText();
                String text = previewArea.getText();
                if (q == null || q.isBlank() || text == null || text.isEmpty()) {
                    searchStat.setText("0 / 0");
                    return;
                }
                String ql = q.toLowerCase();
                String tl = text.toLowerCase();
                int idx = 0;
                while (true) {
                    idx = tl.indexOf(ql, idx);
                    if (idx < 0) break;
                    matchPositions.add(idx);
                    idx += ql.length();
                }
                if (!matchPositions.isEmpty()) { currentMatchIndex[0] = 0; }
                applySelection.run();
            };

            contentSearchField.textProperty().addListener((obs, ov, nv) -> recomputeMatches.run());
            prevBtn.setOnAction(e -> {
                if (matchPositions.isEmpty()) return;
                currentMatchIndex[0] = (currentMatchIndex[0] - 1 + matchPositions.size()) % matchPositions.size();
                applySelection.run();
            });
            nextBtn.setOnAction(e -> {
                if (matchPositions.isEmpty()) return;
                currentMatchIndex[0] = (currentMatchIndex[0] + 1) % matchPositions.size();
                applySelection.run();
            });

            filesList.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
                if (nv == null) { fileNameLabel.setText("(未选择文件)"); previewArea.clear(); matchPositions.clear(); currentMatchIndex[0] = -1; searchStat.setText("0 / 0"); return; }
                fileNameLabel.setText(nv.getName());
                try {
                    String content = java.nio.file.Files.readString(nv.toPath());
                    previewArea.setText(content);
                } catch (Exception ex) {
                    previewArea.setText("<读取失败: " + ex.getMessage() + ">");
                }
                recomputeMatches.run();
            });

            VBox rightPane = new VBox(8, fileNameLabel, contentSearchBar, previewArea);
            rightPane.setPadding(new Insets(10));
            VBox.setVgrow(previewArea, Priority.ALWAYS);

            SplitPane split = new SplitPane(leftPane, rightPane);
            split.setDividerPositions(0.36);

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("数据库管理");
            dlg.getDialogPane().setContent(split);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

            reloadFiles.run();
            dlg.showAndWait();
        } catch (Exception ex) {
            showAlert("错误", "打开数据库管理器失败: " + ex.getMessage());
        }
    }
    // 弹窗查看 resourcse/images 目录图片：可上传/删除任意图片（不裁剪），无文件名文字
    private void showImagesViewer() {
        try {
            java.io.File imgDir = new java.io.File(uiResourceService.getImagesDirPath());
            if (!imgDir.exists() || !imgDir.isDirectory()) { showAlert("提示", "未找到图片目录: " + imgDir.getAbsolutePath()); return; }
            java.io.File[] imgs = imgDir.listFiles((dir, name) -> name.matches("(?i).+\\.(png|jpg|jpeg|gif)"));
            java.util.List<String> paths = new java.util.ArrayList<>();
            if (imgs != null) for (java.io.File f : imgs) paths.add(f.getAbsolutePath());
            String[] selected = new String[1];
            // changed cropOnUpload from false to true so all uploads auto square-crop
            ScrollPane gallery = galleryService.buildPreviewGallery(paths, 72, 6, true, null, true, true, imgDir.getAbsolutePath(), selected, getClass(), null);
            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("图片资源");
            dlg.getDialogPane().setContent(gallery);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
            dlg.showAndWait();
        } catch (Exception ex) { showAlert("错误", "查看图片失败: " + ex.getMessage()); }
    }

    // 供“更换头像”按钮调用：仅从 resourcse/images 选择/管理
    private void chooseAvatarFromImages() {
        Contact c = getCurrentContact();
        if (c == null) { showAlert("提示", "请先选择联系人"); return; }
        java.util.List<String> choices = new java.util.ArrayList<>();
        String imagesDirPath = uiResourceService.getImagesDirPath();
        try {
            java.io.File imgDir = new java.io.File(imagesDirPath);
            if (imgDir.exists() && imgDir.isDirectory()) {
                java.io.File[] imgs = imgDir.listFiles((dir, name) -> name.matches("(?i).+\\.(png|jpg|jpeg|gif)"));
                if (imgs != null) for (java.io.File f : imgs) choices.add(f.getAbsolutePath());
            }
        } catch (Exception ignored) {}
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("选择头像");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        String[] selectedPath = new String[1];
        ScrollPane sp = galleryService.buildPreviewGallery(choices, 64, 5, true, c.getAvatarPath(), true, true, imagesDirPath, selectedPath, getClass(), null);
        dialog.getDialogPane().setContent(sp);
        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            if (selectedPath[0] == null || selectedPath[0].isBlank()) { showAlert("提示", "请先选择一张图片"); return; }
            String chosen = selectedPath[0];
            boolean saved; try { saved = ContactsService.getInstance().updateAvatar(c.getId(), chosen); } catch (Exception ex) { saved = false; }
            if (!saved) { showAlert("错误", "保存头像失败"); return; }
            for (int i = 0; i < contactsData.size(); i++) {
                Contact old = contactsData.get(i);
                if (old.getId().equals(c.getId())) { contactsData.set(i, new Contact(old.getId(), old.getDisplayName(), old.getRemark(), chosen, old.isAi(), old.getSystemPrompt(), old.getGreeting())); break; }
            }
            if (currentContactId != null && currentContactId.equals(c.getId())) {
                currentPersonaAvatarPath = chosen;
                // 新增：立即刷新对话区内已有 AI 消消息的头像
                refreshChatAvatarsForCurrentContact();
            }
            sortAndApplyContacts(); refreshContactInfoSidebar(); showAlert("成功", "头像已更新");
        }
    }

    // 在联系人集合变动后，应用当前联系人：保持原来的 currentContactId，如果被删除则选中列表第一个，并切换聊天
    private void applyCurrentContactAfterChange() {
        if (contactsListView == null || contactsData.isEmpty()) {
            currentContactId = null;
            setCurrentContactDisplayName("");
            return;
        }
        if (currentContactId != null) {
            boolean found = false;
            for (Contact c : contactsData) {
                if (c.getId().equals(currentContactId)) { found = true; break; }
            }
            if (found) {
                for (int i = 0; i < contactsListView.getItems().size(); i++) {
                    if (contactsListView.getItems().get(i).getId().equals(currentContactId)) {
                        contactsListView.getSelectionModel().select(i);
                        break;
                    }
                }
                refreshContactInfoSidebar();
                return;
            }
        }
        Contact first = contactsData.getFirst();
        currentContactId = first.getId();
        setCurrentContactDisplayName(first.getDisplayName());
        currentPersonaAvatarPath = first.getAvatarPath();
        if (contactsListView.getItems().isEmpty()) contactsListView.getItems().setAll(contactsData);
        contactsListView.getSelectionModel().select(0);
        try {
            if (chatService != null && first.isAi()) {
                suppressPlaybackForHistory = true;
                chatService.switchPersona(first.getId(), first.getDisplayName(), first.getSystemPrompt(), first.getGreeting());
            }
        } catch (Exception ex) {
            showAlert("错误", "切换联系人失败: " + ex.getMessage());
        }
        refreshContactInfoSidebar();
    }

    // 刷新当前联系人的历史 AI 消息头像
    private void refreshChatAvatarsForCurrentContact() {
        if (chatContentContainer == null || currentPersonaAvatarPath == null) return;
        Image newAvatar = uiResourceService.loadImageFlexible(currentPersonaAvatarPath, getClass());
        if (newAvatar == null) return;
        for (javafx.scene.Node node : chatContentContainer.getChildren()) {
            if (node instanceof HBox row) {
                if (!row.getChildren().isEmpty() && row.getChildren().getFirst() instanceof ImageView iv) {
                    iv.setImage(newAvatar);
                }
            }
        }
    }

    // 滚动到底部（聊天内容区域）
    private void scrollToBottom() {
        Platform.runLater(() -> {
            if (scrollPane != null) scrollPane.setVvalue(1.0);
        });
    }

    private Image loadWindowIcon() {
        URL icoResource = getClass().getResource("/images/EchoSoul.ico");
        Image icoImage = loadIcoWindowIcon(icoResource);
        if (icoImage != null) {
            return icoImage;
        }

        Path externalIco = Path.of(System.getProperty("user.dir"), "resources", "images", "EchoSoul.ico");
        if (Files.exists(externalIco)) {
            try {
                Image externalImage = loadIcoWindowIcon(externalIco.toUri().toURL());
                if (externalImage != null) {
                    return externalImage;
                }
            } catch (Exception ignored) {
            }
        }

        // PNG fallback when ICO is unavailable on current runtime/platform
        String[] primaryPngCandidates = {
                "/images/EchoSoul.png"
        };
        for (String candidate : primaryPngCandidates) {
            try (InputStream in = getClass().getResourceAsStream(candidate)) {
                if (in == null) {
                    continue;
                }
                Image image = new Image(in);
                if (!image.isError() && image.getWidth() > 0) {
                    return image;
                }
            } catch (Exception ignored) {
            }
        }

        Path externalPng = Path.of(System.getProperty("user.dir"), "resources", "images", "EchoSoul.png");
        if (Files.exists(externalPng)) {
            try (InputStream in = Files.newInputStream(externalPng)) {
                Image image = new Image(in);
                if (!image.isError() && image.getWidth() > 0) {
                    return image;
                }
            } catch (Exception ignored) {
            }
        }

        String[] candidates = {
                "/images/app_icon.png",
                "/images/ai_avatar.png",
                "/images/user_avatar.png"
        };
        for (String candidate : candidates) {
            try (InputStream in = getClass().getResourceAsStream(candidate)) {
                if (in == null) {
                    continue;
                }
                Image image = new Image(in);
                if (!image.isError() && image.getWidth() > 0) {
                    return image;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }


    private Image loadIcoWindowIcon(URL url) {
        if (url == null) {
            return null;
        }

        try {
            Image directImage = new Image(url.toExternalForm());
            if (!directImage.isError() && directImage.getWidth() > 0) {
                return directImage;
            }
        } catch (Exception ignored) {
        }

        try {
            ImageIcon icon = new ImageIcon(url);
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }

            BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = buffered.createGraphics();
            try {
                icon.paintIcon(null, graphics, 0, 0);
            } finally {
                graphics.dispose();
            }
            return SwingFXUtils.toFXImage(buffered, null);
        } catch (Exception ignored) {
            return null;
        }
    }
}

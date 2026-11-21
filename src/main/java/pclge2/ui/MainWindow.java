package pclge2.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import pclge2.core.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * PCLGE2 主窗口（JavaFX）—— 结构对齐原版 PCL / PCLCE：
 * 顶部一级导航：启动 / 基岩版 / 下载 / 设置 / 工具 / 联机；
 * 每个一级页内部是「左侧子页列表 + 右侧内容」双栏；底部常驻日志面板。
 * 视觉皮肤取 PCLCE 官方配色与控件样式（Application.xaml / MyButton / MyRadioButton / MyListItem）。
 */
public final class MainWindow {

    // PCLCE 官方配色（取自 Application.xaml 的 ColorBrush 表）
    private static final String BG = "#eaf2fe";          // ColorBrush8
    private static final String CARD = "#ffffff";
    private static final String PRIMARY = "#1370f3";      // ColorBrush3
    private static final String PRIMARY_DARK = "#0b5bcb"; // ColorBrush2
    private static final String TITLE_BAR = "#4890f5";    // ColorBrush4
    private static final String TEXT = "#343d4a";         // ColorBrush1
    private static final String GRAY2 = "#737373";

    private final Stage stage;
    private final BorderPane root = new BorderPane();
    private final StackPane pageHost = new StackPane();
    private final TextArea logArea = new TextArea();
    private final TextArea logPageArea = new TextArea();
    private final Label statusLabel = new Label("就绪");
    private final ProgressBar progressBar = new ProgressBar(0);

    private static final String[] NAV = {"启动", "基岩版", "下载", "设置", "工具", "联机"};
    private final Button[] navButtons = new Button[NAV.length];
    private final Node[] pages = new Node[NAV.length];

    // 通用
    private final TextField playerNameField = new TextField(PathConfig.get("playerName", "Steve"));
    private final Spinner<Integer> memorySpinner = new Spinner<>(512, 16384,
            Integer.parseInt(PathConfig.get("memory", "2048")), 256);
    private final ComboBox<String> setupSourceBox = new ComboBox<>();
    private final ComboBox<String> downloadSourceBox = new ComboBox<>();
    private final Label gameDirLabel = new Label();
    private final Label javaLabel = new Label(GameLauncher.findJava().toString());
    private final Label launchJavaLabel = new Label(GameLauncher.findJava().toString());
    private final TextField jvmArgsField = new TextField(PathConfig.get("jvmArgs", ""));

    // 启动页
    private final ListView<String> launchVersionList = new ListView<>();
    private final Label launchVersionTitle = new Label("未选择版本");

    // 基岩版
    private final Label bedrockStatus = new Label("未检测到基岩版服务端目录");
    private final TextField bedrockPortField = new TextField("19132");
    private final Label bedrockPortLabel = new Label("端口 19132");

    // 下载页
    private final ListView<String> downloadList = new ListView<>();
    private final ComboBox<String> loaderBox = new ComboBox<>();
    private final TextField loaderVersionField = new TextField();
    private final ListView<String> localVersionList = new ListView<>();
    private final ListView<String> shopResultList = new ListView<>();
    private final TextField shopQueryField = new TextField();
    private final TextField shopTargetVersionField = new TextField();
    private final ComboBox<String> shopLoaderBox = new ComboBox<>();
    private final Label shopStatus = new Label("");
    private final ListView<String> dlCategory = new ListView<>();
    private final StackPane dlHost = new StackPane();
    private String shopCurrentType = "mod";

    // 设置 / 版本管理
    private final ListView<String> setupNavList = new ListView<>();
    private final StackPane setupHost = new StackPane();
    private final ListView<String> instanceList = new ListView<>();
    private final ListView<String> instanceModList = new ListView<>();
    private final ListView<String> instancePackList = new ListView<>();
    private final ListView<String> instanceSaveList = new ListView<>();

    // 工具
    private final ListView<String> toolsNavList = new ListView<>();
    private final StackPane toolsHost = new StackPane();
    private final TextField littleSkinUser = new TextField();
    private final TextField skinTargetName = new TextField("Steve");
    private final Label skinStatus = new Label("");

    // 联机
    private final LanChat lanChat = new LanChat();
    private final TextField chatNickField = new TextField("Steve");
    private final TextArea chatArea = new TextArea();
    private final TextField chatInput = new TextField();
    private final ListView<String> peerList = new ListView<>();
    private final Button chatToggleButton = new Button("启动联机");

    public MainWindow(Stage stage) {
        this.stage = stage;
        buildUi();
        wire();
        refreshAll();
    }

    // ================= 顶栏与框架 =================
    private void buildUi() {
        Label logo = new Label("PCLGE2");
        logo.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 17));
        logo.setTextFill(Color.web("#ffffff"));
        Label ver = new Label("2.00");
        ver.setFont(Font.font("Microsoft YaHei UI", 12));
        ver.setTextFill(Color.web("#d5e6fd"));

        HBox nav = new HBox(2);
        for (int i = 0; i < NAV.length; i++) {
            Button b = new Button(NAV[i]);
            b.setStyle(navStyle(false));
            b.setMinHeight(27);
            b.setMinWidth(64);
            navButtons[i] = b;
            nav.getChildren().add(b);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, logo, ver, spacer, nav);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setMinHeight(48);
        top.setPrefHeight(48);
        top.setPadding(new Insets(0, 12, 0, 14));
        top.setStyle("-fx-background-color: " + TITLE_BAR + ";");
        root.setTop(top);

        pages[0] = buildLaunchPage();
        pages[1] = buildBedrockPage();
        pages[2] = buildDownloadPage();
        pages[3] = buildSetupPage();
        pages[4] = buildToolsPage();
        pages[5] = buildLinkPage();
        pageHost.getChildren().addAll(pages);
        pageHost.setStyle("-fx-background-color: " + BG + ";");
        root.setCenter(pageHost);

        logArea.setEditable(false);
        logArea.setPrefRowCount(3);
        logArea.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 12px; -fx-text-fill: " + GRAY2 + "; -fx-background-color: transparent;");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox statusBar = new HBox(8, statusLabel, progressBar);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 10, 4, 10));
        VBox bottom = new VBox(logArea, statusBar);
        bottom.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e0eafd; -fx-border-width: 1 0 0 0;");
        root.setBottom(bottom);

        showPage(0);
        Scene scene = new Scene(root, 1100, 720);
        java.net.URL css = MainWindow.class.getResource("/pclge2/pclge2.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.setTitle("PCLGE2");
    }

    /** PCL 导航胶囊：选中=白底蓝字，未选中=透明白字，悬停=50% 白 */
    private String navStyle(boolean active) {
        return active
                ? "-fx-background-color: #ffffff; -fx-text-fill: " + PRIMARY
                + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 13.5; -fx-border-color: transparent; -fx-border-width: 0; -fx-padding: 5 14 5 14; -fx-cursor: hand;"
                : "-fx-background-color: transparent; -fx-text-fill: #ffffff; -fx-font-size: 13px;"
                + " -fx-background-radius: 13.5; -fx-border-color: transparent; -fx-border-width: 0; -fx-padding: 5 14 5 14; -fx-cursor: hand;";
    }

    private void showPage(int index) {
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisible(i == index);
            navButtons[i].setStyle(navStyle(i == index));
        }
        pages[index].toFront();
        pages[index].setVisible(true);
    }

    // ================= 原子组件 =================
    private VBox card(Node... content) {
        VBox box = new VBox(8, content);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: " + CARD + "; -fx-background-radius: 8;"
                + " -fx-effect: dropshadow(gaussian, rgba(52,61,74,0.08), 8, 0, 0, 2);");
        return box;
    }

    private Label title(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 14));
        l.setTextFill(Color.web(TEXT));
        return l;
    }

    private Button primary(String text) {
        // PCL 按钮：白底 + 主题色文字 + 1px 主题色边框，圆角 3
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #ffffff; -fx-text-fill: " + PRIMARY_DARK
                + "; -fx-font-size: 13px; -fx-border-color: " + PRIMARY
                + "; -fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 5 16 5 16; -fx-cursor: hand;");
        return b;
    }

    /** PCL 普通按钮 */
    private Button normal(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #ffffff; -fx-text-fill: " + TEXT
                + "; -fx-font-size: 13px; -fx-border-color: #d5e6fd"
                + "; -fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 5 14 5 14; -fx-cursor: hand;");
        return b;
    }

    /** 左侧子页列表栏 */
    private VBox sideBar(String heading, Node... content) {
        VBox box = new VBox(8, title(heading));
        box.getChildren().addAll(content);
        box.setPadding(new Insets(12, 8, 12, 12));
        box.setPrefWidth(250);
        box.setMinWidth(250);
        // 左侧栏用 PCL 的半透明白（ColorBrushBackgroundTransparentSidebar = F1FFFFFF）
        box.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 8;");
        return box;
    }

    private HBox twoPane(Node left, Node right) {
        HBox box = new HBox(12, left, right);
        box.setPadding(new Insets(12));
        HBox.setHgrow(right, Priority.ALWAYS);
        return box;
    }

    // ================= 启动页 =================
    private Node buildLaunchPage() {
        launchVersionList.setPrefHeight(400);
        Button changeDir = new Button("切换游戏目录");
        changeDir.setOnAction(e -> chooseGameDir());
        Button refresh = new Button("刷新");
        refresh.setOnAction(e -> refreshLaunchVersions());
        launchVersionList.getSelectionModel().selectedItemProperty().addListener(
                (o, a, b) -> launchVersionTitle.setText(b == null ? "未选择版本" : b));

        Button launchBtn = primary("启动游戏");
        launchBtn.setPrefWidth(180);
        launchBtn.setOnAction(e -> launchGame());

        GridPane panel = new GridPane();
        panel.setHgap(10);
        panel.setVgap(10);
        panel.add(new Label("版本"), 0, 0);
        panel.add(launchVersionTitle, 1, 0);
        panel.add(new Label("离线账户"), 0, 1);
        panel.add(playerNameField, 1, 1);
        panel.add(new Label("内存(MB)"), 0, 2);
        panel.add(memorySpinner, 1, 2);
        panel.add(new Label("Java"), 0, 3);
        launchJavaLabel.setMinWidth(320);
        panel.add(launchJavaLabel, 1, 3);
        panel.add(launchBtn, 1, 4);

        VBox right = new VBox(12,
                card(title("启动游戏"), panel),
                card(title("公告"),
                        new Label("PCLGE2 Java 版 2.00"),
                        new Label("原版游戏下载，Fabric / Quilt / Forge / NeoForge 安装，"),
                        new Label("模组 / 资源包 / 整合包从 Modrinth 下载，皮肤与局域网联机。")));
        VBox.setVgrow(right, Priority.ALWAYS);
        return twoPane(sideBar("版本列表", launchVersionList, new HBox(6, changeDir, refresh)), right);
    }

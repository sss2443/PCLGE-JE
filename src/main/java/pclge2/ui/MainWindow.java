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

    // ================= 基岩版页 =================
    private Node buildBedrockPage() {
        Button chooseBds = new Button("选择 BDS 目录");
        chooseBds.setOnAction(e -> {
            DirectoryChooser ch = new DirectoryChooser();
            ch.setTitle("选择 bedrock_server 目录");
            File dir = ch.showDialog(stage);
            if (dir != null) {
                PathConfig.put("bedrockDir", dir.getAbsolutePath());
                bedrockStatus.setText("已关联：" + dir.getAbsolutePath());
                log("基岩版服务端目录：" + dir.getAbsolutePath());
            }
        });
        VBox installPane = new VBox(10,
                new Label("基岩版服务端（BDS）安装说明"),
                new Label("① 从 minecraft.net 下载对应版本的 bedrock_server 压缩包"),
                new Label("② 解压到任意目录，选择该目录完成关联"),
                new Label("③ 启动：Windows 用 bedrock_server.exe，Linux 用 ./bedrock_server"),
                new Label("默认端口 19132（UDP）"),
                new HBox(8, new Label("端口"), bedrockPortField),
                chooseBds);

        bedrockStatus.setTextFill(Color.web("#27ae60"));
        Button startBds = new Button("启动服务端");
        startBds.setOnAction(e -> startBedrock());
        VBox managePane = new VBox(10,
                new Label("服务端状态"), bedrockStatus,
                new HBox(8, startBds, bedrockPortLabel));
        bedrockPortField.textProperty().addListener((o, a, b) -> bedrockPortLabel.setText("端口 " + b));

        TabPane tabs = new TabPane();
        Tab t1 = new Tab("安装", installPane);
        Tab t2 = new Tab("管理", managePane);
        t1.setClosable(false);
        t2.setClosable(false);
        tabs.getTabs().addAll(t1, t2);
        tabs.setPrefHeight(400);

        VBox right = new VBox(12, card(title("基岩版"), tabs));
        VBox.setVgrow(right, Priority.ALWAYS);
        return twoPane(sideBar("基岩版", new Label("安装 / 管理")), right);
    }

    // ================= 下载页 =================
    private Node buildDownloadPage() {
        downloadSourceBox.getItems().addAll("官方源（Mojang）", "BMCLAPI 镜像");
        downloadSourceBox.setMinHeight(26);
        downloadSourceBox.setPrefWidth(200);
        downloadSourceBox.getSelectionModel().select(Integer.parseInt(PathConfig.get("source", "1")));
        downloadSourceBox.setOnAction(e -> PathConfig.put("source",
                String.valueOf(downloadSourceBox.getSelectionModel().getSelectedIndex())));

        // ---- 游戏版本面板
        downloadList.setPrefHeight(280);
        downloadList.setPlaceholder(new Label("点「刷新版本列表」加载可用游戏版本"));
        loaderBox.getItems().addAll("原版（无加载器）", "Fabric", "Quilt", "Forge", "NeoForge");
        loaderBox.setMinHeight(26);
        loaderBox.getSelectionModel().select(0);
        loaderVersionField.setPromptText("加载器版本（留空=自动最新）");
        Button fetchVersions = new Button("刷新版本列表");
        fetchVersions.setOnAction(e -> fetchRemoteVersions());
        Button installBtn = primary("下载 / 安装");
        installBtn.setOnAction(e -> installSelectedVersion());

        localVersionList.setPrefHeight(110);
        localVersionList.setPlaceholder(new Label("暂无已安装版本"));
        VBox gamePane = new VBox(10,
                new HBox(8, new Label("下载源"), downloadSourceBox, fetchVersions),
                downloadList,
                new HBox(8, new Label("加载器"), loaderBox, new Label("版本"), loaderVersionField),
                installBtn,
                new Label("已安装"), localVersionList);

        // ---- 整合包 / 模组 / 资源包（Modrinth）
        shopQueryField.setPromptText("搜索关键词（留空=热门）");
        shopTargetVersionField.setPromptText("目标 MC 版本，如 1.20.1");
        shopLoaderBox.getItems().addAll("fabric", "quilt", "forge", "neoforge");
        shopLoaderBox.getSelectionModel().select(0);
        shopLoaderBox.setMinHeight(26);

        Button searchBtn = primary("搜索");
        searchBtn.setOnAction(e -> searchModrinth());

        shopResultList.setPrefHeight(340);
        shopStatus.setTextFill(Color.web("#7f8c8d"));
        VBox shopPane = new VBox(10,
                new HBox(8, shopQueryField, shopTargetVersionField, shopLoaderBox),
                new HBox(8, searchBtn, shopStatus),
                shopResultList,
                new Label("双击结果即可下载到「目标 MC 版本」对应的实例"));

        shopResultList.setOnMouseClicked(e -> {
            if (e.getClickCount() != 2) {
                return;
            }
            installFromShop();
        });

        // ---- 分类左栏：4 项 → 0 游戏版本，1/2/3 共用 Modrinth 面板
        dlCategory.setItems(FXCollections.observableArrayList("游戏版本", "整合包", "模组", "资源包"));
        dlHost.getChildren().addAll(gamePane, shopPane);
        shopPane.setVisible(false);
        gamePane.setVisible(true);
        dlCategory.getSelectionModel().selectFirst();
        dlCategory.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            int idx = b.intValue();
            gamePane.setVisible(idx == 0);
            shopPane.setVisible(idx != 0);
            shopCurrentType = idx == 1 ? "modpack" : idx == 2 ? "mod" : "resourcepack";
            shopResultList.setItems(FXCollections.observableArrayList());
            shopStatus.setText("");
        });

        VBox right = new VBox(12, card(title("下载"), dlHost));
        VBox.setVgrow(right, Priority.ALWAYS);
        return twoPane(sideBar("分类", dlCategory, new Separator(),
                new Label("版本列表 / 模组 / 资源包 / 整合包")), right);
    }

    // ================= 设置页（含版本管理） =================
    private Node buildSetupPage() {
        gameDirLabel.setWrapText(true);

        Button chooseDir = new Button("选择游戏目录");
        chooseDir.setOnAction(e -> chooseGameDir());
        Button resetDir = new Button("恢复默认");
        resetDir.setOnAction(e -> {
            try {
                PathConfig.resetGameDir();
                refreshAll();
                status("已恢复默认游戏目录");
            } catch (IOException ex) {
                status("失败：" + ex.getMessage());
            }
        });
        Button moveSaves = new Button("移动存档到当前目录");
        moveSaves.setOnAction(e -> moveSavesDialog());
        VBox launcherPane = new VBox(12,
                card(title("游戏目录"), gameDirLabel, new HBox(8, chooseDir, resetDir)),
                card(title("存档迁移"), new Label("把旧目录下的世界复制到当前目录（同名跳过）"), moveSaves));

        javaLabel.setWrapText(true);
        Button saveJvm = new Button("保存");
        saveJvm.setOnAction(e -> {
            PathConfig.put("jvmArgs", jvmArgsField.getText().trim());
            status("JVM 参数已保存");
        });
        VBox javaPane = new VBox(12,
                card(title("Java"), new Label("当前 Java"), javaLabel),
                card(title("JVM 参数"), new HBox(8, jvmArgsField, saveJvm)));

        setupSourceBox.getItems().addAll("官方源（Mojang）", "BMCLAPI 镜像");
        setupSourceBox.setMinHeight(26);
        setupSourceBox.getSelectionModel().select(Integer.parseInt(PathConfig.get("source", "1")));
        setupSourceBox.setOnAction(e -> PathConfig.put("source",
                String.valueOf(setupSourceBox.getSelectionModel().getSelectedIndex())));
        VBox downloadPane = new VBox(12,
                card(title("下载源"), new HBox(8, new Label("默认来源"), setupSourceBox)));

        // 版本管理
        instanceList.setPrefHeight(300);
        instanceList.setPrefWidth(200);
        instanceList.getSelectionModel().selectedItemProperty().addListener(
                (o, a, b) -> refreshInstanceDetail(b));
        Button toggleMod = new Button("启用 / 禁用");
        toggleMod.setOnAction(e -> toggleInstanceMod());
        Button addMod = new Button("添加模组(.jar)");
        addMod.setOnAction(e -> addModDialog());
        Button delMod = new Button("删除");
        delMod.setOnAction(e -> deleteInstanceMod());
        VBox modsBox = new VBox(8, new HBox(6, toggleMod, addMod, delMod), instanceModList);
        VBox.setVgrow(instanceModList, Priority.ALWAYS);

        TabPane tabs = new TabPane();
        Tab t1 = new Tab("模组", modsBox);
        Tab t2 = new Tab("资源包", instancePackList);
        Tab t3 = new Tab("存档", instanceSaveList);
        t1.setClosable(false);
        t2.setClosable(false);
        t3.setClosable(false);
        tabs.getTabs().addAll(t1, t2, t3);
        tabs.setPrefHeight(340);

        Button refreshIns = new Button("刷新");
        refreshIns.setOnAction(e -> refreshInstances());
        VBox managePane = new VBox(10,
                new HBox(8, new Label("版本"), instanceList, refreshIns),
                card(title("版本内容"), tabs));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox aboutPane = new VBox(12, card(title("关于 PCLGE2"),
                new Label("PCLGE2 2.00（Java 17 + JavaFX 版）"),
                new Label("界面皮肤取自 PCLCE 官方配色与控件样式"),
                new Label("配置文件：" + PathConfig.launcherDir().resolve("pclge-config.properties"))));

        setupHost.getChildren().addAll(launcherPane, javaPane, downloadPane, managePane, aboutPane);
        setupNavList.setItems(FXCollections.observableArrayList("启动器", "Java", "下载", "版本管理", "关于"));
        for (int i = 1; i < setupHost.getChildren().size(); i++) {
            setupHost.getChildren().get(i).setVisible(false);
        }
        setupNavList.getSelectionModel().selectFirst();
        setupNavList.setPrefHeight(200);
        setupNavList.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            int idx = b.intValue();
            for (int i = 0; i < setupHost.getChildren().size(); i++) {
                setupHost.getChildren().get(i).setVisible(i == idx);
            }
        });

        return twoPane(sideBar("设置", setupNavList), setupHost);
    }

    // ================= 工具页 =================
    private Node buildToolsPage() {
        littleSkinUser.setPromptText("LittleSkin 用户名");
        Button lsBtn = primary("从 LittleSkin 下载");
        lsBtn.setOnAction(e -> downloadLittleSkinSkin());
        Button importBtn = new Button("导入本地皮肤");
        importBtn.setOnAction(e -> importSkinDialog());
        Button customBtn = new Button("自定义皮肤");
        customBtn.setOnAction(e -> customSkinDialog());
        skinStatus.setTextFill(Color.web("#27ae60"));
        VBox skinPane = new VBox(10,
                card(title("LittleSkin 下载"), new HBox(8, littleSkinUser, lsBtn)),
                card(title("导入皮肤"), importBtn),
                card(title("自定义皮肤"), new HBox(8, new Label("账户"), skinTargetName, customBtn)),
                skinStatus,
                new Label("皮肤目录：" + PathConfig.skinsDir()));

        TextField achItem = new TextField("minecraft:diamond");
        TextField achTitle = new TextField("钻石！");
        TextField achDesc1 = new TextField("挖到钻石");
        TextField achDesc2 = new TextField("");
        VBox achPane = new VBox(10,
                new Label("成就图生成（样式同原版成就弹窗）"),
                card(new HBox(8, new Label("物品 ID"), achItem),
                        new HBox(8, new Label("成就名"), achTitle),
                        new HBox(8, new Label("描述1"), achDesc1),
                        new HBox(8, new Label("描述2"), achDesc2)));

        logPageArea.setEditable(false);
        logPageArea.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 12px;");
        logPageArea.setPrefHeight(420);
        VBox logPane = new VBox(8,
                new HBox(8, title("运行日志"), clearLogButton()), logPageArea);
        VBox.setVgrow(logPageArea, Priority.ALWAYS);

        VBox helpPane = new VBox(10,
                new Label("常见问题"),
                new Label("· 游戏启动失败：先在「设置 → Java」确认 Java 路径"),
                new Label("· 下载慢：在「设置 → 下载」切到 BMCLAPI 镜像"),
                new Label("· 模组不显示：确认模组版本与游戏版本匹配"),
                new Label("· 联机看不到对方：同一局域网，或加入同一 EasyTier 网络"),
                new Label("· 皮肤：LittleSkin 用户名即皮肤站角色名"));

        toolsHost.getChildren().addAll(skinPane, achPane, logPane, helpPane);
        toolsNavList.setItems(FXCollections.observableArrayList("皮肤", "成就图", "日志", "帮助"));
        for (int i = 1; i < toolsHost.getChildren().size(); i++) {
            toolsHost.getChildren().get(i).setVisible(false);
        }
        toolsNavList.getSelectionModel().selectFirst();
        toolsNavList.setPrefHeight(200);
        toolsNavList.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            int idx = b.intValue();
            for (int i = 0; i < toolsHost.getChildren().size(); i++) {
                toolsHost.getChildren().get(i).setVisible(i == idx);
            }
        });

        return twoPane(sideBar("工具", toolsNavList), toolsHost);
    }

    private Button clearLogButton() {
        Button b = new Button("清空");
        b.setOnAction(e -> {
            logArea.clear();
            logPageArea.clear();
        });
        return b;
    }

    // ================= 联机页 =================
    private Node buildLinkPage() {
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefHeight(400);
        chatInput.setPromptText("输入聊天内容，回车发送");
        peerList.setPrefHeight(360);

        VBox left = sideBar("联机",
                new Label("UDP " + LanChat.PORT + " 局域网广播"),
                new Label("EasyTier 网段内可直接互见"),
                peerList);
        VBox right = card(title("聊天"),
                new HBox(8, new Label("昵称"), chatNickField, chatToggleButton),
                chatArea, chatInput);
        VBox.setVgrow(chatArea, Priority.ALWAYS);
        return twoPane(left, right);
    }

    // ================= 事件绑定 =================
    private void wire() {
        for (int i = 0; i < navButtons.length; i++) {
            final int idx = i;
            navButtons[i].setOnAction(e -> showPage(idx));
        }
        chatInput.setOnAction(e -> sendChat());
        chatToggleButton.setOnAction(e -> toggleChat());
        lanChat.setOnChat((u, m) -> Platform.runLater(() -> chatArea.appendText(u + "：" + m + "\n")));
        lanChat.setOnMessage(m -> Platform.runLater(() -> status(m)));
        lanChat.setOnPeersChanged(() -> Platform.runLater(() ->
                peerList.setItems(FXCollections.observableArrayList(lanChat.peers()))));
        playerNameField.textProperty().addListener((o, a, b) -> PathConfig.put("playerName", b));
        memorySpinner.valueProperty().addListener((o, a, b) -> PathConfig.put("memory", String.valueOf(b)));
    }

    private Downloader.Source currentSource() {
        return "0".equals(PathConfig.get("source", "1"))
                ? Downloader.Source.MOJANG : Downloader.Source.BMCL;
    }

    private void refreshAll() {
        refreshLaunchVersions();
        refreshInstances();
        localVersionList.setItems(FXCollections.observableArrayList(VersionManager.installedVersions()));
        gameDirLabel.setText(PathConfig.gameDir().toAbsolutePath().toString());
        javaLabel.setText(GameLauncher.findJava().toString());
        launchJavaLabel.setText(GameLauncher.findJava().toString());
        String bds = PathConfig.get("bedrockDir", "");
        if (!bds.isBlank()) {
            bedrockStatus.setText("已关联：" + bds);
        }
    }

    private void refreshLaunchVersions() {
        launchVersionList.setItems(FXCollections.observableArrayList(VersionManager.installedVersions()));
    }

    private void refreshInstances() {
        instanceList.setItems(FXCollections.observableArrayList(VersionManager.installedVersions()));
    }

    private void refreshInstanceDetail(String version) {
        if (version == null) {
            return;
        }
        instanceModList.setItems(FXCollections.observableArrayList(
                ModManager.list(version).stream().map(m -> (m.enabled() ? "[开] " : "[关] ") + m.name()).toList()));
        instancePackList.setItems(FXCollections.observableArrayList(ModManager.listResourcePacks(version)));
        instanceSaveList.setItems(FXCollections.observableArrayList(ModManager.listSaves(version)));
    }

    // ================= 下载逻辑 =================
    private void fetchRemoteVersions() {
        status("获取版本列表...");
        new Thread(() -> {
            try {
                List<VersionManager.VersionEntry> list =
                        VersionManager.fetchRemoteVersions(currentSource(), s -> Platform.runLater(() -> log(s)));
                Platform.runLater(() -> {
                    downloadList.setItems(FXCollections.observableArrayList(
                            list.stream().map(v -> v.id() + "  [" + v.type() + "]").toList()));
                    status("版本列表已更新：" + list.size() + " 个");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status("获取失败：" + ex.getMessage()));
            }
        }, "fetch").start();
    }

    private void installSelectedVersion() {
        int idx = downloadList.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            status("先在左侧选一个版本");
            return;
        }
        String versionId = downloadList.getItems().get(idx).split("  ")[0].trim();
        Downloader.Source source = currentSource();
        String loaderName = loaderBox.getSelectionModel().getSelectedItem();
        String loaderVersion = loaderVersionField.getText().trim();

        status("开始安装 " + versionId + " ...");
        new Thread(() -> {
            try {
                String url = VersionManager.fetchRemoteVersions(source, s -> {
                }).stream().filter(v -> v.id().equals(versionId))
                        .map(VersionManager.VersionEntry::url).findFirst()
                        .orElseThrow(() -> new IOException("清单中没有该版本"));
                VersionManager.install(versionId, url, source,
                        (text, p) -> Platform.runLater(() -> {
                            status(text);
                            progressBar.setProgress(p);
                        }),
                        s -> Platform.runLater(() -> log(s)));

                if (loaderName != null && !loaderName.startsWith("原版")) {
                    String installed;
                    if ("Fabric".equals(loaderName)) {
                        List<String> vs = ModLoaderInstaller.fabricLoaderVersions(versionId);
                        if (!loaderVersion.isBlank() && vs.contains(loaderVersion)) {
                            vs = List.of(loaderVersion);
                        }
                        installed = ModLoaderInstaller.installFabric(versionId, vs, source,
                                s -> Platform.runLater(() -> log(s)));
                    } else if ("Quilt".equals(loaderName)) {
                        installed = ModLoaderInstaller.installQuilt(versionId,
                                ModLoaderInstaller.quiltLoaderVersions(versionId), source,
                                s -> Platform.runLater(() -> log(s)));
                    } else {
                        if (loaderVersion.isBlank()) {
                            throw new IOException("Forge/NeoForge 需要手动填加载器版本");
                        }
                        ModLoaderInstaller.Loader l = "Forge".equals(loaderName)
                                ? ModLoaderInstaller.Loader.FORGE : ModLoaderInstaller.Loader.NEOFORGE;
                        installed = ModLoaderInstaller.installInstallerBased(l, versionId, loaderVersion, source,
                                s -> Platform.runLater(() -> log(s)));
                    }
                    String fin = installed;
                    Platform.runLater(() -> status("已安装：" + versionId + " + " + loaderName + " → " + fin));
                } else {
                    Platform.runLater(() -> status("安装完成：" + versionId));
                }
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    refreshAll();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status("安装失败：" + ex.getMessage());
                    progressBar.setProgress(0);
                });
            }
        }, "install").start();
    }

    private void searchModrinth() {
        String query = shopQueryField.getText().trim();
        String target = shopTargetVersionField.getText().trim();
        shopStatus.setText("搜索中...");
        new Thread(() -> {
            try {
                List<Modrinth.Hit> hits = Modrinth.search(query, shopCurrentType, target, 20);
                Platform.runLater(() -> {
                    shopResultList.setItems(FXCollections.observableArrayList(
                            hits.stream().map(h -> h.title() + "  (" + h.downloads() + " 次下载)  #" + h.slug()).toList()));
                    shopStatus.setText("找到 " + hits.size() + " 个结果，双击安装");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> shopStatus.setText("搜索失败：" + ex.getMessage()));
            }
        }, "modrinth-search").start();
    }

    private void installFromShop() {
        String item = shopResultList.getSelectionModel().getSelectedItem();
        if (item == null) {
            return;
        }
        String slug = item.substring(item.lastIndexOf('#') + 1);
        String targetVersion = shopTargetVersionField.getText().trim();
        String loader = shopLoaderBox.getSelectionModel().getSelectedItem();
        String targetInstance = targetVersion.isEmpty()
                ? launchVersionList.getSelectionModel().getSelectedItem() : targetVersion;
        if (targetInstance == null) {
            shopStatus.setText("先填目标版本或选中一个已安装版本");
            return;
        }
        String type = shopCurrentType;
        new Thread(() -> {
            try {
                Platform.runLater(() -> shopStatus.setText("下载中..."));
                if ("modpack".equals(type)) {
                    Modrinth.installModpack(slug, targetVersion, targetInstance,
                            s -> Platform.runLater(() -> log(s)));
                } else {
                    Modrinth.install(slug, type, targetVersion,
                            "mod".equals(type) ? loader : null, targetInstance,
                            s -> Platform.runLater(() -> log(s)));
                }
                Platform.runLater(() -> shopStatus.setText("安装完成：" + slug));
            } catch (Exception ex) {
                Platform.runLater(() -> shopStatus.setText("安装失败：" + ex.getMessage()));
            }
        }, "modrinth-install").start();
    }

    // ================= 启动 / 目录 / 存档 / 基岩版 =================
    private void launchGame() {
        String version = launchVersionList.getSelectionModel().getSelectedItem();
        if (version == null) {
            status("先在左侧选一个版本");
            return;
        }
        String name = playerNameField.getText().trim();
        if (name.isEmpty()) {
            status("填写离线账户名");
            return;
        }
        Account.Profile profile = Account.Profile.offline(name);
        status("启动 " + version);
        new Thread(() -> {
            try {
                int code = GameLauncher.launch(new GameLauncher.LaunchOptions(version, profile.name(), profile.uuid(),
                        profile.accessToken(), memorySpinner.getValue(),
                        jvmArgsField.getText().isBlank() ? null : List.of(jvmArgsField.getText().split(" "))),
                        s -> Platform.runLater(() -> log(s)));
                Platform.runLater(() -> status("游戏已退出，代码 " + code));
            } catch (Exception ex) {
                Platform.runLater(() -> status("启动失败：" + ex.getMessage()));
            }
        }, "launch").start();
    }

    private void chooseGameDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择 .minecraft 目录");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }
        try {
            PathConfig.setGameDir(dir.toPath());
            refreshAll();
            status("游戏目录已改为：" + dir.getAbsolutePath());
        } catch (IOException ex) {
            status("设置失败：" + ex.getMessage());
        }
    }

    private void moveSavesDialog() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择旧游戏目录");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }
        Path sourceDir = dir.toPath();
        status("移动存档中...");
        new Thread(() -> {
            try {
                int count = PathConfig.moveSaves(sourceDir, PathConfig.gameDir());
                Platform.runLater(() -> status("已复制 " + count + " 个世界"));
            } catch (Exception ex) {
                Platform.runLater(() -> status("移动失败：" + ex.getMessage()));
            }
        }, "move-saves").start();
    }

    private void startBedrock() {
        String dir = PathConfig.get("bedrockDir", "");
        if (dir.isBlank() || !Files.isDirectory(Path.of(dir))) {
            status("先选择 BDS 目录");
            return;
    }
        try {
            Path exe = VersionManager.osName().equals("windows")
                    ? Path.of(dir, "bedrock_server.exe") : Path.of(dir, "bedrock_server");
            if (!Files.exists(exe)) {
                status("目录里没有 bedrock_server");
                return;
            }
            new ProcessBuilder(exe.toString()).directory(new File(dir)).start();
            status("基岩版服务端已启动，端口 " + bedrockPortField.getText());
        } catch (IOException ex) {
            status("启动失败：" + ex.getMessage());
        }
    }

    // ================= 实例操作 =================
    private void toggleInstanceMod() {
        String version = instanceList.getSelectionModel().getSelectedItem();
        String mod = instanceModList.getSelectionModel().getSelectedItem();
        if (version == null || mod == null) {
            status("先选版本和模组");
            return;
        }
        try {
            ModManager.toggle(version, mod.substring(mod.indexOf(']') + 2));
            refreshInstanceDetail(version);
            status("已切换");
        } catch (IOException ex) {
            status("切换失败：" + ex.getMessage());
        }
    }

    private void addModDialog() {
        String version = instanceList.getSelectionModel().getSelectedItem();
        if (version == null) {
            status("先选版本");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择模组 jar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("模组", "*.jar"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            ModManager.add(version, file.toPath());
            refreshInstanceDetail(version);
            status("已添加：" + file.getName());
        } catch (IOException ex) {
            status("添加失败：" + ex.getMessage());
        }
    }

    private void deleteInstanceMod() {
        String version = instanceList.getSelectionModel().getSelectedItem();
        String mod = instanceModList.getSelectionModel().getSelectedItem();
        if (version == null || mod == null) {
            return;
        }
        try {
            ModManager.delete(version, mod.substring(mod.indexOf(']') + 2));
            refreshInstanceDetail(version);
            status("已删除");
        } catch (IOException ex) {
            status("删除失败：" + ex.getMessage());
        }
    }

    // ================= 联机 / 皮肤 =================
    private void toggleChat() {
        if (chatToggleButton.getText().startsWith("停止")) {
            lanChat.close();
            chatToggleButton.setText("启动联机");
            status("联机已停止");
            return;
        }
        try {
            lanChat.start(chatNickField.getText().isBlank() ? "玩家" : chatNickField.getText().trim());
            chatToggleButton.setText("停止联机");
            status("联机已启动，端口 " + LanChat.PORT);
            new Thread(() -> {
                while (chatToggleButton.getText().startsWith("停止")) {
                    lanChat.heartbeat();
                    try {
                        Thread.sleep(20_000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "heartbeat").start();
        } catch (IOException ex) {
            status("启动失败：" + ex.getMessage());
        }
    }

    private void sendChat() {
        String msg = chatInput.getText().trim();
        if (msg.isEmpty()) {
            return;
        }
        try {
            lanChat.send(msg);
            chatArea.appendText("我：" + msg + "\n");
            chatInput.clear();
        } catch (IOException ex) {
            status("发送失败：" + ex.getMessage());
        }
    }

    private void downloadLittleSkinSkin() {
        String user = littleSkinUser.getText().trim();
        if (!Account.validUserName(user)) {
            skinStatus.setText("用户名只能字母、数字、下划线，1-16 位");
            return;
        }
        skinStatus.setText("正在下载...");
        new Thread(() -> {
            try {
                Path file = Account.downloadLittleSkin(user);
                Platform.runLater(() -> skinStatus.setText("已下载：" + file));
            } catch (Exception ex) {
                Platform.runLater(() -> skinStatus.setText("下载失败：" + ex.getMessage()));
            }
        }, "ls-skin").start();
    }

    private void importSkinDialog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择皮肤图片");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            skinStatus.setText("已导入：" + Account.importSkin(file.toPath(), skinTargetName.getText().trim()));
        } catch (IOException ex) {
            skinStatus.setText("导入失败：" + ex.getMessage());
        }
    }

    private void customSkinDialog() {
        String target = skinTargetName.getText().trim();
        if (target.isEmpty()) {
            skinStatus.setText("先填账户名");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择自定义皮肤");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            skinStatus.setText("自定义皮肤已应用：" + Account.importSkin(file.toPath(), target));
        } catch (IOException ex) {
            skinStatus.setText("设置失败：" + ex.getMessage());
        }
    }

    // ================= 日志 =================
    private void status(String text) {
        statusLabel.setText(text);
        log(text);
    }

    private void log(String text) {
        logArea.appendText(text + "\n");
        logPageArea.appendText(text + "\n");
    }

    /** 供启动参数 / 自动化截图使用：直接切到指定一级页 */
    public void openPage(int index) {
        if (index >= 0 && index < pages.length) {
            showPage(index);
        }
    }

    public void show() {
        stage.show();
    }

    public void shutdown() {
        lanChat.close();
    }
}

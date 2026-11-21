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

/** PCLGE2 主窗口：buildUi + 启动 / 基岩版 / 下载三页。 */
public abstract class MainWindowPages extends MainWindowBase {

    public MainWindowPages(Stage stage) {
        super(stage);
    }

    protected void buildUi() {
        Label logo = new Label("PCLGE2");
        logo.setFont(Font.font(fontFamily(), FontWeight.BOLD, 17));
        logo.setTextFill(Color.web("#ffffff"));
        Label ver = new Label("2.00");
        ver.setFont(Font.font(fontFamily(), 12));
        ver.setTextFill(Color.web("#d5e6fd"));

        HBox nav = new HBox(2);
        for (int i = 0; i < NAV.length; i++) {
            Button b = new Button(NAV[i]);
            b.setStyle(navStyle(false));
            b.setMinHeight(30);
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

        root.setStyle("-fx-font-family: \"" + fontFamily() + "\";");
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
    protected String navStyle(boolean active) {
        return active
                ? "-fx-background-color: #ffffff; -fx-text-fill: " + PRIMARY
                + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 13.5; -fx-border-color: transparent; -fx-border-width: 0; -fx-padding: 5 14 5 14; -fx-cursor: hand;"
                : "-fx-background-color: transparent; -fx-text-fill: #ffffff; -fx-font-size: 13px;"
                + " -fx-background-radius: 13.5; -fx-border-color: transparent; -fx-border-width: 0; -fx-padding: 5 14 5 14; -fx-cursor: hand;";
    }


    // ================= 启动页 =================
    protected Node buildLaunchPage() {
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
    protected Node buildBedrockPage() {
        // ---- 在线下载（官方接口）
        ComboBox<String> bdsChannel = new ComboBox<>();
        bdsChannel.getItems().addAll("正式版", "预览版");
        bdsChannel.getSelectionModel().selectFirst();
        bdsChannel.setMinHeight(30);
        Label bdsLatest = new Label("点右侧按钮获取最新版本号");
        bdsLatest.setTextFill(Color.web(GRAY2));
        Button bdsCheck = normal("检查更新");
        Button bdsDownloadBtn = primary("下载并安装 BDS");
        Label bdsPathLabel = new Label("安装到：" + BedrockDownloader.bedrockRoot());
        bdsPathLabel.setTextFill(Color.web(GRAY2));
        bdsPathLabel.setWrapText(true);
        bdsDownloadBtn.setDisable(true);
        final BedrockDownloader.Link[] picked = new BedrockDownloader.Link[1];

        Consumer<List<BedrockDownloader.Link>> resolve = links -> {
            try {
                picked[0] = BedrockDownloader.pick(links, bdsChannel.getSelectionModel().getSelectedIndex() == 1);
                bdsLatest.setText("最新版本：" + picked[0].version()
                        + (picked[0].preview() ? "（预览版）" : "（正式版）")
                        + "，" + (VersionManager.osName().equals("windows") ? "Windows" : "Linux") + " 包");
                bdsLatest.setTextFill(Color.web("#27ae60"));
                bdsDownloadBtn.setDisable(false);
            } catch (Exception ex) {
                bdsLatest.setText("没有可用链接：" + ex.getMessage());
                bdsLatest.setTextFill(Color.web("#e74c3c"));
                bdsDownloadBtn.setDisable(true);
            }
        };

        bdsCheck.setOnAction(e -> {
            bdsLatest.setText("查询官方接口...");
            bdsLatest.setTextFill(Color.web(GRAY2));
            new Thread(() -> {
                try {
                    List<BedrockDownloader.Link> links = BedrockDownloader.fetchLinks();
                    Platform.runLater(() -> resolve.accept(links));
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        bdsLatest.setText("接口请求失败：" + ex.getMessage());
                        bdsLatest.setTextFill(Color.web("#e74c3c"));
                    });
                }
            }, "bds-check").start();
        });

        bdsChannel.setOnAction(e -> {
            if (bdsLatest.getText().startsWith("最新版本")) {
                bdsCheck.fire();
            }
        });

        bdsDownloadBtn.setOnAction(e -> {
            BedrockDownloader.Link link = picked[0];
            if (link == null) {
                return;
            }
            bdsDownloadBtn.setDisable(true);
            status("开始下载基岩版服务端 " + link.version());
            new Thread(() -> {
                try {
                    Path dir = BedrockDownloader.downloadAndInstall(link,
                            s -> Platform.runLater(() -> log(s)),
                            p -> Platform.runLater(() -> progressBar.setProgress(p)));
                    Platform.runLater(() -> {
                        progressBar.setProgress(0);
                        bedrockStatus.setText("已关联：" + dir);
                        bdsPathLabel.setText("安装到：" + dir);
                        status("基岩版服务端安装完成：" + dir);
                        refreshAll();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        progressBar.setProgress(0);
                        status("下载失败：" + ex.getMessage());
                    });
                } finally {
                    Platform.runLater(() -> bdsDownloadBtn.setDisable(false));
                }
            }, "bds-download").start();
        });

        VBox downloadBox = card(title("下载基岩版服务端（官方源）"),
                new HBox(8, new Label("渠道"), bdsChannel, bdsCheck),
                bdsLatest,
                bdsDownloadBtn,
                bdsPathLabel,
                new Label("下载后自动解压并关联服务端目录，无需手动选目录"));

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
                downloadBox,
                card(title("手动关联已解压的 BDS 目录"),
                        new Label("① 解压 bedrock_server 压缩包到任意目录"),
                        new Label("② 启动：Windows 用 bedrock_server.exe，Linux 用 ./bedrock_server"),
                        new Label("默认端口 19132（UDP）"),
                        new HBox(8, new Label("端口"), bedrockPortField),
                        chooseBds));

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
    protected Node buildDownloadPage() {
        downloadSourceBox.getItems().addAll("官方源（Mojang）", "BMCLAPI 镜像");
        downloadSourceBox.setMinHeight(30);
        downloadSourceBox.setPrefWidth(240);
        downloadSourceBox.getSelectionModel().select(Integer.parseInt(PathConfig.get("source", "1")));
        downloadSourceBox.setOnAction(e -> PathConfig.put("source",
                String.valueOf(downloadSourceBox.getSelectionModel().getSelectedIndex())));

        // ---- 游戏版本面板
        downloadList.setPrefHeight(280);
        downloadList.setPlaceholder(new Label("点「刷新版本列表」加载可用游戏版本"));
        loaderBox.getItems().addAll("原版（无加载器）", "Fabric", "Quilt", "Forge", "NeoForge");
        loaderBox.setMinHeight(30);
        loaderBox.setPrefWidth(200);
        loaderBox.getSelectionModel().select(0);
        loaderVersionField.setPromptText("加载器版本（留空=自动最新）");
        loaderVersionField.setPrefWidth(260);
        loaderVersionField.setMinWidth(260);
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
        shopLoaderBox.setMinHeight(30);
        shopLoaderBox.setPrefWidth(150);

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

    protected abstract Node buildSetupPage();

    protected abstract Node buildToolsPage();

    protected abstract Node buildLinkPage();
}

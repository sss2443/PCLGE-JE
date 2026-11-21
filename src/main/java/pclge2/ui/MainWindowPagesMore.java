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

/** PCLGE2 主窗口：设置 / 工具 / 联机三页。 */
public abstract class MainWindowPagesMore extends MainWindowPages {

    public MainWindowPagesMore(Stage stage) {
        super(stage);
    }

    // ================= 设置页（含版本管理） =================
    protected Node buildSetupPage() {
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
        setupSourceBox.setMinHeight(30);
        setupSourceBox.setPrefWidth(240);
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
    protected Node buildToolsPage() {
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
                new Label("· 皮肤：LittleSkin 用户名即皮肤站角色名"),
                new Label("· 基岩版：先在「基岩版 → 安装」下载 BDS，再点「管理」启动"));

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

    protected Button clearLogButton() {
        Button b = new Button("清空");
        b.setOnAction(e -> {
            logArea.clear();
            logPageArea.clear();
        });
        return b;
    }

    // ================= 联机页 =================
    protected Node buildLinkPage() {
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

}

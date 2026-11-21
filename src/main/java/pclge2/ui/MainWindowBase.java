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

/** PCLGE2 主窗口基底：状态字段 + 通用控件构造。 */
public abstract class MainWindowBase {

    // PCLCE 官方配色（取自 Application.xaml 的 ColorBrush 表）
    protected static final String BG = "#eaf2fe";          // ColorBrush8
    protected static final String CARD = "#ffffff";
    protected static final String PRIMARY = "#1370f3";      // ColorBrush3
    protected static final String PRIMARY_DARK = "#0b5bcb"; // ColorBrush2
    protected static final String TITLE_BAR = "#4890f5";    // ColorBrush4
    protected static final String TEXT = "#343d4a";         // ColorBrush1
    protected static final String GRAY2 = "#737373";

    /** MC Next 字体所在资源；加载失败则回退系统字体 */
    protected static final String FONT_RES = "/pclge2/fonts/Minecraft_Next_Font_12px.ttf";
    protected static String fontFamily;

    protected static synchronized String fontFamily() {
        if (fontFamily == null) {
            Font loaded = null;
            try {
                loaded = Font.loadFont(MainWindow.class.getResourceAsStream(FONT_RES), 12);
            } catch (Exception ignored) {
            }
            fontFamily = loaded != null ? loaded.getFamily() : "Microsoft YaHei UI";
        }
        return fontFamily;
    }

    protected final Stage stage;
    protected final BorderPane root = new BorderPane();
    protected final StackPane pageHost = new StackPane();
    protected final TextArea logArea = new TextArea();
    protected final TextArea logPageArea = new TextArea();
    protected final Label statusLabel = new Label("就绪");
    protected final ProgressBar progressBar = new ProgressBar(0);

    protected static final String[] NAV = {"启动", "基岩版", "下载", "设置", "工具", "联机"};
    protected final Button[] navButtons = new Button[NAV.length];
    protected final Node[] pages = new Node[NAV.length];

    // 通用
    protected final TextField playerNameField = new TextField(PathConfig.get("playerName", "Steve"));
    protected final Spinner<Integer> memorySpinner = new Spinner<>(512, 16384,
            Integer.parseInt(PathConfig.get("memory", "2048")), 256);
    protected final ComboBox<String> setupSourceBox = new ComboBox<>();
    protected final ComboBox<String> downloadSourceBox = new ComboBox<>();
    protected final Label gameDirLabel = new Label();
    protected final Label javaLabel = new Label(GameLauncher.findJava().toString());
    protected final Label launchJavaLabel = new Label(GameLauncher.findJava().toString());
    protected final TextField jvmArgsField = new TextField(PathConfig.get("jvmArgs", ""));

    // 启动页
    protected final ListView<String> launchVersionList = new ListView<>();
    protected final Label launchVersionTitle = new Label("未选择版本");

    // 基岩版
    protected final Label bedrockStatus = new Label("未检测到基岩版服务端目录");
    protected final TextField bedrockPortField = new TextField("19132");
    protected final Label bedrockPortLabel = new Label("端口 19132");

    // 下载页
    protected final ListView<String> downloadList = new ListView<>();
    protected final ComboBox<String> loaderBox = new ComboBox<>();
    protected final TextField loaderVersionField = new TextField();
    protected final ListView<String> localVersionList = new ListView<>();
    protected final ListView<String> shopResultList = new ListView<>();
    protected final TextField shopQueryField = new TextField();
    protected final TextField shopTargetVersionField = new TextField();
    protected final ComboBox<String> shopLoaderBox = new ComboBox<>();
    protected final Label shopStatus = new Label("");
    protected final ListView<String> dlCategory = new ListView<>();
    protected final StackPane dlHost = new StackPane();
    protected String shopCurrentType = "mod";

    // 设置 / 版本管理
    protected final ListView<String> setupNavList = new ListView<>();
    protected final StackPane setupHost = new StackPane();
    protected final ListView<String> instanceList = new ListView<>();
    protected final ListView<String> instanceModList = new ListView<>();
    protected final ListView<String> instancePackList = new ListView<>();
    protected final ListView<String> instanceSaveList = new ListView<>();

    // 工具
    protected final ListView<String> toolsNavList = new ListView<>();
    protected final StackPane toolsHost = new StackPane();
    protected final TextField littleSkinUser = new TextField();
    protected final TextField skinTargetName = new TextField("Steve");
    protected final Label skinStatus = new Label("");

    // 联机
    protected final LanChat lanChat = new LanChat();
    protected final TextField chatNickField = new TextField("Steve");
    protected final TextArea chatArea = new TextArea();
    protected final TextField chatInput = new TextField();
    protected final ListView<String> peerList = new ListView<>();
    protected final Button chatToggleButton = new Button("启动联机");


    public MainWindowBase(Stage stage) {
        this.stage = stage;
        buildUi();
        wire();
        refreshAll();
    }

    protected void showPage(int index) {
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisible(i == index);
            navButtons[i].setStyle(navStyle(i == index));
        }
        pages[index].toFront();
        pages[index].setVisible(true);
    }

    // ================= 原子组件 =================
    protected VBox card(Node... content) {
        VBox box = new VBox(8, content);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: " + CARD + "; -fx-background-radius: 8;"
                + " -fx-effect: dropshadow(gaussian, rgba(52,61,74,0.08), 8, 0, 0, 2);");
        return box;
    }

    protected Label title(String text) {
        Label l = new Label(text);
        l.setFont(Font.font(fontFamily(), FontWeight.BOLD, 14));
        l.setTextFill(Color.web(TEXT));
        return l;
    }

    protected Button primary(String text) {
        // PCL 按钮：白底 + 主题色文字 + 1px 主题色边框，圆角 3
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #ffffff; -fx-text-fill: " + PRIMARY_DARK
                + "; -fx-font-size: 13px; -fx-border-color: " + PRIMARY
                + "; -fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 5 16 5 16; -fx-cursor: hand;");
        return b;
    }

    /** PCL 普通按钮 */
    protected Button normal(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #ffffff; -fx-text-fill: " + TEXT
                + "; -fx-font-size: 13px; -fx-border-color: #d5e6fd"
                + "; -fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 5 14 5 14; -fx-cursor: hand;");
        return b;
    }

    /** 左侧子页列表栏 */
    protected VBox sideBar(String heading, Node... content) {
        VBox box = new VBox(8, title(heading));
        box.getChildren().addAll(content);
        box.setPadding(new Insets(12, 8, 12, 12));
        box.setPrefWidth(250);
        box.setMinWidth(250);
        // 左侧栏用 PCL 的半透明白（ColorBrushBackgroundTransparentSidebar = F1FFFFFF）
        box.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 8;");
        return box;
    }

    protected HBox twoPane(Node left, Node right) {
        HBox box = new HBox(12, left, right);
        box.setPadding(new Insets(12));
        HBox.setHgrow(right, Priority.ALWAYS);
        return box;
    }

    protected void refreshLaunchVersions() {
    }

    protected void refreshInstances() {
    }

    protected void refreshInstanceDetail(String version) {
    }

    protected void chooseGameDir() {
    }

    protected void moveSavesDialog() {
    }

    protected void launchGame() {
    }

    protected void fetchRemoteVersions() {
    }

    protected void installSelectedVersion() {
    }

    protected void searchModrinth() {
    }

    protected void installFromShop() {
    }

    protected void startBedrock() {
    }

    protected void toggleInstanceMod() {
    }

    protected void addModDialog() {
    }

    protected void deleteInstanceMod() {
    }

    protected void toggleChat() {
    }

    protected void sendChat() {
    }

    protected void downloadLittleSkinSkin() {
    }

    protected void importSkinDialog() {
    }

    protected void customSkinDialog() {
    }


    protected abstract String navStyle(boolean active);

    protected abstract void buildUi();

    protected abstract void wire();

    protected abstract void refreshAll();

    // ================= 日志 =================
    protected void status(String text) {
        statusLabel.setText(text);
        log(text);
    }

    protected void log(String text) {
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

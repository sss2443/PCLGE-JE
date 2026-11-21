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

/** PCLGE2 主窗口：启动、目录、实例、联机与皮肤行为。 */
public final class MainWindow extends MainWindowActions {

    public MainWindow(Stage stage) {
        super(stage);
    }

    // ================= 启动 / 目录 / 存档 / 基岩版 =================
    protected void launchGame() {
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

    protected void chooseGameDir() {
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

    protected void moveSavesDialog() {
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

    protected void startBedrock() {
        String dir = PathConfig.get("bedrockDir", "");
        if (dir.isBlank() || !Files.isDirectory(Path.of(dir))) {
            status("先下载或选择 BDS 目录");
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
    protected void toggleInstanceMod() {
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

    protected void addModDialog() {
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

    protected void deleteInstanceMod() {
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
    protected void toggleChat() {
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

    protected void sendChat() {
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

    protected void downloadLittleSkinSkin() {
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

    protected void importSkinDialog() {
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

    protected void customSkinDialog() {
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

}

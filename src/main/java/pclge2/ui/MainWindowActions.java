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

/** PCLGE2 主窗口：事件绑定 - 刷新 - 下载逻辑。 */
public abstract class MainWindowActions extends MainWindowPagesMore {

    public MainWindowActions(Stage stage) {
        super(stage);
    }

    // ================= 事件绑定 =================
    protected void wire() {
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

    protected Downloader.Source currentSource() {
        return "0".equals(PathConfig.get("source", "1"))
                ? Downloader.Source.MOJANG : Downloader.Source.BMCL;
    }

    protected void refreshAll() {
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

    protected void refreshLaunchVersions() {
        launchVersionList.setItems(FXCollections.observableArrayList(VersionManager.installedVersions()));
    }

    protected void refreshInstances() {
        instanceList.setItems(FXCollections.observableArrayList(VersionManager.installedVersions()));
    }

    protected void refreshInstanceDetail(String version) {
        if (version == null) {
            return;
        }
        instanceModList.setItems(FXCollections.observableArrayList(
                ModManager.list(version).stream().map(m -> (m.enabled() ? "[开] " : "[关] ") + m.name()).toList()));
        instancePackList.setItems(FXCollections.observableArrayList(ModManager.listResourcePacks(version)));
        instanceSaveList.setItems(FXCollections.observableArrayList(ModManager.listSaves(version)));
    }


    // ================= 下载逻辑 =================
    protected void fetchRemoteVersions() {
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

    protected void installSelectedVersion() {
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

    protected void searchModrinth() {
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

    protected void installFromShop() {
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


    protected abstract void launchGame();

    protected abstract void chooseGameDir();

    protected abstract void moveSavesDialog();

    protected abstract void startBedrock();

    protected abstract void toggleInstanceMod();

    protected abstract void addModDialog();

    protected abstract void deleteInstanceMod();

    protected abstract void toggleChat();

    protected abstract void sendChat();

    protected abstract void downloadLittleSkinSkin();

    protected abstract void importSkinDialog();

    protected abstract void customSkinDialog();
}

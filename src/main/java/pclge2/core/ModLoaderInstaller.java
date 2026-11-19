package pclge2.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * 模组加载器安装：Fabric / Quilt / Forge / NeoForge。
 * Fabric、Quilt 直接生成版本 JSON（无需 installer）；Forge、NeoForge 下载 installer 并执行。
 */
public final class ModLoaderInstaller {

    public enum Loader {
        FABRIC("Fabric"), QUILT("Quilt"), FORGE("Forge"), NEOFORGE("NeoForge");

        public final String display;

        Loader(String display) {
            this.display = display;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ModLoaderInstaller() {
    }

    /** Fabric：拿可用 loader 版本列表 */
    public static List<String> fabricLoaderVersions(String mcVersion) throws IOException, InterruptedException {
        String body = Downloader.getText("https://meta.fabricmc.net/v2/versions/loader/" + mcVersion);
        List<String> out = new ArrayList<>();
        for (JsonElement e : JsonParser.parseString(body).getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("loader")) {
                out.add(o.getAsJsonObject("loader").get("version").getAsString());
            }
        }
        if (out.isEmpty()) {
            throw new IOException("Fabric 没有适配 " + mcVersion + " 的加载器");
        }
        return out;
    }

    /** Quilt：拿可用 loader 版本列表 */
    public static List<String> quiltLoaderVersions(String mcVersion) throws IOException, InterruptedException {
        String body = Downloader.getText("https://meta.quiltmc.org/v3/versions/loader/" + mcVersion);
        List<String> out = new ArrayList<>();
        for (JsonElement e : JsonParser.parseString(body).getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("loader")) {
                out.add(o.getAsJsonObject("loader").get("version").getAsString());
            }
        }
        if (out.isEmpty()) {
            throw new IOException("Quilt 没有适配 " + mcVersion + " 的加载器");
        }
        return out;
    }

    /**
     * 安装 Fabric：拉 loader profile JSON，改名为 <mc>-fabric-<loader> 写入 versions。
     */
    public static String installFabric(String mcVersion, List<String> loaders, Downloader.Source source,
                                       Consumer<String> log) throws IOException, InterruptedException {
        String loader = loaders.get(0);
        String id = mcVersion + "-fabric-" + loader;
        log.accept("安装 Fabric " + loader + " 到 " + mcVersion);

        String profileUrl = "https://meta.fabricmc.net/v2/versions/loader/" + mcVersion + "/" + loader + "/profile/json";
        String body = Downloader.getText(profileUrl);
        JsonObject profile = JsonParser.parseString(body).getAsJsonObject();

        // Fabric profile 的 id 改成本地目录名，inheritsFrom 指向原版
        profile.addProperty("id", id);
        Path dir = PathConfig.versionsDir().resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(id + ".json"), GSON.toJson(profile));
        log.accept("Fabric 安装完成：" + id);
        return id;
    }

    /** 安装 Quilt：逻辑与 Fabric 类似 */
    public static String installQuilt(String mcVersion, List<String> loaders, Downloader.Source source,
                                      Consumer<String> log) throws IOException, InterruptedException {
        String loader = loaders.get(0);
        String id = mcVersion + "-quilt-" + loader;
        log.accept("安装 Quilt " + loader + " 到 " + mcVersion);
        String profileUrl = "https://meta.quiltmc.org/v3/versions/loader/" + mcVersion + "/" + loader + "/profile/json";
        String body = Downloader.getText(profileUrl);
        JsonObject profile = JsonParser.parseString(body).getAsJsonObject();
        profile.addProperty("id", id);
        Path dir = PathConfig.versionsDir().resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(id + ".json"), GSON.toJson(profile));
        log.accept("Quilt 安装完成：" + id);
        return id;
    }

    /** 查 Forge 是否已安装（找 versions 下带 forge 标记的目录） */
    public static Optional<String> findInstalled(String mcVersion, Loader loader) {
        String marker = switch (loader) {
            case FORGE -> "-forge-";
            case NEOFORGE -> "-neoforge-";
            case FABRIC -> "-fabric-";
            case QUILT -> "-quilt-";
        };
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(PathConfig.versionsDir())) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (name.startsWith(mcVersion) && name.contains(marker)) {
                    return Optional.of(name);
                }
            }
        } catch (IOException ignored) {
        }
        return Optional.empty();
    }

    /**
     * 安装 Forge / NeoForge：下载官方 installer 并执行 --installClient。
     */
    public static String installInstallerBased(Loader loader, String mcVersion, String loaderVersion,
                                               Downloader.Source source, Consumer<String> log)
            throws IOException, InterruptedException {
        String url;
        String fileName;
        if (loader == Loader.FORGE) {
            fileName = "forge-" + mcVersion + "-" + loaderVersion + "-installer.jar";
            url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + mcVersion + "-" + loaderVersion + "/"
                    + fileName;
        } else {
            fileName = "neoforge-" + loaderVersion + "-installer.jar";
            url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + loaderVersion + "/" + fileName;
        }
        Path installer = PathConfig.launcherDir().resolve("installers").resolve(fileName);
        log.accept("下载 " + loader.display + " 安装器：" + url);
        Downloader.download(url, installer);

        log.accept("运行安装器（可能需要几分钟）...");
        List<String> cmd = List.of(GameLauncher.findJava().toString(), "-jar", installer.toString(),
                "--installClient", PathConfig.gameDir().toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.accept("[安装器] " + line);
            }
        }
        int code = process.waitFor();
        if (code != 0) {
            throw new IOException(loader.display + " 安装器退出码 " + code);
        }
        String installed = "Forge".equals(loader.display)
                ? mcVersion + "-forge-" + loaderVersion
                : mcVersion + "-neoforge-" + loaderVersion;
        log.accept(loader.display + " 安装完成：" + installed);
        return installed;
    }
}

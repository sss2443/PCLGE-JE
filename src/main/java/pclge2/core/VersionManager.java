package pclge2.core;

import com.google.gson.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 版本管理：拉取版本清单、安装版本（client jar、libraries、natives、assets）。
 */
public final class VersionManager {

    /** 版本清单条目 */
    public record VersionEntry(String id, String type, String url, String releaseTime) {
    }

    /** 安装进度 */
    public interface Listener {
        void stage(String text, double progress);
    }

    private static final Gson GSON = new GsonBuilder().create();

    private VersionManager() {
    }

    /** 当前操作系统名（Mojang 规则使用的写法） */
    public static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    private static boolean is64Bit() {
        return System.getProperty("os.arch", "").contains("64");
    }

    /** 拉取远端版本清单 */
    public static List<VersionEntry> fetchRemoteVersions(Downloader.Source source, Consumer<String> log)
            throws IOException, InterruptedException {
        String url = source.metaBase + "mc/game/version_manifest_v2.json";
        log.accept("获取版本清单：" + url);
        String body = Downloader.getText(url);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        List<VersionEntry> out = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            out.add(new VersionEntry(
                    v.get("id").getAsString(),
                    v.get("type").getAsString(),
                    v.get("url").getAsString(),
                    v.has("releaseTime") ? v.get("releaseTime").getAsString() : ""));
        }
        return out;
    }

    /** 已安装的版本 id 列表 */
    public static List<String> installedVersions() {
        List<String> out = new ArrayList<>();
        Path dir = PathConfig.versionsDir();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String id = p.getFileName().toString();
                if (Files.isRegularFile(dir.resolve(id).resolve(id + ".json"))) {
                    out.add(id);
                }
            }
        } catch (IOException ignored) {
        }
        Collections.sort(out);
        return out;
    }

    /** 判断某个 library 规则是否允许当前系统 */
    private static boolean ruleAllows(JsonObject lib) {
        if (!lib.has("rules")) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement el : lib.getAsJsonArray("rules")) {
            JsonObject rule = el.getAsJsonObject();
            String action = rule.get("action").getAsString();
            boolean match = true;
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name")) {
                    match = os.get("name").getAsString().equals(osName());
                }
                if (match && os.has("arch")) {
                    String arch = os.get("arch").getAsString();
                    if (arch.contains("64")) {
                        match = is64Bit();
                    } else {
                        match = !is64Bit();
                    }
                }
            }
            if (match && rule.has("features")) {
                // 简化：不支持的 feature 一律视为不匹配
                JsonObject features = rule.getAsJsonObject("features");
                for (String key : features.keySet()) {
                    if (features.get(key).isJsonPrimitive() && !features.get(key).getAsBoolean()) {
                        match = false;
                    }
                }
            }
            if (match) {
                allowed = action.equals("allow");
            }
        }
        return allowed;
    }

    /**
     * 安装版本。阻塞方法，请在后台线程调用。
     */
    public static void install(String versionId, String versionUrl, Downloader.Source source, Listener listener,
                               Consumer<String> log) throws IOException, InterruptedException {
        Path versionDir = PathConfig.versionsDir().resolve(versionId);
        Files.createDirectories(versionDir);

        // 1. 版本 JSON
        listener.stage("下载版本信息", 0.02);
        String jsonUrl = source.mirror(versionUrl);
        String body = Downloader.getText(jsonUrl);
        Path jsonFile = versionDir.resolve(versionId + ".json");
        Files.writeString(jsonFile, body);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();

        // 2. client jar
        listener.stage("下载游戏本体", 0.08);
        JsonObject downloads = root.getAsJsonObject("downloads");
        if (downloads != null && downloads.has("client")) {
            String clientUrl = downloads.getAsJsonObject("client").get("url").getAsString();
            Path jar = versionDir.resolve(versionId + ".jar");
            Downloader.download(source.mirror(clientUrl), jar);
        }

        // 3. libraries
        Path nativesDir = versionDir.resolve("natives");
        Files.createDirectories(nativesDir);
        JsonArray libs = root.getAsJsonArray("libraries");
        if (libs != null) {
            int total = libs.size();
            int done = 0;
            for (JsonElement el : libs) {
                JsonObject lib = el.getAsJsonObject();
                done++;
                listener.stage("下载依赖库 " + done + "/" + total, 0.1 + 0.55 * done / Math.max(1, total));
                if (!ruleAllows(lib)) {
                    continue;
                }
                JsonObject libDownloads = lib.has("downloads") ? lib.getAsJsonObject("downloads") : null;
                if (libDownloads == null) {
                    continue;
                }
                // 普通 jar
                if (libDownloads.has("artifact")) {
                    JsonObject art = libDownloads.getAsJsonObject("artifact");
                    if (art.has("path") && art.has("url")) {
                        Path dest = PathConfig.librariesDir().resolve(art.get("path").getAsString());
                        Downloader.download(source.mirror(art.get("url").getAsString()), dest);
                        // 老版本 native：artifact 名里带 natives-windows 或在 classifiers 中
                        if (art.get("path").getAsString().contains("natives-" + osName())) {
                            extractNatives(dest, nativesDir);
                        }
                    }
                }
                // classifiers（老版本 natives）
                if (lib.has("natives")) {
                    JsonObject natives = lib.getAsJsonObject("natives");
                    String classifier = null;
                    if (natives.has(osName())) {
                        classifier = natives.get(osName()).getAsString();
                    }
                    if (classifier != null && libDownloads.has("classifiers")
                            && libDownloads.getAsJsonObject("classifiers").has(classifier)) {
                        JsonObject art = libDownloads.getAsJsonObject("classifiers").getAsJsonObject(classifier);
                        Path dest = PathConfig.librariesDir().resolve(art.get("path").getAsString());
                        Downloader.download(source.mirror(art.get("url").getAsString()), dest);
                        extractNatives(dest, nativesDir);
                    }
                }
            }
        }

        // 4. assets
        if (root.has("assetIndex")) {
            JsonObject ai = root.getAsJsonObject("assetIndex");
            String indexId = ai.get("id").getAsString();
            Path indexFile = PathConfig.assetsDir().resolve("indexes").resolve(indexId + ".json");
            Downloader.download(source.mirror(ai.get("url").getAsString()), indexFile);
            String indexBody = Files.readString(indexFile);
            JsonObject index = JsonParser.parseString(indexBody).getAsJsonObject();
            JsonObject objects = index.getAsJsonObject("objects");
            int total = objects.size();
            int done = 0;
            for (String key : objects.keySet()) {
                JsonObject obj = objects.getAsJsonObject(key);
                done++;
                if (done % 200 == 0 || done == total) {
                    listener.stage("下载资源文件 " + done + "/" + total, 0.7 + 0.28 * done / Math.max(1, total));
                }
                String hash = obj.get("hash").getAsString();
                String sub = hash.substring(0, 2);
                Path dest = PathConfig.assetsDir().resolve("objects").resolve(sub).resolve(hash);
                if (Files.exists(dest)) {
                    continue;
                }
                Downloader.download(source.assetBase + sub + "/" + hash, dest);
            }
            // 复制到 virtual/legacy（老资源包使用）
            Path virtualDir = PathConfig.assetsDir().resolve("virtual").resolve("legacy");
            if (index.has("virtual") || index.has("map_to_resources")) {
                for (String key : objects.keySet()) {
                    String hash = objects.getAsJsonObject(key).get("hash").getAsString();
                    Path src = PathConfig.assetsDir().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                    Path dst = virtualDir.resolve(key);
                    try {
                        Files.createDirectories(dst.getParent());
                        if (!Files.exists(dst)) {
                            Files.copy(src, dst);
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        listener.stage("安装完成", 1.0);
        log.accept("版本 " + versionId + " 安装完成");
    }

    /** 把 natives jar 中的动态库解压到目标目录 */
    private static void extractNatives(Path jar, Path nativesDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                String fileName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".dll")
                        || fileName.toLowerCase(Locale.ROOT).endsWith(".so")
                        || fileName.toLowerCase(Locale.ROOT).endsWith(".dylib")) {
                    Path out = nativesDir.resolve(fileName);
                    try (InputStream in = zis) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /** 下载进度辅助：把字节进度折算成 0~1 */
    public static BiConsumer<Long, Long> ratio(Consumer<Double> sink) {
        return (done, total) -> {
            if (total > 0) {
                sink.accept((double) done / total);
            }
        };
    }
}

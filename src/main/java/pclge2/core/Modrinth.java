package pclge2.core;

import com.google.gson.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Modrinth 资源搜索与下载（模组 / 整合包 / 资源包）。
 */
public final class Modrinth {

    private static final String API = "https://api.modrinth.com/v2";

    public record Hit(String title, String slug, String description, long downloads, String projectType) {
    }

    private Modrinth() {
    }

    /** 搜索：type 取 mod / modpack / resourcepack */
    public static List<Hit> search(String query, String type, String gameVersion, int limit)
            throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(API + "/search?limit=" + limit
                + "&query=" + URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8));
        StringBuilder facets = new StringBuilder("[[\"project_type:" + type + "\"]");
        if (gameVersion != null && !gameVersion.isBlank()) {
            facets.append(",[\"versions:").append(gameVersion).append("\"]");
        }
        facets.append("]");
        url.append("&facets=").append(URLEncoder.encode(facets.toString(), StandardCharsets.UTF_8));
        String body = Downloader.getText(url.toString());
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        List<Hit> out = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("hits")) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Hit(o.get("title").getAsString(), o.get("slug").getAsString(),
                    o.has("description") ? o.get("description").getAsString() : "",
                    o.has("downloads") ? o.get("downloads").getAsLong() : 0, type));
        }
        return out;
    }

    private static JsonArray latestVersion(String slug, String gameVersion, String loader)
            throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(API + "/project/" + slug + "/version");
        List<String> params = new ArrayList<>();
        if (gameVersion != null && !gameVersion.isBlank()) {
            params.add("game_versions=" + URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8));
        }
        if (loader != null && !loader.isBlank()) {
            params.add("loaders=" + URLEncoder.encode("[\"" + loader + "\"]", StandardCharsets.UTF_8));
        }
        if (!params.isEmpty()) {
            url.append("?").append(String.join("&", params));
        }
        String body = Downloader.getText(url.toString());
        JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
        if (arr.isEmpty()) {
            throw new IOException("该项目没有适配 " + gameVersion + " / " + loader + " 的版本");
        }
        return arr.get(0).getAsJsonObject().getAsJsonArray("files");
    }

    /**
     * 下载并安装到一个版本实例：模组/资源包落到对应目录，整合包解压到版本目录。
     * @return 落盘路径
     */
    public static Path install(String slug, String type, String gameVersion, String loader,
                               String targetVersionId, Consumer<String> log)
            throws IOException, InterruptedException {
        JsonArray files = latestVersion(slug, gameVersion, loader);
        JsonObject file = files.get(0).getAsJsonObject();
        String fileName = file.get("filename").getAsString();
        String downloadUrl = file.get("url").getAsString();
        log.accept("下载 " + fileName + " ← " + downloadUrl);

        Path dest;
        switch (type) {
            case "mod" -> dest = ModManager.modsDir(targetVersionId).resolve(fileName);
            case "resourcepack" -> dest = ModManager.resourcePacksDir(targetVersionId).resolve(fileName);
            default -> dest = PathConfig.versionsDir().resolve(targetVersionId).resolve(fileName);
        }
        Files.createDirectories(dest.getParent());
        Downloader.download(downloadUrl, dest);
        log.accept("已保存：" + dest);
        return dest;
    }

    /** 整合包：下载 zip 后解压到版本目录（mods/config/resourcepacks） */
    public static void installModpack(String slug, String gameVersion, String targetVersionId,
                                      Consumer<String> log) throws IOException, InterruptedException {
        Path zip = install(slug, "modpack", gameVersion, null, targetVersionId, log);
        Path versionDir = PathConfig.versionsDir().resolve(targetVersionId);
        log.accept("解压整合包到 " + versionDir);
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path out = versionDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log.accept("整合包解压完成");
    }
}

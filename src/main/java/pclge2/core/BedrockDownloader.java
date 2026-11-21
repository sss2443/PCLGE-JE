package pclge2.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 基岩版服务端（BDS）下载：查官方下载接口 → 下 zip → 解压到启动器目录。 */
public final class BedrockDownloader {

    private static final String LINKS_API =
            "https://net-secondary.web.minecraft-services.net/api/v1.0/download/links";

    /** 一条下载链接：类型（serverBedrockWindows / serverBedrockLinux / Preview 变体）+ 直链 */
    public record Link(String type, String url) {
        public String version() {
            String name = url.substring(url.lastIndexOf('/') + 1);
            return name.replace("bedrock-server-", "").replace(".zip", "");
        }

        public boolean preview() {
            return type.contains("Preview");
        }
    }

    private BedrockDownloader() {
    }

    /** 读取官方下载接口，返回全部链接 */
    public static List<Link> fetchLinks() throws IOException, InterruptedException {
        String body = Downloader.getText(LINKS_API);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray links = root.getAsJsonObject("result").getAsJsonArray("links");
        List<Link> out = new ArrayList<>();
        for (JsonElement e : links) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Link(o.get("downloadType").getAsString(), o.get("downloadUrl").getAsString()));
        }
        return out;
    }

    /** 按当前系统与正式/预览版挑链接 */
    public static Link pick(List<Link> links, boolean preview) {
        String want = VersionManager.osName().equals("windows") ? "Windows" : "Linux";
        return links.stream()
                .filter(l -> l.type().endsWith(want) && l.preview() == preview)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("官方接口里没有匹配的 BDS 链接"));
    }

    /** 基岩版根目录：启动器目录/bedrock */
    public static Path bedrockRoot() {
        return PathConfig.launcherDir().resolve("bedrock");
    }

    /** 下载并解压，返回解压后的目录（bedrock/<版本>） */
    public static Path downloadAndInstall(Link link, Consumer<String> log, Consumer<Double> progress)
            throws IOException, InterruptedException {
        String version = link.version();
        Path zip = bedrockRoot().resolve("bedrock-server-" + version + ".zip");
        Path dir = bedrockRoot().resolve(version);
        Files.createDirectories(dir);

        log.accept("下载 BDS " + version + " ← " + link.url());
        Downloader.download(link.url, zip, (done, total) -> {
            if (progress != null && total > 0) {
                progress.accept(done * 1.0 / total);
            }
        });
        log.accept("解压到 " + dir);
        unzip(zip, dir);
        PathConfig.put("bedrockDir", dir.toAbsolutePath().toString());
        log.accept("已关联服务端目录：" + dir);
        return dir;
    }

    private static void unzip(Path zip, Path dest) throws IOException {
        try (var zin = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IOException("压缩包路径越界：" + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}

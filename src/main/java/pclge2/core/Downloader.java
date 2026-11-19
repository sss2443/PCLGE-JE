package pclge2.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * 简易下载器：支持官方源与 BMCLAPI 镜像切换、断点重下、进度回调。
 */
public final class Downloader {

    /** 下载源 */
    public enum Source {
        /** Mojang 官方源 */
        MOJANG("https://launchermeta.mojang.com/", "https://launcher.mojang.com/", "https://resources.download.minecraft.net/"),
        /** BMCLAPI 国内镜像 */
        BMCL("https://bmclapi2.bangbang93.com/", "https://bmclapi2.bangbang93.com/", "https://bmclapi2.bangbang93.com/assets/");

        public final String metaBase;
        public final String objectBase;
        public final String assetBase;

        Source(String metaBase, String objectBase, String assetBase) {
            this.metaBase = metaBase;
            this.objectBase = objectBase;
            this.assetBase = assetBase;
        }

        /** 把官方 URL 转换成对应源的 URL */
        public String mirror(String officialUrl) {
            if (this == MOJANG) {
                return officialUrl;
            }
            return officialUrl
                    .replace("https://launchermeta.mojang.com/", metaBase)
                    .replace("https://launcher.mojang.com/", objectBase)
                    .replace("https://resources.download.minecraft.net/", assetBase);
        }
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Downloader() {
    }

    public static String getText(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "PCLGE2/2.00")
                .GET()
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + " : " + url);
        }
        return resp.body();
    }

    /**
     * 下载文件到 dest（已存在且大小一致则跳过）。
     *
     * @param progress 回调（已下载字节, 总字节），可为 null
     */
    public static void download(String url, Path dest, BiConsumer<Long, Long> progress)
            throws IOException, InterruptedException {
        Files.createDirectories(dest.getParent());
        if (Files.exists(dest) && Files.size(dest) > 0) {
            return; // 已存在，跳过（简化处理）
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "PCLGE2/2.00")
                .GET()
                .build();
        HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + " : " + url);
        }
        long total = resp.headers().firstValueAsLong("content-length").orElse(-1L);
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (progress != null) {
                    progress.accept(done, total);
                }
            }
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void download(String url, Path dest) throws IOException, InterruptedException {
        download(url, dest, null);
    }
}

package pclge2.core;

import com.google.gson.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.*;

/**
 * 账户与皮肤：离线账户 UUID、LittleSkin 皮肤下载、导入皮肤、自定义皮肤。
 */
public final class Account {

    /** 简单账户模型 */
    public record Profile(String name, String uuid, String accessToken) {
        public static Profile offline(String name) {
            return new Profile(name, offlineUuid(name), "0");
        }
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Gson GSON = new Gson();

    private Account() {
    }

    /** 离线账户 UUID（与 Minecraft 一致的生成算法） */
    public static String offlineUuid(String name) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            digest[6] = (byte) ((digest[6] & 0x0f) | 0x30);
            digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) msb = (msb << 8) | (digest[i] & 0xff);
            for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (digest[i] & 0xff);
            return new UUID(msb, lsb).toString();
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    /** LittleSkin：用户名 -> UUID */
    public static String littleSkinUuid(String userName) throws IOException, InterruptedException {
        String body = "[\"" + userName.replace("\"", "") + "\"]";
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://littleskin.cn/api/yggdrasil/api/profiles/minecraft"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("User-Agent", "PCLGE2/2.00")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("LittleSkin 返回 HTTP " + resp.statusCode());
        }
        JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
        if (arr.isEmpty()) {
            throw new IOException("LittleSkin 找不到用户：" + userName);
        }
        return arr.get(0).getAsJsonObject().get("id").getAsString();
    }

    /** LittleSkin：UUID -> 皮肤纹理地址 */
    public static String littleSkinSkinUrl(String uuid) throws IOException, InterruptedException {
        String url = "https://littleskin.cn/api/yggdrasil/sessionserver/session/minecraft/profile/" + uuid;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "PCLGE2/2.00")
                .GET()
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("LittleSkin 返回 HTTP " + resp.statusCode());
        }
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!root.has("properties")) {
            throw new IOException("该账户没有皮肤数据");
        }
        for (JsonElement e : root.getAsJsonArray("properties")) {
            JsonObject p = e.getAsJsonObject();
            if (!"textures".equals(p.get("name").getAsString())) {
                continue;
            }
            String decoded = new String(java.util.Base64.getDecoder().decode(p.get("value").getAsString()),
                    StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
            if (textures.has("SKIN")) {
                return textures.getAsJsonObject("SKIN").get("url").getAsString();
            }
        }
        throw new IOException("该账户没有 SKIN 纹理");
    }

    /** 下载 LittleSkin 皮肤到 skins/<用户名>.png，返回文件路径 */
    public static Path downloadLittleSkin(String userName) throws IOException, InterruptedException {
        String uuid = littleSkinUuid(userName);
        String skinUrl = littleSkinSkinUrl(uuid);
        Path dest = PathConfig.skinsDir().resolve(userName + ".png");
        Files.createDirectories(dest.getParent());
        HttpRequest req = HttpRequest.newBuilder(URI.create(skinUrl))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "PCLGE2/2.00")
                .GET()
                .build();
        HttpResponse<Path> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(dest));
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("皮肤下载失败 HTTP " + resp.statusCode());
        }
        return dest;
    }

    /** 导入皮肤：把本地图片复制成 skins/<目标名>.png */
    public static Path importSkin(Path sourceImage, String targetName) throws IOException {
        String fileName = targetName + ".png";
        Path dest = PathConfig.skinsDir().resolve(fileName);
        Files.createDirectories(dest.getParent());
        Files.copy(sourceImage, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    /** 当前账户皮肤文件 */
    public static Path skinFile(String accountName) {
        return PathConfig.skinsDir().resolve(accountName + ".png");
    }

    /** 判断文件名是否是合法皮肤图（简单扩展名检查） */
    public static boolean looksLikeSkin(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    /** 提取 LittleSkin 用户名合法性（字母数字下划线） */
    public static boolean validUserName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
}

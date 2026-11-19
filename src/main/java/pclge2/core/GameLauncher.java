package pclge2.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * 游戏启动：拼装 classpath、native 路径与启动参数，调用 Java 运行游戏。
 */
public final class GameLauncher {

    private static final Gson GSON = new GsonBuilder().create();

    /** 启动配置 */
    public record LaunchOptions(String versionId, String playerName, String uuid, String accessToken,
                                int maxMemoryMb, List<String> extraJvmArgs) {
    }

    private GameLauncher() {
    }

    /** 查找可用的 java 可执行文件（优先启动器自带 runtime，其次系统环境） */
    public static Path findJava() {
        Path runtime = PathConfig.launcherDir().resolve("runtime");
        if (Files.isDirectory(runtime)) {
            Path win = runtime.resolve("bin").resolve("java.exe");
            Path unix = runtime.resolve("bin").resolve("java");
            if (Files.isRegularFile(win)) return win;
            if (Files.isRegularFile(unix)) return unix;
            // 多版本子目录再找一层
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(runtime)) {
                for (Path p : ds) {
                    Path w = p.resolve("bin").resolve("java.exe");
                    Path u = p.resolve("bin").resolve("java");
                    if (Files.isRegularFile(w)) return w;
                    if (Files.isRegularFile(u)) return u;
                }
            } catch (IOException ignored) {
            }
        }
        String javaHome = System.getProperty("java.home");
        Path win = Paths.get(javaHome, "bin", "java.exe");
        Path unix = Paths.get(javaHome, "bin", "java");
        return Files.isRegularFile(win) ? win : unix;
    }

    /** 构建启动命令 */
    public static List<String> buildCommand(LaunchOptions opt, Consumer<String> log) throws IOException {
        Path versionDir = PathConfig.versionsDir().resolve(opt.versionId());
        Path jsonFile = versionDir.resolve(opt.versionId() + ".json");
        if (!Files.isRegularFile(jsonFile)) {
            throw new IOException("找不到版本 JSON：" + jsonFile);
        }
        JsonObject root = JsonParser.parseString(Files.readString(jsonFile)).getAsJsonObject();

        // classpath
        List<String> cp = new ArrayList<>();
        cp.add(versionDir.resolve(opt.versionId() + ".jar").toString());
        JsonArray libs = root.getAsJsonArray("libraries");
        if (libs != null) {
            for (JsonElement el : libs) {
                JsonObject lib = el.getAsJsonObject();
                if (!VersionManager.osName().equals(VersionManager.osName())) {
                    continue;
                }
                JsonObject downloads = lib.has("downloads") ? lib.getAsJsonObject("downloads") : null;
                if (downloads != null && downloads.has("artifact")
                        && downloads.getAsJsonObject("artifact").has("path")) {
                    String path = downloads.getAsJsonObject("artifact").get("path").getAsString();
                    Path p = PathConfig.librariesDir().resolve(path);
                    if (Files.isRegularFile(p)) {
                        cp.add(p.toString());
                    }
                }
            }
        }

        String classpath = String.join(java.io.File.pathSeparator, cp);
        Path natives = versionDir.resolve("natives");
        Path assets = PathConfig.assetsDir();
        String assetIndex = root.has("assetIndex") ? root.getAsJsonObject("assetIndex").get("id").getAsString()
                : root.has("assets") ? root.get("assets").getAsString() : "legacy";

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", opt.playerName());
        vars.put("version_name", opt.versionId());
        vars.put("game_directory", PathConfig.gameDir().toAbsolutePath().toString());
        vars.put("assets_root", assets.toAbsolutePath().toString());
        vars.put("assets_index_name", assetIndex);
        vars.put("auth_uuid", opt.uuid());
        vars.put("auth_access_token", opt.accessToken());
        vars.put("user_type", "legacy");
        vars.put("version_type", root.has("type") ? root.get("type").getAsString() : "release");
        vars.put("natives_directory", natives.toAbsolutePath().toString());
        vars.put("launcher_name", "PCLGE2");
        vars.put("launcher_version", "2.00");
        vars.put("classpath", classpath);
        vars.put("classpath_separator", java.io.File.pathSeparator);
        vars.put("library_directory", PathConfig.librariesDir().toAbsolutePath().toString());
        vars.put("auth_session", "-");

        List<String> cmd = new ArrayList<>();
        cmd.add(findJava().toString());
        cmd.add("-Xmx" + opt.maxMemoryMb() + "M");

        // JVM 参数
        if (root.has("arguments")) {
            JsonObject arguments = root.getAsJsonObject("arguments");
            if (arguments.has("jvm")) {
                for (JsonElement e : arguments.getAsJsonArray("jvm")) {
                    String value = argValue(e, vars, "jvm");
                    if (value != null) {
                        cmd.addAll(splitArg(value));
                    }
                }
            }
        } else if (root.has("minecraftArguments")) {
            // 老版本没有 jvm 段，补基础参数
            cmd.add("-Djava.library.path=" + natives.toAbsolutePath());
            cmd.add("-cp");
            cmd.add(classpath);
        } else {
            cmd.add("-Djava.library.path=" + natives.toAbsolutePath());
            cmd.add("-cp");
            cmd.add(classpath);
        }
        // 老版本 -cp 缺失时补上
        if (!cmd.contains("-cp") && !cmd.contains("-classpath")) {
            cmd.add("-cp");
            cmd.add(classpath);
        }
        if (opt.extraJvmArgs() != null) {
            cmd.addAll(opt.extraJvmArgs());
        }

        String mainClass = root.get("mainClass").getAsString();
        cmd.add(mainClass);

        // 游戏参数
        if (root.has("arguments")) {
            JsonObject arguments = root.getAsJsonObject("arguments");
            if (arguments.has("game")) {
                for (JsonElement e : arguments.getAsJsonArray("game")) {
                    String value = argValue(e, vars, "game");
                    if (value != null) {
                        cmd.addAll(splitArg(value));
                    }
                }
            }
        } else if (root.has("minecraftArguments")) {
            String mArgs = root.get("minecraftArguments").getAsString();
            for (String part : mArgs.split(" ")) {
                if (!part.isBlank()) {
                    cmd.add(replaceVars(part, vars));
                }
            }
        }

        log.accept("启动命令：" + String.join(" ", cmd));
        return cmd;
    }

    /** 处理参数项（字符串或带 rules 的对象） */
    private static String argValue(JsonElement e, Map<String, String> vars, String section) {
        if (e.isJsonPrimitive()) {
            return replaceVars(e.getAsString(), vars);
        }
        if (!e.isJsonObject()) {
            return null;
        }
        JsonObject obj = e.getAsJsonObject();
        if (obj.has("rules")) {
            boolean allowed = false;
            for (JsonElement rel : obj.getAsJsonArray("rules")) {
                JsonObject rule = rel.getAsJsonObject();
                String action = rule.get("action").getAsString();
                boolean match = true;
                if (rule.has("os")) {
                    JsonObject os = rule.getAsJsonObject("os");
                    if (os.has("name")) {
                        match = os.get("name").getAsString().equals(VersionManager.osName());
                    }
                }
                if (match) {
                    allowed = action.equals("allow");
                }
            }
            if (!allowed) {
                return null;
            }
        }
        if (obj.has("value")) {
            JsonElement v = obj.get("value");
            if (v.isJsonArray()) {
                // 多值参数，用空格拼起来后由 splitArg 再拆
                List<String> vals = new ArrayList<>();
                for (JsonElement item : v.getAsJsonArray()) {
                    vals.add(replaceVars(item.getAsString(), vars));
                }
                return String.join(" ", vals);
            }
            return replaceVars(v.getAsString(), vars);
        }
        return null;
    }

    private static List<String> splitArg(String value) {
        if (value.contains(" ") && value.contains("-D")) {
            // 形如 -Dfoo=a -Dbar=b 的合并参数
            List<String> out = new ArrayList<>();
            for (String part : value.split(" ")) {
                if (!part.isBlank()) {
                    out.add(part);
                }
            }
            return out;
        }
        return List.of(value);
    }

    private static String replaceVars(String input, Map<String, String> vars) {
        String out = input;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    /** 启动游戏（阻塞直到进程退出，请在后台线程调用） */
    public static int launch(LaunchOptions opt, Consumer<String> log) throws IOException, InterruptedException {
        List<String> cmd = buildCommand(opt, log);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(PathConfig.gameDir().toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (var in = process.getInputStream();
             var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in)) ) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.accept("[游戏] " + line);
            }
        }
        return process.waitFor();
    }
}

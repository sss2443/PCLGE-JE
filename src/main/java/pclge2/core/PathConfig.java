package pclge2.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Properties;

/**
 * PCLGE2 的目录与配置管理。
 * 负责：启动器数据目录、游戏目录（可自定义）、配置文件读写、存档迁移。
 */
public final class PathConfig {

    private static final String DEFAULT_GAME_DIR_NAME = ".minecraft";

    private static Path launcherDir;
    private static Path gameDir;

    private static final Properties props = new Properties();

    private PathConfig() {
    }

    /** 启动器数据目录：%APPDATA%/PCLGE2（Windows）或 ~/.pclge（其他系统） */
    public static synchronized Path launcherDir() {
        if (launcherDir == null) {
            String appData = System.getenv("APPDATA");
            Path base = (appData != null && !appData.isBlank())
                    ? Paths.get(appData)
                    : Paths.get(System.getProperty("user.home"));
            launcherDir = base.resolve("PCLGE2");
            try {
                Files.createDirectories(launcherDir);
            } catch (IOException ignored) {
            }
            load();
        }
        return launcherDir;
    }

    private static Path configFile() {
        return launcherDir.resolve("pclge-config.properties");
    }

    private static void load() {
        Path f = configFile();
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                props.load(in);
            } catch (IOException ignored) {
            }
        }
        String saved = props.getProperty("gameDir");
        if (saved != null && !saved.isBlank()) {
            gameDir = Paths.get(saved);
        } else {
            gameDir = defaultGameDir();
        }
        try {
            Files.createDirectories(gameDir);
        } catch (IOException ignored) {
        }
    }

    private static Path defaultGameDir() {
        return launcherDir.resolve(DEFAULT_GAME_DIR_NAME);
    }

    /** 当前游戏目录 */
    public static synchronized Path gameDir() {
        launcherDir();
        return gameDir;
    }

    /** 修改游戏目录并保存配置 */
    public static synchronized void setGameDir(Path dir) throws IOException {
        launcherDir();
        Files.createDirectories(dir);
        gameDir = dir;
        props.setProperty("gameDir", dir.toAbsolutePath().toString());
        save();
    }

    /** 恢复默认游戏目录 */
    public static synchronized void resetGameDir() throws IOException {
        setGameDir(defaultGameDir());
    }

    private static void save() throws IOException {
        try (OutputStream out = Files.newOutputStream(configFile())) {
            props.store(out, "PCLGE2 configuration");
        }
    }

    /** 通用配置项读写 */
    public static synchronized String get(String key, String def) {
        launcherDir();
        return props.getProperty(key, def);
    }

    public static synchronized void put(String key, String value) {
        launcherDir();
        props.setProperty(key, value);
        try {
            save();
        } catch (IOException ignored) {
        }
    }

    public static Path versionsDir() {
        return gameDir().resolve("versions");
    }

    public static Path librariesDir() {
        return gameDir().resolve("libraries");
    }

    public static Path assetsDir() {
        return gameDir().resolve("assets");
    }

    public static Path skinsDir() {
        return gameDir().resolve("skins");
    }

    public static Path savesDir() {
        return gameDir().resolve("saves");
    }

    /**
     * 移动存档：把 sourceGameDir 下的所有世界复制到 targetGameDir 的 saves 下。
     * 逐文件夹合并，同名已存在则跳过（不覆盖用户数据）。
     *
     * @return 复制的世界数量
     */
    public static int moveSaves(Path sourceGameDir, Path targetGameDir) throws IOException {
        Path src = sourceGameDir.resolve("saves");
        Path dst = targetGameDir.resolve("saves");
        if (!Files.isDirectory(src)) {
            return 0;
        }
        Files.createDirectories(dst);
        int copied = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(src)) {
            for (Path world : ds) {
                if (!Files.isDirectory(world)) {
                    continue;
                }
                Path target = dst.resolve(world.getFileName());
                if (Files.exists(target)) {
                    continue; // 同名世界已存在，跳过，避免覆盖
                }
                copyTree(world, target);
                copied++;
            }
        }
        return copied;
    }

    /** 递归复制目录（可在耗时任务中被调用） */
    public static void copyTree(Path source, Path target) throws IOException {
        Files.walk(source).forEach(p -> {
            try {
                Path rel = source.relativize(p);
                Path out = target.resolve(rel);
                if (Files.isDirectory(p)) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

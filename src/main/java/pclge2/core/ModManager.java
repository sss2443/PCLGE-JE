package pclge2.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 模组管理：列出、启用、禁用、删除。
 */
public final class ModManager {

    public record ModFile(String name, boolean enabled, long size) {
    }

    private ModManager() {
    }

    public static Path modsDir(String versionId) {
        return PathConfig.gameDir().resolve("versions").resolve(versionId).resolve("mods");
    }

    /** 列出某个版本的模组 */
    public static List<ModFile> list(String versionId) {
        Path dir = modsDir(versionId);
        List<ModFile> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    continue;
                }
                boolean disabled = name.endsWith(".disabled");
                if (name.endsWith(".jar") || name.endsWith(".jar.disabled") || disabled) {
                    try {
                        out.add(new ModFile(name, !disabled, Files.size(p)));
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
        out.sort(Comparator.comparing(ModFile::name));
        return out;
    }

    /** 启用/禁用模组（重命名加/去 .disabled 后缀） */
    public static void toggle(String versionId, String fileName) throws IOException {
        Path dir = modsDir(versionId);
        Path src = dir.resolve(fileName);
        if (!Files.exists(src)) {
            throw new IOException("模组不存在：" + fileName);
        }
        Path dst;
        if (fileName.endsWith(".disabled")) {
            dst = dir.resolve(fileName.substring(0, fileName.length() - ".disabled".length()));
        } else {
            dst = dir.resolve(fileName + ".disabled");
        }
        Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    /** 删除模组 */
    public static void delete(String versionId, String fileName) throws IOException {
        Files.deleteIfExists(modsDir(versionId).resolve(fileName));
    }

    /** 添加模组（复制 jar 到 mods 目录） */
    public static Path add(String versionId, Path jar) throws IOException {
        Path dir = modsDir(versionId);
        Files.createDirectories(dir);
        Path dest = dir.resolve(jar.getFileName().toString());
        Files.copy(jar, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    /** 某版本的资源包目录 */
    public static Path resourcePacksDir(String versionId) {
        return PathConfig.gameDir().resolve("versions").resolve(versionId).resolve("resourcepacks");
    }

    public static List<String> listResourcePacks(String versionId) {
        Path dir = resourcePacksDir(versionId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.zip")) {
            List<String> out = new ArrayList<>();
            ds.forEach(p -> out.add(p.getFileName().toString()));
            Collections.sort(out);
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 某版本的存档列表 */
    public static List<String> listSaves(String versionId) {
        Path dir = PathConfig.gameDir().resolve("versions").resolve(versionId).resolve("saves");
        if (!Files.isDirectory(dir)) {
            dir = PathConfig.savesDir();
        }
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            List<String> out = new ArrayList<>();
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    out.add(p.getFileName().toString());
                }
            }
            Collections.sort(out);
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }
}

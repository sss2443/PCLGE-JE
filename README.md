PCLGE2 —— Java 17 + JavaFX 17 的 Minecraft 启动器
==================================================
版本 2.00。界面结构对齐原版 PCL：顶部 6 个一级导航（启动 / 基岩版 / 下载 / 设置 / 工具 / 联机），
每页「左子页列表 + 右内容」双栏，底部常驻日志与状态栏。

功能
----
1. 启动：离线账户（自动生成 UUID）、内存与 JVM 参数、版本列表、切换游戏目录
2. 下载：游戏版本（官方源 / BMCLAPI 镜像）、Fabric / Quilt / Forge / NeoForge 加载器安装；
   整合包 / 模组 / 资源包走 Modrinth，双击即装到目标版本
3. 设置：游戏目录、存档迁移、Java 路径、下载源、版本管理（模组启停/添加/删除、资源包、存档）
4. 工具：LittleSkin 皮肤下载、导入/自定义皮肤、成就图样式预览、运行日志、帮助
5. 联机：UDP 25567 局域网广播聊天（协议 [CHAT]昵称||消息），EasyTier 网段内互见
6. 基岩版：BDS 服务端目录关联与启动

环境
----
编译：JDK 17 + JavaFX 17
运行：JDK/JRE 17 + JavaFX 17

构建
----
bash build.sh                      # 编译 + 打 jar + 同步 dist
cd dist && java -cp ../../launch4j/launch4j.jar net.sf.launch4j.Main l4j.xml   # 生成 PCLGE2.exe

直接运行
--------
java --module-path ../jfx17/linux --add-modules javafx.controls \
     -cp PCLGE2.jar:lib/gson-2.11.0.jar pclge2.Main

依赖
----
lib/gson-2.11.0.jar           JSON 解析
JavaFX 17 base / controls / graphics（win 与 linux 两套）

目录
----
src/main/java/pclge2/Main.java              入口
src/main/java/pclge2/ui/MainWindow.java     JavaFX 界面
src/main/java/pclge2/core/*.java            版本 / 下载 / 启动 / 账户 / 模组 / 联机
dist/                                       Windows 发行目录（PCLGE2.exe + 内置 JRE17）

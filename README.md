PCLGE2 —— Java 17 + JavaFX 17 的 Minecraft 启动器
==================================================
版本 2.00。界面结构对齐原版 PCL：顶部 6 个一级导航（启动 / 基岩版 / 下载 / 设置 / 工具 / 联机），
每页「左子页列表 + 右内容」双栏，底部常驻日志与状态栏。

视觉皮肤
--------
取自 PCLCE 官方源码，不是自创配色：
- 标题栏 #4890f5（ColorBrush4），高 48px
- 主色 #1370f3（ColorBrush3）、深色 #0b5bcb（ColorBrush2）
- 正文色 #343d4a（ColorBrush1），辅助灰 #737373
- 页面底色 #eaf2fe（ColorBrush8），左侧栏 rgba(255,255,255,0.95)（对应 F1FFFFFF）
- 导航胶囊：高 27px、圆角 13.5，选中白底蓝字
- 卡片：白底、圆角 8、轻投影（对应 PCL 内容卡片）
样式集中在 `src/main/resources/pclge2/pclge2.css`，改主题色只需改这一处。

功能
----
1. 启动：离线账户（自动生成 UUID）、内存与 JVM 参数、版本列表、切换游戏目录
2. 下载：游戏版本（官方源 / BMCLAPI 镜像）、Fabric / Quilt / Forge / NeoForge 加载器安装；
   整合包 / 模组 / 资源包走 Modrinth，双击即装到目标版本
3. 设置：游戏目录、存档迁移、Java 路径、下载源、版本管理（模组启停/添加/删除、资源包、存档）
4. 工具：LittleSkin 皮肤下载、导入/自定义皮肤、成就图样式预览、运行日志、帮助
5. 联机：UDP 25567 局域网广播聊天（协议 [CHAT]昵称||消息），EasyTier 网段内互见
6. 基岩版：一键下载 BDS（官方下载接口，正式版 / 预览版，自动按 Windows / Linux 选包），
   下载后自动解压到 `启动器目录/bedrock/<版本>` 并关联，也可手动选择已解压目录后启动

基岩版下载说明
--------------
接口：`https://net-secondary.web.minecraft-services.net/api/v1.0/download/links`
返回 serverBedrockWindows / serverBedrockLinux / 预览版变体直链（例：bedrock-server-1.26.66.1.zip）。
点击「基岩版 → 安装 → 检查更新」拉到最新版本号，再点「下载并安装 BDS」：下载 → 解压 → 写回配置 bedrockDir，
进度显示在底部状态栏，日志在底部日志区与「工具 → 日志」同步。

环境
----
编译：JDK 17 + JavaFX 17
运行：JDK/JRE 17 + JavaFX 17

构建
----
bash build.sh                      # 编译 + 打 jar + 同步 dist（含 CSS 资源）
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
src/main/java/pclge2/core/*.java            版本 / 下载 / 启动 / 账户 / 模组 / 联机 / 基岩版下载
src/main/resources/pclge2/pclge2.css        PCLCE 皮肤

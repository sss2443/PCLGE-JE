#!/bin/bash
# PCLGE2 Java 版构建脚本（Linux / macOS 交叉构建 Windows 版）
# 依赖：JDK 17 + JavaFX 17 SDK
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/home/user/android/jdk17}"
JFX="${JFX:-$DIR/../jfx17/linux}"

echo "== 1. 编译（--release 17）=="
rm -rf "$DIR/out"; mkdir -p "$DIR/out"
find "$DIR/src/main/java" -name "*.java" > "$DIR/sources.txt"
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 17 \
  --module-path "$JFX" --add-modules javafx.controls \
  -cp "$DIR/lib/gson-2.11.0.jar" \
  -d "$DIR/out" @"$DIR/sources.txt"

echo "== 1.5 复制资源 =="
if [ -d "$DIR/src/main/resources" ]; then
  cp -R "$DIR/src/main/resources/." "$DIR/out/"
fi

echo "== 2. 打包 jar =="
"$JAVA_HOME/bin/jar" --create --file "$DIR/PCLGE2.jar" \
  --main-class pclge2.Main -C "$DIR/out" .
echo "jar 已生成：$DIR/PCLGE2.jar"

echo "== 3. 同步到 dist =="
mkdir -p "$DIR/dist/lib"
cp "$DIR/PCLGE2.jar" "$DIR/dist/"
cp "$DIR/lib/gson-2.11.0.jar" "$DIR/dist/lib/"
cp "$DIR"/../jfx17/win/*.jar "$DIR/dist/lib/"
echo "完成：$DIR/dist（Windows 直接双击 PCLGE2.exe）"

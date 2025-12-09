#!/bin/bash
# Скрипт для локальной сборки DMG на macOS

# --- КОНФИГУРАЦИЯ ---
VERSION="1.0.0"
MAIN_CLASS="com.samfort.photorenamer.PhotoRenamer"
JAR_NAME="photo-renamer-$VERSION.jar"
MAVEN_OUTPUT_JAR="photo-renamer-$VERSION.jar"

if [ -z "$JAVA_HOME" ]; then
    echo "ERROR: JAVA_HOME не установлен."
    exit 1
fi
echo "Используем JAVA_HOME: $JAVA_HOME"
echo "Используем версию: $VERSION"

# 1. Очистка и сборка JAR
echo "--- 1. Сборка Maven и очистка ---"
rm -rf build-input custom-jre output Contents
JAVA_HOME="$JAVA_HOME" mvn clean package

# Проверка, что JAR создан
if [ ! -f "target/$MAVEN_OUTPUT_JAR" ]; then
    echo "FATAL ERROR: JAR file not found in target/ after Maven build: target/$MAVEN_OUTPUT_JAR"
    exit 1
fi

# 2. Подготовка input
echo "--- 2. Подготовка input и иконки ---"
mkdir -p build-input
# ИСПРАВЛЕНИЕ 1: Используем прямое имя JAR, чтобы избежать проблем с масками
cp "target/$MAVEN_OUTPUT_JAR" "build-input/$JAR_NAME"

# 4. Создание минимальной JRE
echo "--- 3. Создание JRE (jlink) ---"

# 1. Снова устанавливаем правильный путь, чтобы гарантировать, что jlink использует его.
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"

# 2. Используем JMODS_PATH, основанный на правильном JAVA_HOME
echo "--- 3. Создание JRE (jlink) ---"

# Мы полагаемся на JRT-файловую систему, которая автоматически найдет модули
# внутри установленного JDK (через файл modules), если не указан --module-path.
"$JAVA_HOME/bin/jlink" \
  --add-modules java.base,java.desktop,java.logging,java.naming,java.sql,java.management,java.instrument \
  --strip-debug --no-header-files --no-man-pages \
  --compress=2 \
  --output custom-jre

echo "JLink успешно создан custom-jre"

# 5. Сборка установщика (DMG)
echo "--- 4. Сборка установщика (jpackage) ---"
mkdir -p output
"$JAVA_HOME/bin/jpackage" \
  --input build-input \
  --name PhotoRenamer \
  --main-jar "$JAR_NAME" \
  --main-class "$MAIN_CLASS" \
  --runtime-image custom-jre \
  --app-version "$VERSION" \
  --type dmg \
  --dest output \
  --icon src/main/resources/icons/icon.icns \
  --resource-dir . \
  --mac-package-identifier com.samfort.photorenamer

echo "✅ DMG-пакет собран в папке output/"
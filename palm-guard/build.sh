#!/usr/bin/env bash
# 重新打包 palm-guard.apk。
#
# 用法：TOOLS=/放工具的資料夾 ./build.sh
#
# TOOLS 資料夾裡要有這四個檔：
#   aapt2        從 Maven 套件 org.apktool:apktool-lib:3.0.3 裡的 prebuilt/linux/aapt2 取出
#   android.jar  Android 14（API 34）的 android.jar
#   dx.jar       Maven 套件 com.jakewharton.android.repackaged:dalvik-dx:16.0.1
#   apksig.jar   Maven 套件 com.android.tools.build:apksig:2.3.0
#
# libs/ 裡的 Shizuku 小元件（Maven 套件 dev.rikka.shizuku 13.1.5）已經放在專案裡：
#   aidl、shared 兩個套件的 classes.jar，以及 provider 套件裡的 BinderContainer。
#   Shizuku 的 api 套件用了 lambda，dx 轉不了，所以改用自己寫的 ShizukuClient。
set -euo pipefail
cd "$(dirname "$0")"

TOOLS="${TOOLS:?請設定 TOOLS，指到放 aapt2、android.jar、dx.jar、apksig.jar 的資料夾}"
OUT=build

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/signer"

# 1. 編譯圖示和文字資源，產生 R.java
"$TOOLS/aapt2" compile --dir res -o "$OUT/res.zip"
"$TOOLS/aapt2" link -o "$OUT/unsigned.apk" \
    -I "$TOOLS/android.jar" \
    --manifest AndroidManifest.xml \
    --java "$OUT/gen" \
    --min-sdk-version 29 \
    --target-sdk-version 34 \
    "$OUT/res.zip"

# 2. 編譯 Java 程式碼（不用 lambda，因為 dx 不會轉換 lambda）
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 \
    -bootclasspath "$TOOLS/android.jar" \
    -cp "$(echo libs/*.jar | tr ' ' ':')" \
    -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java')

# 3. 轉成 Android 看得懂的 classes.dex，放進 APK
java -cp "$TOOLS/dx.jar" com.android.dx.command.Main \
    --dex --output="$OUT/classes.dex" "$OUT/classes" libs/*.jar
(cd "$OUT" && zip -q -j unsigned.apk classes.dex)

# 4. 簽名（這版 apksig 比較舊，要打開幾個 Java 內部套件給它用）
javac -cp "$TOOLS/apksig.jar" -d "$OUT/signer" tools/Sign.java
java \
    --add-exports java.base/sun.security.x509=ALL-UNNAMED \
    --add-exports java.base/sun.security.pkcs=ALL-UNNAMED \
    --add-exports java.base/sun.security.util=ALL-UNNAMED \
    -cp "$TOOLS/apksig.jar:$OUT/signer" Sign \
    debug.keystore android palmguard "$OUT/unsigned.apk" palm-guard.apk

echo "完成：$(pwd)/palm-guard.apk"

#!/data/data/com.termux/files/usr/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SDK_DIR=/data/data/com.termux/files/home/.cache/android-api/android-35
RESOURCE_SDK_DIR=/data/data/com.termux/files/home/.cache/android-api/android-9
TOOLS_DIR=/data/data/com.termux/files/usr/bin
OUT="$PROJECT_DIR/build"
INTERNAL_DOCUMENTS=/storage/emulated/0/Documents
TFLITE_AAR="$PROJECT_DIR/third_party/tflite/tensorflow-lite-2.16.1.aar"
TFLITE_API_AAR="$PROJECT_DIR/third_party/tflite/tensorflow-lite-api-2.16.1.aar"
TFLITE_WORK="$OUT/tflite"
NATIVE_LIBS="$OUT/native-libs"
RES_COMPILED="$OUT/res-compiled.zip"
RES_APK="$OUT/resources.apk"
GEN="$OUT/gen"
CLASSES="$OUT/classes"
DEX="$OUT/dex"

rm -rf "$GEN" "$CLASSES" "$DEX" "$RES_COMPILED" "$RES_APK" \
    "$OUT/classes.jar" "$OUT/tflite-classes.jar" "$OUT/unsigned.apk" \
    "$OUT/Fundo-Studio-aligned.apk" "$TFLITE_WORK" "$NATIVE_LIBS"
mkdir -p "$GEN" "$CLASSES" "$DEX" "$TFLITE_WORK/aar" "$TFLITE_WORK/classes"

unzip -q -o "$TFLITE_AAR" -d "$TFLITE_WORK/aar"
unzip -q -o "$TFLITE_API_AAR" -d "$TFLITE_WORK/api"
unzip -q -o "$TFLITE_WORK/aar/classes.jar" -d "$TFLITE_WORK/classes"
unzip -q -o "$TFLITE_WORK/api/classes.jar" -d "$TFLITE_WORK/classes"
mkdir -p "$NATIVE_LIBS/lib/arm64-v8a" "$NATIVE_LIBS/lib/armeabi-v7a"
cp "$TFLITE_WORK/aar/jni/arm64-v8a/libtensorflowlite_jni.so" "$NATIVE_LIBS/lib/arm64-v8a/"
cp "$TFLITE_WORK/aar/jni/armeabi-v7a/libtensorflowlite_jni.so" "$NATIVE_LIBS/lib/armeabi-v7a/"

"$TOOLS_DIR/aapt2" compile --dir "$PROJECT_DIR/res" -o "$RES_COMPILED"
"$TOOLS_DIR/aapt2" link \
    -I "$RESOURCE_SDK_DIR/android.jar" \
    --manifest "$PROJECT_DIR/AndroidManifest.xml" \
    --java "$GEN" \
    --min-sdk-version 26 \
    --target-sdk-version 35 \
    --version-code 3 \
    --version-name 1.2.0 \
    --auto-add-overlay \
    -o "$RES_APK" -R "$RES_COMPILED"

find "$PROJECT_DIR/src" "$GEN" -type f -name '*.java' -print > "$OUT/sources.list"
javac --release 8 -g:none -encoding UTF-8 \
    -classpath "$SDK_DIR/android.jar:$TFLITE_WORK/aar/classes.jar:$TFLITE_WORK/api/classes.jar" \
    -d "$CLASSES" \
    @"$OUT/sources.list"

mkdir -p "$OUT/tool-classes"
javac --release 8 -g:none -d "$OUT/tool-classes" \
    "$PROJECT_DIR/tools/StripMethodParameters.java"
java -cp "$OUT/tool-classes" StripMethodParameters "$CLASSES"
java -cp "$OUT/tool-classes" StripMethodParameters "$TFLITE_WORK/classes"

jar cf "$OUT/classes.jar" -C "$CLASSES" .
jar cf "$OUT/tflite-classes.jar" -C "$TFLITE_WORK/classes" .

"$TOOLS_DIR/d8" --release --min-api 26 --lib "$SDK_DIR/android.jar" \
    --output "$DEX" "$OUT/classes.jar" "$OUT/tflite-classes.jar"

cp "$RES_APK" "$OUT/unsigned.apk"
jar uf "$OUT/unsigned.apk" -C "$DEX" classes.dex
jar uf "$OUT/unsigned.apk" -C "$NATIVE_LIBS" .

"$TOOLS_DIR/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/Fundo-Studio-aligned.apk"

if [ ! -f "$OUT/fundo-studio-release.keystore" ]; then
    keytool -genkeypair -noprompt \
        -keystore "$OUT/fundo-studio-release.keystore" \
        -storepass fundostudio \
        -keypass fundostudio \
        -alias fundostudio \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Fundo Studio, OU=Local, O=Fundo Studio, L=Local, ST=Local, C=BR"
fi

"$TOOLS_DIR/apksigner" sign \
    --ks "$OUT/fundo-studio-release.keystore" \
    --ks-pass pass:fundostudio \
    --key-pass pass:fundostudio \
    --out "$OUT/Fundo-Studio.apk" \
    "$OUT/Fundo-Studio-aligned.apk"

"$TOOLS_DIR/apksigner" verify --verbose "$OUT/Fundo-Studio.apk"
mkdir -p "$INTERNAL_DOCUMENTS"
cp "$OUT/Fundo-Studio.apk" "$INTERNAL_DOCUMENTS/Fundo-Studio.apk"
printf 'APK criado: %s\n' "$OUT/Fundo-Studio.apk"
printf 'APK copiado para: %s\n' "$INTERNAL_DOCUMENTS/Fundo-Studio.apk"

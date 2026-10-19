#!/data/data/com.termux/files/usr/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SDK_DIR=/data/data/com.termux/files/home/.cache/android-api/android-35
RESOURCE_SDK_DIR=/data/data/com.termux/files/home/.cache/android-api/android-9
TOOLS_DIR=/data/data/com.termux/files/usr/bin
OUT="$PROJECT_DIR/build"
KEY_DIR=/data/data/com.termux/files/home/.android-keys
KEYSTORE="$KEY_DIR/nivel-digital.keystore"
APK_NAME="Nivel-Digital.apk"

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
sh "$PROJECT_DIR/tests/run.sh"
"$TOOLS_DIR/aapt2" compile --dir "$PROJECT_DIR/res" -o "$OUT/res-compiled.zip"
"$TOOLS_DIR/aapt2" link \
    -I "$RESOURCE_SDK_DIR/android.jar" \
    --manifest "$PROJECT_DIR/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 30 \
    --version-code 2 \
    --version-name 1.1.0 \
    --auto-add-overlay \
    -o "$OUT/resources.apk" -R "$OUT/res-compiled.zip"
find "$PROJECT_DIR/src" "$OUT/gen" -type f -name '*.java' -print > "$OUT/sources.list"
javac --release 8 -encoding UTF-8 -classpath "$SDK_DIR/android.jar" \
    -d "$OUT/classes" @"$OUT/sources.list"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
"$TOOLS_DIR/d8" --release --min-api 26 --lib "$SDK_DIR/android.jar" \
    --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/resources.apk" "$OUT/unsigned.apk"
jar uf "$OUT/unsigned.apk" -C "$OUT/dex" classes.dex
"$TOOLS_DIR/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -noprompt \
        -keystore "$KEYSTORE" \
        -storepass niveldigital -key-pass niveldigital -alias niveldigital \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Nivel Digital, OU=Local, O=Nivel Digital, L=Local, ST=SP, C=BR"
fi
"$TOOLS_DIR/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:niveldigital --key-pass pass:niveldigital \
    --out "$OUT/$APK_NAME" "$OUT/aligned.apk"
"$TOOLS_DIR/apksigner" verify --verbose "$OUT/$APK_NAME"
mkdir -p /storage/emulated/0/Documents/APK
cp "$OUT/$APK_NAME" /storage/emulated/0/Documents/APK/"$APK_NAME"
printf 'APK entregue em /storage/emulated/0/Documents/APK/%s\n' "$APK_NAME"

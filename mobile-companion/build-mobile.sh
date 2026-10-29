#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANDROID_MAIN="${REPO_ROOT}/launcher/app/src/main"

export JAVA_HOME="${JAVA_HOME:-${ HOME }/android-sdk/jdk}"
export PATH="${JAVA_HOME}/bin:${PATH}"
export ANDROID_HOME="${ANDROID_HOME:-${HOME}/Android/Sdk}"
BUILD_TOOLS="${ANDROID_HOME}/build-tools/34.0.0"
PLATFORM_JAR="${ANDROID_HOME}/platforms/android-34/android.jar"

KEYSTORE="${OMARCHY_KEYSTORE:-${HOME}/.config/omarchytv/omarchytv-release.jks}"
KEY_ALIAS="${OMARCHY_KEY_ALIAS:-omarchytv}"
PASS_FILE="${OMARCHY_KEY_PASS_FILE:-${HOME}/.config/omarchytv/password.txt}"

mkdir -p "${SCRIPT_DIR}/app/src/main/res/drawable" \
         "${SCRIPT_DIR}/app/src/main/res/font" \
         "${SCRIPT_DIR}/app/src/main/assets/webtv" \
         "${SCRIPT_DIR}/app/src/main/assets/licenses"

base64 -d << 'B64_IC_LAUNCHER' > "${SCRIPT_DIR}/app/src/main/res/drawable/ic_launcher.png"
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
AAAASUVORK5CYII=
B64_IC_LAUNCHER

base64 -d << 'B64_WEBTV_ICON' > "${SCRIPT_DIR}/app/src/main/assets/webtv/icon.png"
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
Lr0xX0IswTAAAAAASUVORK5CYII=
B64_WEBTV_ICON

if [ -d "${ANDROID_MAIN}" ]; then
  cp -f "${ANDROID_MAIN}/res/font/caskaydia_mono_regular.ttf" "${SCRIPT_DIR}/app/src/main/res/font/"
  cp -f "${ANDROID_MAIN}/res/font/caskaydia_mono_bold.ttf" "${SCRIPT_DIR}/app/src/main/res/font/"
  cp -f "${ANDROID_MAIN}/assets/omarchy_themes.json" "${SCRIPT_DIR}/app/src/main/assets/omarchy_themes.json"
  [ -f "${ANDROID_MAIN}/assets/licenses/FONT-LICENSE-OFL.txt" ] && cp -f "${ANDROID_MAIN}/assets/licenses/FONT-LICENSE-OFL.txt" "${SCRIPT_DIR}/app/src/main/assets/licenses/FONT-LICENSE-OFL.txt" || true
fi

BUILD_DIR="${SCRIPT_DIR}/build"
rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/compiled_res" "${BUILD_DIR}/gen" "${BUILD_DIR}/classes" "${BUILD_DIR}/dex" "${BUILD_DIR}/apk"

echo "[1/6] Compiling resources with aapt2..."
"${BUILD_TOOLS}/aapt2" compile --dir "${SCRIPT_DIR}/app/src/main/res" -o "${BUILD_DIR}/compiled_res/res.zip"

echo "[2/6] Linking resources & packaging assets with aapt2..."
"${BUILD_TOOLS}/aapt2" link \
  -I "${PLATFORM_JAR}" \
  --manifest "${SCRIPT_DIR}/app/src/main/AndroidManifest.xml" \
  -A "${SCRIPT_DIR}/app/src/main/assets" \
  --auto-add-overlay -R "${BUILD_DIR}/compiled_res/res.zip" \
  --java "${BUILD_DIR}/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code 21300 \
  --version-name "2.13.0-beta" \
  -o "${BUILD_DIR}/apk/unaligned.apk"

echo "[3/6] Compiling Java sources with JDK 17..."
find "${SCRIPT_DIR}/app/src/main/java" "${BUILD_DIR}/gen" -name "*.java" > "${BUILD_DIR}/sources.txt"
"${JAVA_HOME}/bin/javac" \
  --release 11 \
  -encoding UTF-8 \
  -cp "${PLATFORM_JAR}" \
  -d "${BUILD_DIR}/classes" \
  @"${BUILD_DIR}/sources.txt"

echo "[4/6] Converting bytecode to DEX with d8..."
find "${BUILD_DIR}/classes" -name "*.class" > "${BUILD_DIR}/classes.txt"
"${BUILD_TOOLS}/d8" \
  --lib "${PLATFORM_JAR}" \
  --min-api 26 \
  --output "${BUILD_DIR}/dex" \
  @"${BUILD_DIR}/classes.txt"

echo "[5/6] Adding classes.dex and aligning with zipalign..."
(cd "${BUILD_DIR}/dex" && "${JAVA_HOME}/bin/jar" uf "${BUILD_DIR}/apk/unaligned.apk" classes.dex)
"${BUILD_TOOLS}/zipalign" -f -p 4 "${BUILD_DIR}/apk/unaligned.apk" "${BUILD_DIR}/apk/aligned.apk"

OUT_BETA="${REPO_ROOT}/omarchytv-mobile-2.13.0-beta.apk"
OUT_STD="${REPO_ROOT}/omarchytv-mobile-2.13.0.apk"

echo "[6/6] Signing release APK with OmarchyTV release key..."
"${BUILD_TOOLS}/apksigner" sign \
  --ks "${KEYSTORE}" \
  --ks-key-alias "${KEY_ALIAS}" \
  --ks-pass "file:${PASS_FILE}" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "${OUT_BETA}" \
  "${BUILD_DIR}/apk/aligned.apk"

cp -f "${OUT_BETA}" "${OUT_STD}"

echo "Verifying APK signature and certificate..."
"${BUILD_TOOLS}/apksigner" verify --verbose --print-certs "${OUT_BETA}"
sha256sum "${OUT_BETA}" "${OUT_STD}"

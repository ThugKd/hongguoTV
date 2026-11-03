#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" ]]; then
  for native_jdk in "$HOME"/.cache/lvguotv/toolchain/jdk-17*/Contents/Home; do
    if [[ -x "$native_jdk/bin/java" ]]; then export JAVA_HOME="$native_jdk"; break; fi
  done
fi
if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/.cache/lvguotv/android-sdk" ]]; then
  export ANDROID_HOME="$HOME/.cache/lvguotv/android-sdk"
fi
"$repo_dir/kotlin-tv/gradlew" -p "$repo_dir/kotlin-tv" :core:test :app:lintRelease :app:assembleRelease --console=plain
mkdir -p "$repo_dir/outputs"
native_version="${LVGUOTV_VERSION_NAME:-$(sed -n 's/.*versionName = .*?: "\([^"]*\)".*/\1/p' "$repo_dir/kotlin-tv/app/build.gradle.kts")}"
[[ "$native_version" =~ ^[0-9][0-9A-Za-z.+-]*$ ]] || { echo "Invalid APK version" >&2; exit 1; }
native_apk="$repo_dir/outputs/lvguotv-kotlin-$native_version-android6.apk"
cp "$repo_dir/kotlin-tv/app/build/outputs/apk/release/app-release.apk" "$native_apk"
if command -v shasum >/dev/null; then shasum -a 256 "$native_apk"; else sha256sum "$native_apk"; fi

#!/usr/bin/env python3
"""Publish signed APK, update manifest, and checksums as a GitHub release."""
import hashlib, json, os, re, subprocess, sys
from pathlib import Path

TAG = os.environ["UPDATE_TAG"]
VERSION_NAME = os.environ["LVGUOTV_VERSION_NAME"]
VERSION_CODE = int(os.environ["LVGUOTV_VERSION_CODE"])
APK_SRC = "kotlin-tv/app/build/outputs/apk/release/app-release.apk"
APK_NAME = f"lvguotv-kotlin-{VERSION_NAME}-android6.apk"
OUTPUTS = "outputs"
os.makedirs(OUTPUTS, exist_ok=True)
apk_path = os.path.join(OUTPUTS, APK_NAME)
subprocess.run(["cp", APK_SRC, apk_path], check=True)
sha256 = hashlib.sha256(open(apk_path, "rb").read()).hexdigest()
size = os.path.getsize(apk_path)
sdk_root = os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"]
badging = subprocess.check_output([str(Path(sdk_root) / "build-tools/36.0.0/aapt"),
                                  "dump", "badging", apk_path], text=True)
sdk_match = re.search(r"^sdkVersion:'(\d+)'$", badging, re.MULTILINE)
if sdk_match is None or int(sdk_match[1]) < 23:
    raise ValueError("APK must declare minSdk >= 23")
min_sdk = int(sdk_match[1])

def changelog():
    prev = subprocess.run(["git", "describe", "--tags", "--abbrev=0", "--match", "kotlin-v*"],
                          capture_output=True, text=True)
    base = prev.stdout.strip() if prev.returncode == 0 else ""
    rng = [f"{base}..HEAD"] if base else ["-n", "20"]
    log = subprocess.run(["git", "log", "--no-merges", "--pretty=format:- %s"] + rng,
                         capture_output=True, text=True).stdout.strip()
    if not log:
        return "本次为重新构建，无新增提交。"
    return f"更新日志（自 {base} 起）：\n{log}" if base else f"更新日志：\n{log}"

notes = os.environ.get("RELEASE_NOTES", "").strip() or changelog()
manifest = {
    "schema": 1,
    "packageName": "com.thugkd.lvguotv",
    "versionCode": VERSION_CODE,
    "versionName": VERSION_NAME,
    "minSdk": min_sdk,
    "tag": TAG,
    "apk": {"name": APK_NAME, "size": size, "sha256": sha256},
    "notes": notes,
}
json_path = os.path.join(OUTPUTS, "update.json")
with open(json_path, "w") as f:
    json.dump(manifest, f, indent=2, ensure_ascii=False)

sha_path = os.path.join(OUTPUTS, "SHA256SUMS")
with open(sha_path, "w") as f:
    f.write(f"{sha256}  {APK_NAME}\n")

subprocess.run(["gh", "release", "create", TAG, "--title", f"绿果TV {VERSION_NAME}", "--notes",
    f"绿果TV {VERSION_NAME}\n\n{notes}", "--latest"], check=True)
for asset in [apk_path, json_path, sha_path]:
    subprocess.run(["gh", "release", "upload", TAG, asset, "--clobber"], check=True)
print(f"Published {TAG}: {APK_NAME} ({size} bytes, sha256={sha256[:16]}...)")

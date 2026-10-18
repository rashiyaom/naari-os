#!/usr/bin/env bash
set -euo pipefail

app_dir="$(cd "$(dirname "$0")" && pwd)"
repo_dir="$(cd "$app_dir/.." && pwd)"
source_apk="$app_dir/app/build/outputs/apk/debug/app-debug.apk"
artifact_dir="$app_dir/build"
website_dir="$repo_dir/sheshield-sentinel-guardian-main/public/downloads"
artifact_name="Naari-Companion-1.2-debug.apk"

if [[ ! -f "$source_apk" ]]; then
  echo "APK not found. Run ./gradlew assembleDebug first." >&2
  exit 1
fi

mkdir -p "$artifact_dir" "$website_dir"
cp "$source_apk" "$artifact_dir/$artifact_name"
cp "$source_apk" "$website_dir/$artifact_name"

(
  cd "$artifact_dir"
  shasum -a 256 "$artifact_name" > "$artifact_name.sha256"
)
cp "$artifact_dir/$artifact_name.sha256" "$website_dir/$artifact_name.sha256"

echo "$artifact_dir/$artifact_name"
echo "$website_dir/$artifact_name"

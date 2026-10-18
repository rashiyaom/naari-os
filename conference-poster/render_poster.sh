#!/usr/bin/env bash
set -euo pipefail

poster_dir="$(cd "$(dirname "$0")" && pwd)"
pdf="$poster_dir/naari-os-a3-poster.pdf"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

python3 "$poster_dir/generate_poster.py"

# Quick Look renders the vector PDF at the requested long-edge resolution.
qlmanage -t -s 4961 -o "$tmp_dir" "$pdf" >/dev/null 2>&1
rendered="$tmp_dir/$(basename "$pdf").png"
if [[ ! -f "$rendered" ]]; then
  echo "Quick Look did not produce the expected PNG" >&2
  exit 1
fi

# Resize to the exact ISO A3 300 dpi pixel dimensions and preserve 300 dpi metadata.
sips -z 4961 3508 "$rendered" --out "$poster_dir/naari-os-a3-poster.png" >/dev/null
sips -s dpiWidth 300 -s dpiHeight 300 "$poster_dir/naari-os-a3-poster.png" >/dev/null
sips -Z 1400 "$poster_dir/naari-os-a3-poster.png" --out "$poster_dir/naari-os-a3-poster-preview.png" >/dev/null
echo "Wrote print PNG and preview PNG"

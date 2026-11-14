#!/bin/bash
# Cuts the subject out of each licensed photograph, for the scrap that appears while a
# question is asked. The pixels are the photograph's own: this removes a background, it
# does not draw anything. Places whose subject cannot be lifted keep their drawing.
#
#   ./make.sh            rebuild every cut-out into app/src/main/assets/cutouts/
#
# Each line is: id  crop-width  crop-x  crop-height  (fractions of the original)
# The crops were found by hand. The system's segmentation looks for one clear subject,
# so a wide skyline has to be narrowed to the building first.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
PHOTOS="$HERE/../../native-app/app/src/main/assets/photos"
OUT="$HERE/../../native-app/app/src/main/assets/cutouts"
SDK=/Library/Developer/CommandLineTools/SDKs/MacOSX26.sdk
mkdir -p "$OUT"
[ -x "$HERE/lift" ] || swiftc -O -sdk "$SDK" "$HERE/lift.swift" -o "$HERE/lift"

scrap() {
  local id=$1 w=$2 x=$3 h=$4
  local src="$PHOTOS/$id-now.jpg" tmp="$HERE/.$id.jpg"
  ffmpeg -y -loglevel error -i "$src" -vf "crop=iw*$w:ih*$h:iw*$x:0" "$tmp"
  "$HERE/lift" "$tmp" "$HERE/.$id.png"
  # One height for all of them, so no place arrives bigger than another by accident.
  ffmpeg -y -loglevel error -i "$HERE/.$id.png" -vf "scale=-1:440:flags=lanczos" "$OUT/$id.png"
  rm -f "$tmp" "$HERE/.$id.png"
  printf "%-14s %s\n" "$id" "$(du -h "$OUT/$id.png" | cut -f1)"
}

scrap lotte       1.00 0.00 1.00
scrap sungnyemun  0.62 0.19 1.00
scrap gwanghwamun 0.44 0.28 0.85
scrap yeouido     0.30 0.42 1.00
# 63빌딩: the gold tower against a bright sky gives the segmentation nothing to hold on to.
# 청진옥: no freely licensed photograph exists. Both keep their explanatory drawing.

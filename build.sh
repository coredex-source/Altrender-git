#!/usr/bin/env bash
# Builds everything and copies the mod jars (Altrender and AltShaders, one per Minecraft version) to libs/.
# Each jar is complete on its own (LWJGL 3 and the LWJGL2 layer are nested in Altrender's).
#
#   ./build.sh [--sources] [extra Gradle arguments]
#
# --sources also copies the -sources jars. libs/ is emptied of jars first and gets only jars of the current mod_version
# (Gradle leaves older versions' jars in each build/libs/).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

sources=false
gradle_args=()
for arg in "$@"; do
  case "$arg" in
    --sources) sources=true ;;
    *) gradle_args+=("$arg") ;;
  esac
done

./gradlew build "${gradle_args[@]}"

version=$(sed -n 's/^mod_version=//p' gradle.properties | tr -d '[:space:]')
mkdir -p libs
rm -f libs/*.jar
copied=0
for jar in altrender/mc/versions/*/build/libs/*-"$version"+mc*.jar altshaders/mc/versions/*/build/libs/*-"$version"+mc*.jar; do
  [ -e "$jar" ] || continue
  if [[ "$jar" == *-sources.jar ]] && [ "$sources" = false ]; then
    continue
  fi
  cp "$jar" libs/
  echo "libs/$(basename "$jar")"
  copied=$((copied + 1))
done
if [ "$copied" -eq 0 ]; then
  echo "build.sh: no $version jars found under */mc/versions/*/build/libs" >&2
  exit 1
fi

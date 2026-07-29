#!/usr/bin/env bash
set -euo pipefail

target="${1:-1.21.11}"
mode="${2:-}"

case "${target}" in
  1.21.11)
    java_version="21"
    ;;
  26.2)
    java_version="25"
    ;;
  *)
    printf 'UI Lab supports the primary lanes 1.21.11 and 26.2 (received %s).\n' "${target}" >&2
    exit 2
    ;;
esac

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
default_java_home="${HOME}/Library/Java/JavaVirtualMachines/mapkluss-jbr-${java_version}.jdk/Contents/Home"
java_home="${MAPKLUSS_UI_LAB_JAVA_HOME:-${default_java_home}}"
java_executable="${java_home}/bin/java"
mac_app_java="${HOME}/Applications/MapKluss UI Lab.app/Contents/MacOS/java"
gradle_toolchain_args=()
if [[ "${target}" == "1.21.11" && -x "${mac_app_java}" ]]; then
  java_executable="${mac_app_java}"
  gradle_toolchain_args=(
    -Dorg.gradle.java.installations.auto-detect=false
    -Dorg.gradle.java.installations.auto-download=false
    "-Dorg.gradle.java.installations.paths=${HOME}/Applications/MapKluss UI Lab.app/Contents"
  )
fi
cd "${repo}"

if [[ ! -x "${java_home}/bin/java" ]]; then
  printf 'MapKluss JBR %s was not found at %s\n' "${java_version}" "${java_home}" >&2
  printf 'Set MAPKLUSS_UI_LAB_JAVA_HOME to a compatible JetBrains Runtime home.\n' >&2
  exit 3
fi

args=(
  runClient
  "-Pminecraft_version=${target}"
  -Pmapkluss_ui_lab=true
  "-Pmapkluss_ui_lab_java_home=${java_home}"
  "-Pmapkluss_ui_lab_java_executable=${java_executable}"
  "-Pmapkluss_ui_lab_capture_on_open=${MAPKLUSS_UI_LAB_CAPTURE_ON_OPEN:-false}"
)
if [[ "${mode}" == "--debug" ]]; then
  hotswap_source="${repo}/scripts/dev/MapKlussHotSwap.java"
  hotswap_classes="${repo}/build/ui-lab/hotswap"
  client_classes="${repo}/build/classes/java/main"
  mkdir -p "${hotswap_classes}"
  "${java_home}/bin/javac" --add-modules jdk.jdi -d "${hotswap_classes}" "${hotswap_source}"

  client_pid=""
  compiler_pid=""
  hotswap_pid=""
  cleanup() {
    [[ -n "${compiler_pid}" ]] && kill "${compiler_pid}" 2>/dev/null || true
    [[ -n "${hotswap_pid}" ]] && kill "${hotswap_pid}" 2>/dev/null || true
    [[ -n "${client_pid}" ]] && kill "${client_pid}" 2>/dev/null || true
  }
  trap cleanup EXIT INT TERM

  ./gradlew "${gradle_toolchain_args[@]}" "${args[@]}" --debug-jvm &
  client_pid="$!"
  "${java_home}/bin/java" --add-modules jdk.jdi \
    -cp "${hotswap_classes}" \
    MapKlussHotSwap 5005 "${client_classes}" &
  hotswap_pid="$!"
  ./gradlew "${gradle_toolchain_args[@]}" compileJava --continuous \
    "-Pminecraft_version=${target}" \
    -Pmapkluss_ui_lab=true &
  compiler_pid="$!"

  wait "${client_pid}"
  exit "$?"
elif [[ -n "${mode}" ]]; then
  printf 'Unknown option: %s (expected --debug or no option).\n' "${mode}" >&2
  exit 2
fi

exec ./gradlew "${gradle_toolchain_args[@]}" "${args[@]}"

#!/usr/bin/env bash
# ============================================================================
# adb-helper.sh
#
# Comandos ADB úteis para debugar o AulaLogger em device conectado.
#
# Uso: ./adb-helper.sh <comando>
# ============================================================================
set -euo pipefail

PKG="com.aulalogger.app"

usage() {
    cat <<EOF
Uso: $0 <comando>

Comandos:
  logs          - Tail dos logs do app (com filtro AulaLogger)
  logs-all      - Tail de TODOS os logs do app
  install <apk> - Instala APK no device conectado
  uninstall     - Desinstala app
  pull-data     - Copia /data/data/${PKG}/ para ./device-dump/
  pull-recordings - Copia apenas pasta de gravações
  storage       - Mostra uso de storage do app
  permissions   - Mostra permissões concedidas
  battery-stats - Estatísticas de bateria do app
  open-settings - Abre tela de Configurações > Apps > AulaLogger
  clear-data    - Apaga TODOS os dados do app (cuidado!)
  start         - Inicia o app
  stop          - Força parada do app
EOF
}

require_device() {
    if ! adb get-state >/dev/null 2>&1; then
        echo "❌ Nenhum device conectado. Cheque com: adb devices"
        exit 1
    fi
}

cmd="${1:-}"
shift || true

case "$cmd" in
    logs)
        require_device
        adb logcat -v time --pid=$(adb shell pidof "$PKG" 2>/dev/null) 2>/dev/null | grep -i AulaLogger || \
            adb logcat -v time | grep -i AulaLogger
        ;;
    logs-all)
        require_device
        adb logcat -v time --pid=$(adb shell pidof "$PKG")
        ;;
    install)
        require_device
        apk="${1:?Caminho do APK obrigatório}"
        adb install -r "$apk"
        ;;
    uninstall)
        require_device
        adb uninstall "$PKG"
        ;;
    pull-data)
        require_device
        mkdir -p ./device-dump
        adb shell "run-as $PKG tar c ." | tar x -C ./device-dump
        echo "✓ Conteúdo em ./device-dump/"
        ;;
    pull-recordings)
        require_device
        mkdir -p ./device-recordings
        adb shell "run-as $PKG tar c files/recordings" | tar x -C ./device-recordings
        ;;
    storage)
        require_device
        adb shell dumpsys diskstats | grep -A 5 "$PKG" || true
        adb shell run-as "$PKG" du -sh files/ databases/ shared_prefs/ 2>/dev/null
        ;;
    permissions)
        require_device
        adb shell dumpsys package "$PKG" | grep -A 30 "runtime permissions:"
        ;;
    battery-stats)
        require_device
        adb shell dumpsys batterystats "$PKG"
        ;;
    open-settings)
        require_device
        adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d "package:$PKG"
        ;;
    clear-data)
        require_device
        read -p "⚠️  Apagar TODOS os dados de $PKG? [y/N] " confirm
        if [[ "$confirm" == "y" ]]; then
            adb shell pm clear "$PKG"
            echo "✓ Dados apagados"
        fi
        ;;
    start)
        require_device
        adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
        ;;
    stop)
        require_device
        adb shell am force-stop "$PKG"
        ;;
    *)
        usage
        exit 1
        ;;
esac

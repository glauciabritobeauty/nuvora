#!/usr/bin/env bash
# Instala o APK no emulador, mede a abertura, tira prints e registra memória e erros.
# Uso: bash ci/telas.sh celular|tv
set -uo pipefail

NAME="$1"
PKG=com.nuvora.app
OUT=out
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$NAME-$1.png"; }

adb install -r apk/*.apk
adb logcat -c

# Abertura a frio, 5 vezes (emulador: serve para comparar versões, não como número real de aparelho).
for i in 1 2 3 4 5; do
  adb shell am force-stop "$PKG"
  sleep 2
  adb shell am start -W -n "$PKG/.MainActivity" | grep -E "TotalTime" | tee -a "$OUT/$NAME-abertura.txt"
done
sleep 6
shot 1-inicio

if [ "$NAME" = "tv" ]; then
  adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 2; shot 2-ok-no-assistir
  adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 2; shot 3-seta-baixo
  adb shell input keyevent KEYCODE_DPAD_RIGHT; adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 2; shot 4-seta-direita
  adb shell input keyevent KEYCODE_DPAD_DOWN; adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 2; shot 5-cartazes
  for i in 1 2 3; do adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1; done
  sleep 2; shot 6-estados
  for i in 1 2 3 4 5 6; do adb shell input keyevent KEYCODE_DPAD_LEFT; done
  sleep 2; shot 7-menu-lateral
else
  adb shell input tap 234 866; sleep 2; shot 2-toque-assistir
  for i in 3 4 5 6; do
    adb shell input swipe 540 1800 540 700 500
    sleep 2
    shot "$i-rolagem"
  done
fi

if adb shell pidof "$PKG" > /dev/null; then echo "app aberto (sem fechar sozinho)" > "$OUT/$NAME-status.txt"; else echo "APP FECHOU" > "$OUT/$NAME-status.txt"; fi
adb shell dumpsys meminfo "$PKG" > "$OUT/$NAME-memoria.txt"
adb logcat -d "*:E" | grep -iE "nuvora|AndroidRuntime" > "$OUT/$NAME-erros.txt" || true
echo "concluído"

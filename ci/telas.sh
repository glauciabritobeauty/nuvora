#!/usr/bin/env bash
# Instala o APK no emulador, mede a abertura, percorre as telas, tira prints e registra memória e erros.
# Uso: bash ci/telas.sh celular|tablet|tv
set -uo pipefail

NAME="$1"
PKG=com.nuvora.app
OUT=out
mkdir -p "$OUT"
STEPS="$OUT/$NAME-passos.txt"
shot() { adb exec-out screencap -p > "$OUT/$NAME-$1.png"; }
key() { adb shell input keyevent "$1"; sleep 1; }

# Toca no elemento pelo texto ou pela descrição (como um leitor de tela enxerga), não por coordenada fixa.
tap_text() {
  # A leitura da tela às vezes falha no emulador; tenta até 4 vezes e registra o motivo.
  rm -f ui.xml
  for tentativa in 1 2 3 4; do
    adb shell rm -f /sdcard/ui.xml
    msg=$(adb shell uiautomator dump --compressed /sdcard/ui.xml 2>&1)
    adb shell cat /sdcard/ui.xml > ui.xml 2> /dev/null
    if grep -q "<node" ui.xml 2> /dev/null; then break; fi
    echo "  leitura da tela falhou (tentativa $tentativa): $msg" >> "$STEPS"
    sleep 2
  done
  local pos
  pos=$(python3 - "$1" <<'EOF'
import re, sys
alvo = sys.argv[1]
xml = open('ui.xml', encoding='utf-8').read()
achados = []
for n in re.findall(r'<node [^>]*>', xml):
    if f'text="{alvo}"' in n or f'content-desc="{alvo}"' in n:
        a = list(map(int, re.findall(r'\d+', re.search(r'bounds="([^"]*)"', n).group(1))))
        # Ignora elementos rolados para fora da tela (altura zero ou colados no topo).
        if a[3] - a[1] > 10 and a[1] > 60:
            achados.append(a)
if achados:
    # Com dois iguais (lupa do topo e aba "Buscar"), usa o mais de baixo: o menu.
    a = max(achados, key=lambda b: b[1])
    print((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
EOF
)
  if [ -n "$pos" ]; then
    adb shell input tap $pos
    echo "ok: tocou em '$1'" >> "$STEPS"
  else
    echo "FALHOU: não achou '$1'" >> "$STEPS"
    cp ui.xml "$OUT/$NAME-leitura-falhou-$(echo "$1" | tr -dc 'a-zA-Z').xml" 2> /dev/null || true
  fi
  sleep 2
}

adb install -r apk/*.apk
adb logcat -c

# Abertura a frio, 5 vezes (emulador: serve para comparar versões, não como número real de aparelho).
for i in 1 2 3 4 5; do
  adb shell am force-stop "$PKG"
  sleep 2
  adb shell am start -W -n "$PKG/.MainActivity" | grep -E "TotalTime" | tee -a "$OUT/$NAME-abertura.txt"
done
sleep 6
shot 01-inicio

if [ "$NAME" = "tv" ]; then
  key KEYCODE_DPAD_CENTER; shot 02-ok-no-assistir
  key KEYCODE_DPAD_DOWN; shot 03-seta-baixo
  key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT; shot 04-seta-direita
  key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN; shot 05-cartazes
  for i in 1 2 3; do key KEYCODE_DPAD_DOWN; done
  shot 06-estados
  for i in 1 2 3 4 5 6; do adb shell input keyevent KEYCODE_DPAD_LEFT; done
  sleep 2; shot 07-menu-lateral
else
  W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
  H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
  swipe_up() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 500; sleep 2; }

  tap_text "Ver demonstração"; shot 02-home-demonstracao
  tap_text "Adicionar aos favoritos"; shot 03-favoritado
  swipe_up; tap_text "Séries"; shot 04-categoria-series
  swipe_up; shot 05-favoritos-e-historico
  tap_text "Buscar"; tap_text "Filmes, séries e canais"
  adb shell input text "Exemplo%s1"; sleep 2; key KEYCODE_BACK; shot 06-busca
  tap_text "Favoritos"; shot 07-aba-favoritos
  tap_text "Ajustes"; shot 08-aba-ajustes
  key KEYCODE_BACK; shot 09-voltar-para-inicio
  tap_text "Ver tudo"; shot 10-ver-tudo
  key KEYCODE_BACK
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
  sleep 4; shot 11-deitado
  adb shell settings put system user_rotation 0
  sleep 3; shot 12-em-pe-de-novo
fi

if adb shell pidof "$PKG" > /dev/null; then echo "app aberto (sem fechar sozinho)" > "$OUT/$NAME-status.txt"; else echo "APP FECHOU" > "$OUT/$NAME-status.txt"; fi
adb shell dumpsys meminfo "$PKG" > "$OUT/$NAME-memoria.txt"
adb logcat -d "*:E" | grep -iE "nuvora|AndroidRuntime" > "$OUT/$NAME-erros.txt" || true
echo "concluído"

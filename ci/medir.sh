#!/usr/bin/env bash
# Mede o NUVORA como a Glaucia usa: provedor grande (Provedor/Usuário/Senha) e internet de 8 Mega.
# Mede: abertura, login, download e organização do conteúdo, montagem da Home, fluidez ao rolar,
# início do vídeo (canal ao vivo e filme) e memória. Os números vêm do próprio Android e das anotações
# de teste do app (sem senhas nem endereços).
# Uso: bash ci/medir.sh celular|tv caminho-do.apk rotulo
set -uo pipefail

NAME="$1"
APK="$2"
ROTULO="$3"
PKG=com.nuvora.app
OUT=out
mkdir -p "$OUT"
STEPS="$OUT/$NAME-$ROTULO-medicao-passos.txt"
RES="$OUT/medidas-$NAME-$ROTULO.txt"
source "$(dirname "$0")/comum.sh"
: > "$RES"
: > "$STEPS"

med() { echo "$1=$2" >> "$RES"; echo "medida: $1 = $2" >> "$STEPS"; }
mediana() { sort -n | awk 'NF {a[++n]=$1} END {if (n) print a[int((n+1)/2)]}'; }
flog() { adb logcat -d -s NuvoraFontes:I | sed 's/.*NuvoraFontes: //'; }
# "Fully drawn com.nuvora.app/.MainActivity: +1s234ms" -> 1234 (primeira tela útil pronta, em ms).
fully_ms() {
  adb logcat -d | grep "Fully drawn $PKG" | tail -1 | python3 -c '
import re, sys
m = re.search(r"\+(?:(\d+)s)?(\d+)ms", sys.stdin.read())
print(int(m.group(1) or 0) * 1000 + int(m.group(2)) if m else "")'
}
pss() { adb shell dumpsys meminfo "$PKG" | grep "TOTAL PSS" | awk '{print $3}'; }
agora_ms() { echo $(( $(date +%s%N) / 1000000 )); }
# Espera um texto aparecer lendo a tela sem pausa (≈1 s por leitura) e devolve quanto tempo levou, em ms.
wait_fast() {
  local inicio=$2 limite=$(( $(agora_ms) + ${3:-60} * 1000 ))
  while [ "$(agora_ms)" -lt "$limite" ]; do
    read_ui
    if grep -qF "$1" ui.xml; then echo $(( $(agora_ms) - inicio )); return 0; fi
  done
  echo ""
}

# Abre o app do zero N vezes: tempo até o 1º desenho (TotalTime) e até a tela útil (Fully drawn).
cold_starts() {
  local prefixo=$1 vezes=$2 tot="" ful=""
  for i in $(seq 1 "$vezes"); do
    adb shell am force-stop "$PKG"; sleep 2; adb logcat -c
    local t
    t=$(adb shell am start -W -n "$PKG/.MainActivity" | grep TotalTime | awk '{print $2}' | tr -d '\r')
    sleep 8
    tot="$tot $t"; ful="$ful $(fully_ms)"
  done
  echo "  aberturas (1º desenho):$tot | (tela pronta):$ful" >> "$STEPS"
  med "${prefixo}_primeiro_desenho_ms" "$(echo $tot | tr ' ' '\n' | mediana)"
  med "${prefixo}_tela_pronta_ms" "$(echo $ful | tr ' ' '\n' | mediana)"
}

# Fluidez: quadros lentos enquanto rola a Home (o Android conta sozinho).
jank() {
  adb shell dumpsys gfxinfo "$PKG" reset > /dev/null
  if [ "$NAME" = "tv" ]; then
    for k in DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT \
             DPAD_DOWN DPAD_DOWN DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_UP DPAD_UP DPAD_UP DPAD_UP; do
      adb shell input keyevent "KEYCODE_$k"; sleep 0.4
    done
  else
    for i in 1 2 3 4 5 6; do adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 250; sleep 0.6; done
    for i in 1 2 3; do adb shell input swipe $((W * 3 / 4)) $((H / 2)) $((W / 4)) $((H / 2)) 250; sleep 0.6; done
    for i in 1 2 3 4 5 6; do adb shell input swipe $((W / 2)) $((H / 4)) $((W / 2)) $((H * 3 / 4)) 250; sleep 0.6; done
  fi
  sleep 2
  adb shell dumpsys gfxinfo "$PKG" > gfx.txt
  cp gfx.txt "$OUT/$NAME-$ROTULO-gfxinfo.txt"
  med rolagem_quadros "$(grep -m1 'Total frames rendered' gfx.txt | awk '{print $4}')"
  med rolagem_quadros_lentos_pct "$(grep -m1 'Janky frames' gfx.txt | grep -oE '\([0-9.]+%\)' | tr -d '()%')"
  med rolagem_quadro_p90_ms "$(grep -m1 '90th percentile' gfx.txt | grep -oE '[0-9]+ms' | tr -d 'ms')"
  med rolagem_quadro_p99_ms "$(grep -m1 '99th percentile' gfx.txt | grep -oE '[0-9]+ms' | tr -d 'ms')"
}

# Fecha o player (o 1º Voltar pode só esconder os controles).
close_player() {
  for i in 1 2 3; do key KEYCODE_BACK; sleep 2; if plog | grep -q "fechado"; then return; fi; done
}

# Abre o vídeo 3 vezes e guarda o tempo até o 1º quadro (mediana).
first_frames() {
  local prefixo=$1 abrir=$2 vals=""
  for i in 1 2 3; do
    adb logcat -c
    $abrir
    sleep 15
    local v
    v=$(plog | grep -o 'medida primeiro quadro [0-9]*' | head -1 | awk '{print $4}')
    vals="$vals $v"
    [ "$i" = 1 ] && check_playing "$prefixo" && med "${prefixo}_memoria_tocando_kb" "$(pss)"
    close_player; sleep 2
  done
  echo "  1º quadro ($prefixo):$vals" >> "$STEPS"
  med "${prefixo}_primeiro_quadro_ms" "$(echo $vals | tr ' ' '\n' | mediana)"
}

adb uninstall "$PKG" > /dev/null 2>&1
adb install -r "$APK" > /dev/null && echo "ok: instalou $APK" >> "$STEPS"
med apk_mb "$(python3 -c "import os,sys; print(round(os.path.getsize(sys.argv[1]) / 1e6, 1))" "$APK")"

# 1) Primeira abertura (boas-vindas).
cold_starts primeira_abertura 3

# 2) Entrar com Provedor/Usuário/Senha no provedor grande (8 Mega).
adb shell am start -n "$PKG/.MainActivity" > /dev/null; sleep 5
adb logcat -c
if [ "$NAME" = "tv" ]; then
  login_form "http://10.0.2.2:8800" "grande" "1234" nao-entrar
  INICIO=$(date +%s); INICIO_MS=$(agora_ms); key $OK
else
  W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
  H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
  login_form "http://10.0.2.2:8800" "grande" "1234" nao-entrar
  INICIO=$(date +%s); INICIO_MS=$(agora_ms); tap_text "ENTRAR"
fi
# Tempo de tocar em ENTRAR até a tela de escolha aparecer (lendo a tela sem pausa).
med entrar_ate_tela_de_escolha_ms "$(wait_fast "TV ao vivo" "$INICIO_MS" 60)"; shot "$ROTULO-medicao-escolha"
med login_ms "$(flog | grep -o 'medida login [0-9]*' | tail -1 | awk '{print $3}')"
med entrada_ms "$(flog | grep -o 'medida entrada [0-9]* categorias [0-9]*' | tail -1 | awk '{print $5}')"
med categorias "$(flog | grep -o 'medida entrada [0-9]*' | tail -1 | awk '{print $3}')"
# 2b) Abrir TV ao vivo logo depois de entrar (a 1ª categoria já vem junto com o login).
if [ "$NAME" = "tv" ]; then focus "escolha"; focus_until "TV ao vivo" $U; key $OK; else tap_text "TV ao vivo"; fi
med tv_ao_vivo_ate_canais_ms "$(wait_fast "Canal 1" "$(agora_ms)" 60)"; shot "$ROTULO-medicao-tv-ao-vivo"
# Assistir um canal e voltar para a lista ENQUANTO o resto ainda carrega (o que a Glaucia sentiu demorar).
if [ "$NAME" = "tv" ]; then focus "lista"; focus_until "Canal 1" $U; key $OK; else tap_text "Canal 1" sem-rolar; fi
sleep 10
for i in 1 2 3; do key KEYCODE_BACK; if plog | grep -q "fechado"; then break; fi; done
med voltar_do_canal_ate_lista_ms "$(wait_fast "Canal 2" "$(agora_ms)" 30)"
shot "$ROTULO-medicao-voltou-para-lista"
med segundo_plano_ainda_carregando "$(if flog | grep -q 'lista .* completa'; then echo nao; else echo sim; fi)"
# O resto chega em segundo plano: quanto tempo até a lista inteira estar no aparelho (sem travar a tela).
for i in $(seq 1 180); do if flog | grep -q "lista .* completa"; then break; fi; sleep 5; done
med segundo_plano_ate_completa_s "$(( $(date +%s) - INICIO ))"

flog | grep -v relatorio | sed 's/^/  anotação: /' | tail -30 >> "$STEPS"
key KEYCODE_BACK; sleep 2

# 3) Abrir o app já com o conteúdo (o dia a dia).
cold_starts abertura_com_conteudo 5
med memoria_home_kb "$(pss)"
if [ "$NAME" != "tv" ]; then
  W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
  H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
fi
jank
shot "$ROTULO-medicao-depois-de-rolar"

# 4) Início do vídeo: canal ao vivo (HLS) pela lista de TV ao vivo.
adb shell am force-stop "$PKG"; sleep 2; adb shell am start -W -n "$PKG/.MainActivity" > /dev/null; sleep 6
if [ "$NAME" = "tv" ]; then focus "escolha"; focus_until "TV ao vivo" $U; key $OK; else tap_text "TV ao vivo"; fi
wait_text "Canal 1" 60
if [ "$NAME" = "tv" ]; then
  open_live() { focus "lista"; focus_until "Canal 1" $U; key $OK; }
else
  open_live() { tap_text "Canal 1" sem-rolar; }
fi
first_frames canal open_live
key KEYCODE_BACK; sleep 2

# 5) Filme (MP4) pela busca, no celular.
if [ "$NAME" != "tv" ]; then
  tap_text "Buscar" sem-rolar
  tap_text "Campo de busca"; adb shell input text "Filme%s1"; sleep 2; key KEYCODE_BACK; sleep 2
  open_movie() { tap_card "Filme 1"; }
  first_frames filme open_movie
fi

if adb shell pidof "$PKG" > /dev/null; then echo "app aberto (sem fechar sozinho)" >> "$STEPS"; else echo "FALHOU: APP FECHOU" >> "$STEPS"; fi
adb logcat -d "*:E" | grep -iE "nuvora|AndroidRuntime|ANR in com.nuvora" > "$OUT/$NAME-$ROTULO-medicao-erros.txt" || true
echo "medição concluída ($ROTULO)" >> "$STEPS"

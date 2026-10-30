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

# Lê a tela de novo para ui.xml (com as mesmas repetições do tap_text).
read_ui() {
  for tentativa in 1 2 3 4; do
    adb shell rm -f /sdcard/ui.xml
    adb shell uiautomator dump --compressed /sdcard/ui.xml > /dev/null 2>&1
    adb shell cat /sdcard/ui.xml > ui.xml 2> /dev/null
    if grep -q "<node" ui.xml 2> /dev/null; then return; fi
    sleep 2
  done
}

# Confere se um texto está (sim) ou não está (nao) na tela e registra o resultado.
check_text() {
  read_ui
  if grep -qF "text=\"$1\"" ui.xml || grep -qF "content-desc=\"$1\"" ui.xml; then achou=sim; else achou=nao; fi
  if [ "$achou" = "$2" ]; then echo "ok: '$1' na tela = $2" >> "$STEPS"; else echo "FALHOU: '$1' na tela = $achou (esperado $2)" >> "$STEPS"; fi
}

# Segura o dedo no cartão com essa descrição (o cartão, não o texto embaixo dele).
long_press_card() {
  read_ui
  local pos
  pos=$(python3 - "$1" <<'EOF'
import re, sys
for n in re.findall(r'<node [^>]*>', open('ui.xml', encoding='utf-8').read()):
    if f'content-desc="{sys.argv[1]}"' in n:
        a = list(map(int, re.findall(r'\d+', re.search(r'bounds="([^"]*)"', n).group(1))))
        if a[3] - a[1] > 10:
            print((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
            break
EOF
)
  if [ -n "$pos" ]; then
    adb shell input swipe $pos $pos 1200
    echo "ok: segurou '$1'" >> "$STEPS"
  else
    echo "FALHOU: não achou o cartão '$1'" >> "$STEPS"
  fi
  sleep 2
}

# Esconde avisos de "não está respondendo" de apps do próprio emulador (ex.: Pixel Launcher),
# que às vezes cobrem a tela e atrapalham o teste. Travamentos do NUVORA continuam no registro de erros.
adb shell settings put global hide_error_dialogs 1

# Memória total do app (PSS, em KB), depois de pedir ao app para limpar o lixo da memória (coleta de lixo).
total_pss() {
  local pid
  pid=$(adb shell pidof "$PKG" | tr -d '
')
  [ -n "$pid" ] && adb shell run-as "$PKG" kill -10 "$pid" 2> /dev/null
  sleep 4
  adb shell dumpsys meminfo "$PKG" | grep "TOTAL PSS" | awk '{print $3}'
}

# Toca no cartão com essa descrição (o cartão, não o texto embaixo dele).
tap_card() {
  read_ui
  local pos
  pos=$(python3 - "$1" <<'EOF3'
import re, sys
for n in re.findall(r'<node [^>]*>', open('ui.xml', encoding='utf-8').read()):
    if f'content-desc="{sys.argv[1]}"' in n:
        a = list(map(int, re.findall(r'\d+', re.search(r'bounds="([^"]*)"', n).group(1))))
        if a[3] - a[1] > 10:
            print((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
            break
EOF3
)
  if [ -n "$pos" ]; then adb shell input tap $pos; echo "ok: tocou no cartão '$1'" >> "$STEPS"; else echo "FALHOU: não achou o cartão '$1'" >> "$STEPS"; fi
  sleep 2
}

# Lê o relógio do player ("Tempo atual 1:23") e devolve em segundos; vazio se os controles não estiverem na tela.
read_time() {
  read_ui
  python3 - <<'EOF2'
import re
m = re.search(r'content-desc="Tempo atual ([0-9:]+)"', open('ui.xml', encoding='utf-8').read())
if m:
    t = 0
    for part in m.group(1).split(':'):
        t = t * 60 + int(part)
    print(t)
EOF2
}

# Registra se o vídeo andou entre duas leituras do relógio.
check_advanced() {
  if [ -n "$1" ] && [ -n "$2" ] && [ "$2" -gt "$1" ]; then
    echo "ok: vídeo andou ($3): ${1}s -> ${2}s" >> "$STEPS"
  else
    echo "FALHOU: vídeo não andou ($3): '${1}' -> '${2}'" >> "$STEPS"
  fi
}

# Anotações do player (só existem na versão de teste).
plog() { adb logcat -d -s NuvoraPlayer:I | sed 's/.*NuvoraPlayer: //'; }
check_log() {
  if plog | grep -qF "$1"; then echo "ok: player anotou '$1'" >> "$STEPS"; else echo "FALHOU: player não anotou '$1'" >> "$STEPS"; fi
}
last_pos() { plog | grep -o 'posicao=[0-9]*' | tail -1 | cut -d= -f2; }
check_playing() { local a b; a=$(last_pos); sleep 7; b=$(last_pos); check_advanced "$a" "$b" "$1"; }

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
  # Registra em qual elemento está o foco do controle (com tudo o que está escrito dentro dele).
  focus() {
    adb shell uiautomator dump --compressed /sdcard/ui.xml > /dev/null 2>&1
    adb shell cat /sdcard/ui.xml > ui.xml
    local f
    f=$(python3 - <<'EOF'
import xml.etree.ElementTree as ET
try:
    root = ET.parse('ui.xml').getroot()
except Exception:
    print('(leitura da tela falhou)'); raise SystemExit
for n in root.iter('node'):
    if n.get('focused') == 'true':
        nomes = [m.get('text') or m.get('content-desc') for m in n.iter('node')]
        print(' / '.join(x for x in nomes if x) or '(sem nome)')
        break
else:
    print('(nada em foco)')
EOF
)
    echo "$1 -> foco em: $f" >> "$STEPS"
    LAST_FOCUS="$f"
  }
  U=KEYCODE_DPAD_UP; D=KEYCODE_DPAD_DOWN; L=KEYCODE_DPAD_LEFT; R=KEYCODE_DPAD_RIGHT; OK=KEYCODE_DPAD_CENTER; BACK=KEYCODE_BACK

  focus "1. abertura sem fonte"
  key $D; focus "2. seta baixo"
  key $OK; sleep 2; focus "3. OK em Ver demonstração"; shot 02-demonstracao
  key $R; key $OK; focus "4. direita + OK (Minha lista)"; shot 03-minha-lista
  key $D; key $R; key $R; focus "5. baixo + direita x2 (Continuar assistindo)"; shot 04-continuar
  key $D; for i in 1 2 3 4; do key $L; done; key $R; key $R; key $OK; focus "6. categorias: OK em Séries"; shot 05-categoria-series
  key $D; focus "7. baixo (recentes filtrados)"; shot 06-recentes
  key $U; focus "8. cima: deve voltar em Séries"
  key $D; key $D; focus "9. baixo x2 (Favoritos)"; shot 07-favoritos
  key $BACK; focus "10. Voltar: vai para o menu"; shot 08-voltar-menu
  key $D; key $OK; focus "11. menu: OK em Favoritos"; shot 09-aba-favoritos
  key $BACK; focus "12. Voltar no menu: volta ao Início"; shot 10-voltou-inicio
  key $U; key $U; key $OK; key $R; focus "13. menu Buscar + direita (campo)"
  key $OK; adb shell input text "Exemplo%s1"; sleep 2; key $BACK; focus "14. busca digitada"; shot 11-busca

  # Etapa 5: histórico salvo, remover um item e limpar tudo, só com o controle.
  key $BACK; key $BACK; focus "15. Voltar até o menu"
  key $D; key $D; key $D; key $OK; focus "16. menu Histórico"; shot 12-historico
  key $R; key $D; focus "17. cartão do histórico em foco"; ALVO="$LAST_FOCUS"
  adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2; focus "18. segurou OK: janela abre no Cancelar"; shot 13-opcoes
  key $U; focus "19. cima: Remover do histórico"; key $OK; sleep 2
  check_text "$ALVO" nao
  key $U; key $U; focus "20. botão Limpar histórico"; key $OK; sleep 1; focus "21. confirmação abre no Cancelar"
  key $U; key $OK; sleep 2
  check_text "Nada assistido ainda" sim; shot 14-historico-limpo

  # Etapa 6: player pelo controle. Primeiro, ir com certeza ao Início pelo menu.
  key $BACK; for i in 1 2 3 4 5; do key $U; done; key $D; focus "22. menu: Início"; key $OK; sleep 3
  key $R; sleep 1; focus "23. Home: foco no Assistir"
  MEM_ANTES=$(total_pss)
  adb logcat -c
  key $OK; sleep 15; shot 15-player-tocando
  check_log "aberto id="; check_playing "TV"
  key $R; sleep 2; check_log "avancou 10s"; shot 16-player-controles
  # Voltar até o player fechar (a 1ª pode só esconder os controles).
  for i in 1 2 3; do key $BACK; sleep 2; if plog | grep -q "fechado"; then break; fi; done
  check_log "fechado (player liberado)"
  plog | grep -E "voltar" | sed 's/^/  anotação: /' >> "$STEPS"
  sleep 2; focus "24. voltou à Home"; shot 17-voltou-home
  MEM_1=$(total_pss)
  # Troca de áudio no vídeo DASH com vários idiomas (Exemplo 2), só com o controle.
  for i in 1 2 3 4 5 6; do
    if echo "$LAST_FOCUS" | grep -q "^Exemplo 1"; then break; fi
    key $D; focus "  descendo até a fileira de recentes"
  done
  key $R; focus "25. cartão Exemplo 2"
  adb logcat -c
  key $OK; sleep 15; check_log "aberto id=demo-2"; check_playing "DASH na TV"
  check_log "legenda inicial=Port"
  key $U; sleep 1; key $OK; sleep 1; check_log "pausou"; focus "26. controles, vídeo pausado"
  key $D; key $D; focus "27. fileira de baixo"
  for i in 1 2 3; do
    if echo "$LAST_FOCUS" | grep -q "Áudio"; then break; fi
    key $L; focus "  procurando o botão Áudio"
  done
  key $OK; sleep 2; shot 18-janela-audio
  OPCOES=$(plog | grep "opcoes audio:" | tail -1 | sed 's/opcoes audio: //')
  echo "  opções de áudio anotadas: $OPCOES" >> "$STEPS"
  # A janela abre no "Fechar" (último). Sobe até o topo e desce até a posição do Espanhol.
  POS=$(echo "$OPCOES" | tr '|' '
' | grep -n "Espanhol" | head -1 | cut -d: -f1)
  for i in 1 2 3 4 5 6 7 8 9 10; do key $U; done
  if [ -n "$POS" ]; then for i in $(seq 2 "$POS"); do key $D; done; fi
  key $OK; sleep 3; check_log "audio=Espanhol"; shot 19-audio-trocado
  adb logcat -c
  for i in 1 2 3; do key $BACK; sleep 2; if plog | grep -q "fechado"; then break; fi; done
  check_log "fechado (player liberado)"; focus "29. voltou à Home"
  for i in 1 2 3 4 5; do key $U; done; focus "30. topo da Home"
  for i in 1 2 3; do key $OK; sleep 8; adb logcat -c; for j in 1 2 3; do key $BACK; sleep 2; if plog | grep -q "fechado"; then break; fi; done; sleep 2; done
  MEM_4=$(total_pss)
  echo "memória (KB): Home antes do player $MEM_ANTES; após 1ª saída $MEM_1; após 5ª saída $MEM_4" >> "$STEPS"
else
  W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
  H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
  swipe_up() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 500; sleep 2; }

  # Toca numa aba do menu de baixo (1 a 5): pelo nome; se a leitura da tela não der a posição, pela posição.
  tap_tab() {
    local before
    before=$(grep -c FALHOU "$STEPS" 2> /dev/null || true)
    tap_text "$1"
    if [ "$(grep -c FALHOU "$STEPS" 2> /dev/null || true)" != "$before" ]; then
      adb shell input tap $((W * (2 * $2 - 1) / 10)) $((H - H * 10 / 100))
      echo "  -> tocou em '$1' pela posição" >> "$STEPS"
      sleep 2
    fi
  }

  adb shell uiautomator dump --compressed /sdcard/ui.xml > /dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/$NAME-leitura-inicio.xml"
  tap_text "Ver demonstração"; shot 02-home-demonstracao
  tap_text "Adicionar aos favoritos"; shot 03-favoritado
  swipe_up; tap_text "Séries"; shot 04-categoria-series
  swipe_up; shot 05-favoritos-e-historico
  tap_tab "Buscar" 2; tap_text "Filmes, séries e canais"
  adb shell input text "Exemplo%s1"; sleep 2; key KEYCODE_BACK; shot 06-busca
  tap_tab "Favoritos" 3; shot 07-aba-favoritos
  tap_tab "Ajustes" 5; shot 08-aba-ajustes
  key KEYCODE_BACK; shot 09-voltar-para-inicio
  tap_text "Ver tudo"; shot 10-ver-tudo
  key KEYCODE_BACK
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
  sleep 4; shot 11-deitado
  adb shell settings put system user_rotation 0
  sleep 3; shot 12-em-pe-de-novo

  # Etapa 5: histórico salvo, remover um item, limpar tudo e favoritos depois de fechar o app.
  tap_tab "Histórico" 4; shot 13-historico
  check_text "Exemplo 5" sim
  long_press_card "Exemplo 5"; shot 14-opcoes
  tap_text "Remover do histórico"
  check_text "Exemplo 5" nao
  tap_text "Limpar histórico"; shot 15-confirmar-limpar
  tap_text "Limpar"
  check_text "Nada assistido ainda" sim; shot 16-historico-limpo
  adb shell am force-stop "$PKG"; sleep 2
  adb shell am start -W -n "$PKG/.MainActivity" > /dev/null; sleep 5
  tap_text "Ver demonstração"
  tap_tab "Favoritos" 3
  check_text "Destaque de exemplo 1" sim; shot 17-favorito-depois-de-fechar

  # Etapa 6: player no celular/tablet (o que acontece no vídeo é conferido pelas anotações do player).
  tap_tab "Início" 1
  MEM_ANTES=$(total_pss)
  adb logcat -c
  tap_text "Assistir"; sleep 15; shot 18-player-tocando
  check_log "aberto id=demo-destaque-1"; check_playing "HLS"
  check_log "legenda inicial=nenhuma (Automático)"
  C=$(( (W < H ? W : H) / 2 )); adb shell input tap $C $C; sleep 1; shot 19-player-controles
  # Queda de internet no meio do vídeo e volta.
  adb shell cmd connectivity airplane-mode enable; sleep 75; shot 20-sem-internet
  check_log "falha=Network"
  adb shell cmd connectivity airplane-mode disable; sleep 30; shot 21-internet-voltou
  check_log "retomou"; check_playing "depois que a internet voltou"
  key KEYCODE_BACK; sleep 4; check_log "fechado (player liberado)"; shot 22-voltou-home
  check_text "Assistir" sim
  MEM_1=$(total_pss)
  # Ajustes > Legendas "Sempre ligadas": o mesmo vídeo agora começa com legenda.
  tap_tab "Ajustes" 5; tap_text "Mudar legendas"; shot 25-escolher-legendas; tap_text "Sempre ligadas"
  check_text "Legendas: Sempre ligadas" sim
  tap_tab "Início" 1; adb logcat -c
  tap_text "Assistir"; sleep 12
  plog | grep "legenda inicial" | sed 's/^/  anotação: /' >> "$STEPS"
  if plog | grep "legenda inicial" | grep "Sempre ligadas" | grep -qv "=nenhuma"; then echo "ok: com 'Sempre ligadas' o vídeo começou com legenda" >> "$STEPS"; else echo "FALHOU: com 'Sempre ligadas' o vídeo não começou com legenda" >> "$STEPS"; fi
  shot 26-legenda-sempre; key KEYCODE_BACK; sleep 3
  # Exemplo 3 = MP4 de 10 s, aberto pela busca: chega ao fim.
  tap_tab "Buscar" 2; tap_text "Filmes, séries e canais"; adb shell input text "Exemplo%s3"; sleep 1; key KEYCODE_BACK
  adb logcat -c
  tap_card "Exemplo 3"; sleep 25; check_log "fim"; shot 23-mp4-terminou
  key KEYCODE_BACK; sleep 4; check_log "fechado (player liberado)"
  tap_tab "Histórico" 4; check_text "Destaque de exemplo 1" sim; shot 24-historico-apos-assistir
  tap_tab "Início" 1
  for i in 1 2 3; do tap_text "Assistir"; sleep 8; key KEYCODE_BACK; sleep 3; done
  MEM_4=$(total_pss)
  echo "memória (KB): Home antes do player $MEM_ANTES; após 1ª saída $MEM_1; após 5ª saída $MEM_4" >> "$STEPS"
fi

if adb shell pidof "$PKG" > /dev/null; then echo "app aberto (sem fechar sozinho)" > "$OUT/$NAME-status.txt"; else echo "APP FECHOU" > "$OUT/$NAME-status.txt"; fi
adb shell dumpsys meminfo "$PKG" > "$OUT/$NAME-memoria.txt"
adb logcat -d "*:E" | grep -iE "nuvora|AndroidRuntime|ANR in com.nuvora" > "$OUT/$NAME-erros.txt" || true
echo "concluído"

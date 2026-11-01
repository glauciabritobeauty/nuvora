# Funções comuns dos testes no emulador (usadas por telas.sh e medir.sh).
# Precisam de NAME, PKG, OUT e STEPS definidos antes.
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
  if [ -z "$pos" ] && [ "${2:-}" != "sem-rolar" ] && [ "${NAME}" != "tv" ] && [ "${TRIED_SCROLL:-0}" -lt 3 ]; then
    # Pode estar mais para baixo na tela: rola um pouco e tenta de novo (até 3 vezes).
    TRIED_SCROLL=$(( ${TRIED_SCROLL:-0} + 1 ))
    adb shell input swipe 500 1500 500 900 400; sleep 1
    tap_text "$1"; local r=$?; TRIED_SCROLL=0; return $r
  fi
  TRIED_SCROLL=0
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

# Apaga o texto do campo em foco.
clear_field() { adb shell input keyevent KEYCODE_MOVE_END; for i in $(seq 1 40); do adb shell input keyevent KEYCODE_DEL; done; }

# Espera um texto aparecer na tela (até N segundos).
wait_text() {
  local limite=$2 t=0
  while [ $t -lt "$limite" ]; do
    read_ui
    if grep -qF "text=\"$1\"" ui.xml || grep -qF "content-desc=\"$1\"" ui.xml; then echo "ok: '$1' apareceu em ~${t}s" >> "$STEPS"; return 0; fi
    sleep 5; t=$((t + 5))
  done
  echo "FALHOU: '$1' não apareceu em ${limite}s" >> "$STEPS"
}

# --- TV: foco do controle remoto ---
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

  focus_until() {  # aperta a tecla $2 até o foco conter o texto $1 (até 10 vezes)
    for i in 1 2 3 4 5 6 7 8 9 10; do
      if echo "$LAST_FOCUS" | grep -qF "$1"; then return 0; fi
      key "$2"; focus "  procurando '$1'"
    done
  }

# --- Nova entrada (usuário e senha direto, servidor opcional, ENTRAR) ---
# login_form servidor usuario senha [nao-entrar]: preenche e (se não pedir o contrário) aperta ENTRAR.
login_form() {
  local servidor=$1 usuario=$2 senha=$3
  read_ui
  if [ "$NAME" = "tv" ]; then
    focus "entrada"
    if ! grep -qF 'Entre com seu usuário e senha' ui.xml; then focus_until "Usuário e senha" $D; key $OK; sleep 2; focus "voltou ao login"; fi
    focus_until "USUÁRIO" $U; key $OK; sleep 1; adb shell input text "$usuario"; key $BACK
    focus_until "SENHA" $D; key $OK; sleep 1; adb shell input text "$senha"; key $BACK
    focus_until "ervidor" $D; key $OK; sleep 2; focus "campo do servidor"
    focus_until "SERVIDOR (DNS)" $D; key $OK; sleep 1; clear_field; adb shell input text "$servidor"; key $BACK
    focus_until "ENTRAR" $D
    [ "${4:-}" = "nao-entrar" ] || key $OK
  else
    if ! grep -qF 'Entre com seu usuário e senha' ui.xml; then tap_text "Usuário e senha"; fi
    tap_text "Digite seu usuário"; adb shell input text "$usuario"; key KEYCODE_BACK
    tap_text "Digite sua senha"; adb shell input text "$senha"; key KEYCODE_BACK
    read_ui
    if grep -qF '+ Usar outro servidor (DNS)' ui.xml; then tap_text "+ Usar outro servidor (DNS)"; else tap_text "Servidor: ${servidor#*://} · Trocar"; fi
    read_ui
    if grep -qF 'text="http://exemplo.com:8080"' ui.xml; then tap_text "http://exemplo.com:8080"; else tap_text "$servidor"; clear_field; fi
    adb shell input text "$servidor"; key KEYCODE_BACK
    [ "${4:-}" = "nao-entrar" ] || tap_text "ENTRAR"
  fi
}

# link_form endereço: entra pela opção discreta "Link da lista" e aperta ENTRAR.
# O "&" e o "?" do link precisam de uma barra antes para chegarem inteiros pelo adb.
adb_text() { adb shell input text "$(printf '%s' "$1" | sed 's/[&?]/\\&/g')"; }

link_form() {
  if [ "$NAME" = "tv" ]; then
    focus "entrada"; focus_until "Link da lista" $D; key $OK; sleep 2; focus "campo do link"
    focus_until "LINK DA LISTA" $U; key $OK; sleep 1; adb_text "$1"; key $BACK
    focus_until "ENTRAR" $D; key $OK
  else
    tap_text "Link da lista"
    tap_text "http://..."; adb_text "$1"; key KEYCODE_BACK
    tap_text "ENTRAR"
  fi
}

#!/usr/bin/env bash
# Etapa "login com provedor": testa o login contra um servidor que imita o provedor real da Glaucia
# (mesmas respostas: 400/422 "Bad Request" no player_api e 404 no get.php quando os dados não batem).
# Cenários: dados errados, provedor que só entrega a lista pelo link, provedor no padrão completo.
# Em cada um: mensagem certa, tipo do problema, relatório de conexão sem senha, Home e canal tocando.
# Uso: bash ci/login.sh celular|tv caminho-do.apk
set -uo pipefail

NAME="$1"
APK="$2"
PKG=com.nuvora.app
OUT=out
mkdir -p "$OUT"
STEPS="$OUT/$NAME-login-passos.txt"
source "$(dirname "$0")/comum.sh"
: > "$STEPS"
SERVIDOR="http://10.0.2.2:8900"
SENHA="Ab12cd"

flog() { adb logcat -d -s NuvoraFontes:I | sed 's/.*NuvoraFontes: //'; }
falha() { echo "FALHOU: $1" >> "$STEPS"; }

# Confere se algum texto na tela CONTÉM $1 (rola a tela até 6 vezes procurando).
check_contains() {
  for i in 1 2 3 4 5 6; do
    read_ui
    if grep -qF "$1" ui.xml; then echo "ok: tela contém '$1'" >> "$STEPS"; return 0; fi
    if [ "$NAME" = "tv" ]; then key $D; else adb shell input swipe 500 1500 500 700 300; sleep 1; fi
  done
  falha "tela não contém '$1'"
}

# Confere que a senha e o usuário de teste não aparecem nem na tela nem nas anotações do app.
check_secret() {
  read_ui
  if grep -qF "$SENHA" ui.xml || grep -qF "$1" ui.xml; then falha "senha ou usuário apareceu na tela ($2)"; else echo "ok: senha e usuário não aparecem na tela ($2)" >> "$STEPS"; fi
  if adb logcat -d -s NuvoraFontes:I NuvoraPlayer:I | grep -qE "$SENHA|$1"; then falha "senha ou usuário apareceu nas anotações ($2)"; else echo "ok: senha e usuário não aparecem nas anotações ($2)" >> "$STEPS"; fi
}

# Guarda o relatório de conexão que o app anotou (já sem senha) para conferir.
save_report() {
  adb logcat -d -s NuvoraFontes:I | sed -n '/relatorio de conexao/,$p' | sed 's/^.*NuvoraFontes: //' > "$OUT/$NAME-relatorio-$1.txt"
  echo "  relatório salvo em $NAME-relatorio-$1.txt ($(wc -l < "$OUT/$NAME-relatorio-$1.txt") linhas)" >> "$STEPS"
}

# Começa do zero (app como recém-instalado) e preenche Provedor/Usuário/Senha.
login() {
  local usuario=$1
  adb shell pm clear "$PKG" > /dev/null
  adb logcat -c
  adb shell am start -n "$PKG/.MainActivity" > /dev/null; sleep 6
  if [ "$NAME" = "tv" ]; then
    focus "boas-vindas"; focus_until "Provedor, usuário e senha" $U; focus_until "Provedor, usuário e senha" $D; key $OK; sleep 2
    focus_until "exemplo.com" $D; key $OK; sleep 1; adb shell input text "$SERVIDOR"; key $BACK
    focus_until "Usuário" $D; key $OK; sleep 1; adb shell input text "$usuario"; key $BACK
    focus_until "Senha" $D; key $OK; sleep 1; adb shell input text "$SENHA"; key $BACK
    focus_until "Tenho direito" $D; key $OK
    focus_until "Testar e salvar" $D; key $OK
  else
    tap_text "Provedor, usuário e senha"
    tap_text "http://exemplo.com:8080"; adb shell input text "$SERVIDOR"; key KEYCODE_BACK
    tap_text "Usuário"; adb shell input text "$usuario"; key KEYCODE_BACK
    tap_text "Senha"; adb shell input text "$SENHA"; key KEYCODE_BACK
    tap_text "Tenho direito de acesso a este conteúdo."
    tap_text "Testar e salvar"
  fi
}

open_report() {
  if [ "$NAME" = "tv" ]; then focus "relatório"; focus_until "Ver relatório de conexão" $U; focus_until "Ver relatório de conexão" $D; key $OK; else tap_text "Ver relatório de conexão"; fi
  sleep 2
}

# Abre o canal do destaque da Home e confere que o vídeo anda.
play_hero() {
  adb logcat -c
  if [ "$NAME" = "tv" ]; then
    focus "home"; if ! echo "$LAST_FOCUS" | grep -q "Assistir"; then key $R; focus "home"; fi; key $OK
  else
    tap_text "Assistir" sem-rolar
  fi
  sleep 15; shot "login-$1-tocando"
  check_log "aberto id="; check_playing "$2"
  for i in 1 2 3; do key KEYCODE_BACK; sleep 2; if plog | grep -q "fechado"; then break; fi; done
}

adb install -r "$APK" > /dev/null && echo "ok: instalou $APK" >> "$STEPS"

# 1) Dados errados: o servidor responde 400 no player_api e 404 no get.php (igual ao real).
echo "== cenário 1: usuário e senha que o provedor não reconhece" >> "$STEPS"
login "errado"
wait_text "O provedor não reconheceu esse usuário e senha. Confira letra por letra (maiúsculas e minúsculas contam) ou peça ao seu fornecedor para conferir se o acesso está ativo." 60
shot login-1-nao-reconhecido
check_text "Tipo do problema: Autenticação: o provedor não aceitou o usuário e a senha" sim
open_report; shot login-1-relatorio
check_contains "1. Endereço do provedor: ok"
check_contains "Plano B: lista completa pelo link (get.php): falhou"
check_contains "Tipo do problema: Autenticação"
check_secret "errado" "cenário 1"
save_report 1-nao-reconhecido

# 2) Provedor que recusa o jeito padrão mas entrega a lista pelo link (plano B).
echo "== cenário 2: só a lista completa pelo link funciona" >> "$STEPS"
login "sololink"
wait_text "Pronto! 25 itens encontrados." 90; shot login-2-pronto
open_report; shot login-2-relatorio
check_contains "Recursos do provedor: ok"
check_secret "sololink" "cenário 2"
save_report 2-so-link
if [ "$NAME" = "tv" ]; then focus "início"; focus_until "Ir para o Início" $U; key $OK; else tap_text "Ir para o Início"; fi
sleep 5; shot login-2-home
check_contains "Abertos"
play_hero 2 "canal do provedor (plano B)"

# 3) Provedor no padrão completo: login, categorias e consulta por categoria.
echo "== cenário 3: jeito padrão funciona" >> "$STEPS"
login "padrao"
wait_text "Pronto! 25 itens encontrados." 90; shot login-3-pronto
open_report; shot login-3-relatorio
check_contains "Recursos do provedor: pedir canais de uma categoria só: ok"
save_report 3-padrao
if [ "$NAME" = "tv" ]; then focus "início"; focus_until "Ir para o Início" $U; key $OK; else tap_text "Ir para o Início"; fi
sleep 5; shot login-3-home
check_contains "TV · Abertos"
play_hero 3 "canal do provedor (padrão)"

if adb shell pidof "$PKG" > /dev/null; then echo "app aberto (sem fechar sozinho)" >> "$STEPS"; else falha "APP FECHOU"; fi
adb logcat -d "*:E" | grep -iE "nuvora|AndroidRuntime|ANR in com.nuvora" > "$OUT/$NAME-login-erros.txt" || true
echo "login concluído" >> "$STEPS"

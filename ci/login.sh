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

# Confere se algum texto na tela CONTÉM $1 (rola a tela até 10 vezes procurando).
check_contains() {
  for i in 1 2 3 4 5 6 7 8 9 10; do
    read_ui
    if grep -qF "$1" ui.xml; then echo "ok: tela contém '$1'" >> "$STEPS"; return 0; fi
    if [ "$NAME" = "tv" ]; then key $D; else adb shell input swipe 500 1500 500 700 300; sleep 1; fi
  done
  falha "tela não contém '$1'"
}

# Confere que a senha e o usuário de teste não aparecem nem na tela nem nas anotações do app.
check_secret() {
  read_ui
  # O usuário aparece de propósito no topo da tela de escolha (como no CasaTV); a senha nunca.
  if grep -qF "$SENHA" ui.xml; then falha "senha apareceu na tela ($2)"; else echo "ok: senha não aparece na tela ($2)" >> "$STEPS"; fi
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
  login_form "$SERVIDOR" "$usuario" "$SENHA"
}

open_report() {
  if [ "$NAME" = "tv" ]; then focus "relatório"; focus_until "Ver relatório de conexão" $U; focus_until "Ver relatório de conexão" $D; key $OK; else tap_text "Ver relatório de conexão"; fi
  sleep 2
}

# Confere uma linha do relatório de conexão (o app anota o relatório já sem senha).
check_report() {
  if grep -qF "$2" "$OUT/$NAME-relatorio-$1.txt"; then echo "ok: relatório contém '$2'" >> "$STEPS"; else falha "relatório não contém '$2'"; fi
}

# Da tela de escolha: abre TV ao vivo, espera o canal $1 aparecer e toca.
open_live_and_play() {
  if [ "$NAME" = "tv" ]; then focus "tela de escolha"; focus_until "TV ao vivo" $U; key $OK; else tap_text "TV ao vivo"; fi
  sleep 3
  wait_text "$1" 60; shot "login-$2-tv-ao-vivo"
  adb logcat -c
  if [ "$NAME" = "tv" ]; then focus "lista de canais"; focus_until "$1" $D; key $OK; else tap_text "$1"; fi
  sleep 15; shot "login-$2-tocando"
  check_log "aberto id="; check_playing "$3"
  for i in 1 2 3; do key KEYCODE_BACK; sleep 2; if plog | grep -q "fechado"; then break; fi; done
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
wait_text "TV ao vivo" 60; shot login-2-escolha
flog | grep "medida" | sed 's/^/  anotação: /' >> "$STEPS"
save_report 2-so-link
check_report 2-so-link "Recursos do provedor: ok"
check_secret "sololink" "cenário 2"
open_live_and_play "Canal Intermediário 1" 2 "canal do provedor (plano B)"

# 3) Provedor no padrão completo: login, categorias e consulta por categoria.
echo "== cenário 3: jeito padrão funciona" >> "$STEPS"
login "padrao"
wait_text "TV ao vivo" 60; shot login-3-escolha
flog | grep "medida" | sed 's/^/  anotação: /' >> "$STEPS"
sleep 8  # a linha "aceita pedir por categoria?" chega do segundo plano
save_report 3-padrao
check_report 3-padrao "Recursos do provedor: pedir canais de uma categoria só: ok"
check_secret "padrao" "cenário 3"
open_live_and_play "Canal Intermediário 1" 3 "canal do provedor (padrão)"
# Outra categoria, aberta na hora (busca só ela no provedor).
if [ "$NAME" != "tv" ]; then
  tap_text "Esportes"; wait_text "Canal Intermediário 6" 30; shot login-3-esportes
  tap_text "Voltar"; sleep 2
  tap_text "Filmes"; wait_text "Filme Intermediário 1" 60; shot login-3-filmes
  # Filme abre a tela de detalhes (com Assistir e Favoritar).
  tap_card "Filme Intermediário 1"; sleep 4; shot login-3-detalhes
  check_text "Assistir" sim
  key KEYCODE_BACK; sleep 2
fi
# Conta que venceu depois do login: ao abrir o app, aviso claro na tela de escolha (com Sair da conta).
echo "== cenário 4: conta vencida ao abrir o app" >> "$STEPS"
touch expirar
adb shell am force-stop "$PKG"; sleep 2; adb shell am start -n "$PKG/.MainActivity" > /dev/null; sleep 8
check_contains "venceu"
check_contains "Sair da conta"
shot login-4-conta-vencida
rm -f expirar
grep "action=" intermediario.log 2> /dev/null | tail -20 | sed 's/^/  provedor recebeu: /' >> "$STEPS" || true

if adb shell pidof "$PKG" > /dev/null; then echo "app aberto (sem fechar sozinho)" >> "$STEPS"; else falha "APP FECHOU"; fi
adb logcat -d "*:E" | grep -iE "nuvora|AndroidRuntime|ANR in com.nuvora" > "$OUT/$NAME-login-erros.txt" || true
echo "login concluído" >> "$STEPS"

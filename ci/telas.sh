#!/usr/bin/env bash
# Instala o APK no emulador, mede a abertura, percorre as telas, tira prints e registra memória e erros.
# Uso: bash ci/telas.sh celular|tablet|tv
set -uo pipefail

NAME="$1"
PKG=com.nuvora.app
OUT=out
mkdir -p "$OUT"
STEPS="$OUT/$NAME-passos.txt"
source "$(dirname "$0")/comum.sh"

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
  focus "1. abertura sem fonte: entrada (foco no campo Usuário)"
  focus_until "Ver demonstração" $D; focus "2. desceu até Ver demonstração"
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

  # Etapa 7 (Parte 1): adicionar lista por link só com o controle.
  # Reabre o app (se um Voltar a mais tiver saído dele). Sem lista, ele abre nas boas-vindas (que já são o cadastro).
  adb shell am start -n "$PKG/.MainActivity" > /dev/null; sleep 4
  read_ui
  if grep -qF 'Entre com seu usuário e senha' ui.xml; then
    focus "31. entrada na TV (sem lista)"
  else
    for i in 1 2 3 4; do key $L; done; for i in 1 2 3 4 5 6 7 8; do key $D; done; focus "31. menu: Ajustes"; key $OK; sleep 2
    key $R; focus "32. entrou em Ajustes"; focus_until "Adicionar lista" $U; key $OK; sleep 2
    focus "33. tela de entrada"
  fi
  link_form "http://10.0.2.2:8000/teste.m3u"
  wait_text "Pronto! 4 itens encontrados." 30; shot 20-lista-adicionada-tv
  focus_until "Ir para o Início" $D; key $OK; sleep 3
  check_text "Filmes de teste" sim; shot 21-home-com-lista-tv
else
  W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
  H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
  swipe_up() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 500; sleep 2; }

  # Toca numa aba do menu de baixo (1 a 5): pelo nome; se a leitura da tela não der a posição, pela posição.
  tap_tab() {
    local before
    before=$(grep -c FALHOU "$STEPS" 2> /dev/null || true)
    tap_text "$1" sem-rolar
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
  tap_tab "Buscar" 2; tap_text "Campo de busca"; clear_field; adb shell input text "Exemplo%s3"; sleep 1; key KEYCODE_BACK
  adb logcat -c
  tap_card "Exemplo 3"; sleep 25; check_log "fim"; shot 23-mp4-terminou
  key KEYCODE_BACK; sleep 4; check_log "fechado (player liberado)"
  tap_tab "Histórico" 4; check_text "Destaque de exemplo 1" sim; shot 24-historico-apos-assistir
  tap_tab "Início" 1
  for i in 1 2 3; do tap_text "Assistir"; sleep 8; key KEYCODE_BACK; sleep 3; done
  MEM_4=$(total_pss)
  echo "memória (KB): Home antes do player $MEM_ANTES; após 1ª saída $MEM_1; após 5ª saída $MEM_4" >> "$STEPS"
  # Letra maior (Ajustes).
  tap_tab "Ajustes" 5; tap_text "Ligar letra maior"; check_text "Letra maior: ligada" sim; shot 28-ajustes-letra-maior
  tap_tab "Início" 1; shot 29-home-letra-maior

  # Etapa 7 (Parte 1): listas por link.
  tap_tab "Ajustes" 5; tap_text "Voltar ao tamanho normal"; tap_text "Desligar demonstração"
  # Sem nenhuma lista, o app mostra as boas-vindas (já é o cadastro); senão, vai por Ajustes > Adicionar lista.
  open_add() {
    read_ui
    if grep -qF 'Entre com seu usuário e senha' ui.xml && ! grep -qF 'text="Voltar"' ui.xml; then
      echo "  (entrada na tela: ainda sem lista)" >> "$STEPS"
    else
      tap_tab "Ajustes" 5; tap_text "Adicionar lista"
    fi
  }
  add_link() {
    open_add; link_form "$2"
  }
  add_link "Lista%sde%steste" "http://10.0.2.2:8000/teste.m3u"
  wait_text "Pronto! 4 itens encontrados." 30; shot 30-lista-adicionada
  tap_text "Ir para o Início"; sleep 3; check_text "Filmes de teste" sim; shot 31-home-com-lista
  adb logcat -c; tap_text "Assistir"; sleep 15
  check_log "aberto id=f1-0"; check_playing "item da lista M3U"; key KEYCODE_BACK; sleep 3
  # Lista gigante (20.000 itens): tempo de importação e memória.
  MEM_ANTES_LISTA=$(total_pss)
  adb logcat -c
  add_link "Lista%sgigante" "http://10.0.2.2:8000/gigante.m3u"
  wait_text "Pronto! 20.000 itens encontrados." 180; shot 32-lista-gigante
  adb logcat -d -s NuvoraFontes:I | sed 's/.*NuvoraFontes: /  importação: /' >> "$STEPS"
  tap_text "Ir para o Início"; sleep 5; shot 33-home-lista-gigante
  MEM_DEPOIS_LISTA=$(total_pss)
  echo "memória (KB) com a lista de 4 itens: $MEM_ANTES_LISTA; depois de importar 20.000 itens e abrir a Home: $MEM_DEPOIS_LISTA" >> "$STEPS"
  tap_tab "Buscar" 2; tap_text "Campo de busca"; clear_field; adb shell input text "Item%s19999"; key KEYCODE_BACK
  sleep 2; check_text "Item 19999" sim; shot 34-busca-lista-gigante
  # Link errado: mensagem clara.
  add_link "Lista%serrada" "http://10.0.2.2:8000/nao-existe.m3u"
  wait_text "Não encontramos a lista nesse endereço. Confira o link." 30; shot 35-link-errado
  check_text "Detalhe técnico: HTTP 404" sim
  # Provedores "difíceis" (imitados pelo servidor de teste).
  add_link "Provedor%slento" "http://10.0.2.2:8000/lento.m3u"
  wait_text "Pronto! 4 itens encontrados." 120; shot 36-provedor-lento
  add_link "Exige%snavegador" "http://10.0.2.2:8000/exige-navegador.m3u"
  wait_text "Pronto! 4 itens encontrados." 30
  add_link "Redireciona" "http://10.0.2.2:8000/redireciona.m3u"
  wait_text "Pronto! 4 itens encontrados." 30
  add_link "Limite" "http://10.0.2.2:8000/erro-456"
  wait_text "Detalhe técnico: HTTP 456" 30; shot 37-limite-de-conexoes
  check_text "O provedor limitou o acesso agora (muitas conexões ao mesmo tempo ou assinatura vencida). Tente mais tarde ou fale com o seu fornecedor." sim
  # Certificado inválido no https: o app tenta o http sozinho.
  adb logcat -c
  add_link "Certificado%sruim" "https://10.0.2.2/teste.m3u"
  wait_text "Pronto! 4 itens encontrados." 40; shot 38-certificado-ruim
  adb logcat -d -s NuvoraFontes:I | grep -i "certificado" | sed 's/.*NuvoraFontes: /  anotação: /' >> "$STEPS"
  # Provedor que corta a conexão no meio: o app tenta de novo sozinho.
  adb logcat -c
  add_link "Corta%sno%smeio" "http://10.0.2.2:8000/corta.m3u"
  wait_text "Pronto! 20.000 itens encontrados." 240; shot 39-corta-no-meio
  adb logcat -d -s NuvoraFontes:I | grep -E "caiu|tentativa" | sed 's/.*NuvoraFontes: /  anotação: /' >> "$STEPS"

  # Etapa 7 (Parte 2): Provedor, Usuário e Senha.
  add_provider() {
    open_add; login_form "$2" "$3" "$4"
  }
  add_provider "Senha%serrada" "http://10.0.2.2:8000" "teste" "0000"
  wait_text "Usuário ou senha incorretos. Confira e tente de novo." 30; shot 40-senha-errada
  add_provider "Vencido" "http://10.0.2.2:8000" "vencido" "1234"
  wait_text "A sua assinatura venceu. Fale com o seu fornecedor." 30
  check_text "Detalhe técnico: venceu em 13/09/2020" sim; shot 41-assinatura-vencida
  # Provedor certo (a separação do link colado tem testes automáticos próprios).
  add_provider "Meu%sprovedor" "http://10.0.2.2:8000" "teste" "1234"
  wait_text "Pronto! 5 itens encontrados." 60; shot 42-provedor-ok
  tap_text "Ir para o Início"; sleep 3
  tap_tab "Buscar" 2; tap_text "Campo de busca"; clear_field; adb shell input text "Canal%sHLS"; key KEYCODE_BACK; sleep 2
  adb logcat -c; tap_card "Canal HLS de teste"; sleep 15
  check_log "aberto id="; check_playing "canal ao vivo do provedor"; key KEYCODE_BACK; sleep 3
  tap_text "Campo de busca"; clear_field; adb shell input text "rie%sde%steste"; key KEYCODE_BACK; sleep 2
  tap_card "Série de teste"; wait_text "T1 E2 · Episódio 2" 30; shot 43-episodios
  adb logcat -c; tap_text "T1 E2 · Episódio 2"; sleep 12
  check_log "-e402"; check_log "fim"; key KEYCODE_BACK; sleep 3
fi

if adb shell pidof "$PKG" > /dev/null; then echo "app aberto (sem fechar sozinho)" > "$OUT/$NAME-status.txt"; else echo "APP FECHOU" > "$OUT/$NAME-status.txt"; fi
adb shell dumpsys meminfo "$PKG" > "$OUT/$NAME-memoria.txt"
adb logcat -d "*:E" | grep -iE "nuvora|AndroidRuntime|ANR in com.nuvora" > "$OUT/$NAME-erros.txt" || true
echo "concluído"

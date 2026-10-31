# NUVORA — Registro de progresso

Arquivo vivo: decisões, etapas aprovadas, testes e pendências. Atualizado a cada entrega.

## Regras de trabalho (combinadas em 29/10/2026)
- Uma etapa por vez. Só avanço quando a Glaucia responder expressamente que podemos avançar.
- Cada entrega segue o formato: Etapa atual · Objetivo · O que foi implementado · Arquivos criados ou alterados · Testes executados · Resultado · Pendências · Próxima etapa prevista.
- Nada é declarado "funcionando" sem teste real. Problemas são explicados com causa e proposta antes de continuar.
- Sem refatorações desnecessárias, sem funcionalidades fora do escopo sem autorização, sem alterar partes aprovadas sem avisar.
- Só fontes de conteúdo autorizadas. Nada de APIs, servidores ou credenciais inventados; nada que contorne DRM.

## Onde paramos (30/10/2026, noite)
- Etapa 7 — **Parte 1 entregue** (listas M3U por link e arquivo). Falta a Glaucia aprovar a Parte 1 para começar a Parte 2 (Provedor/Usuário/Senha + QR Code na TV).
- APK: `apk/nuvora-etapa7-parte2a.apk` — Parte 2 em andamento: Provedor/Usuário/Senha e boas-vindas prontos e testados; falta o QR Code na TV.

## Mudança de direção pedida pela Glaucia (31/10/2026)
- A Glaucia quer que eu assuma a responsabilidade técnica do produto como um todo, e não só corrija detalhes: entender a referência (app CasaTV), comparar com o NUVORA atual, apresentar o que falta, e só então implementar, testar e validar por fases. Aproveitar o que já funciona; não recomeçar.
- Referência: o CasaTV.apk **não será aberto nem descompilado** (direitos de terceiros e segurança). Alternativa combinada: a Glaucia envia capturas de tela/gravação de tela do CasaTV em uso (celular e TV), e eu analiso a experiência (organização, navegação, apresentação, player, controle remoto, leveza) para criar uma solução própria do NUVORA.
- Implementação pausada até a Glaucia aprovar o entendimento e a proposta de direção.

## Situação das etapas
| Etapa | Descrição | Situação |
|---|---|---|
| 1 | Análise do ambiente, arquitetura e estrutura | **Aprovada** em 29/10/2026 ("Pode avançar") |
| 2 | Identidade visual e componentes reutilizáveis | **Aprovada** em 29/10/2026 ("Pode avançar"). Teste nos aparelhos reais ainda não relatado |
| 3 | Home para celular | **Aprovada** em 30/10/2026 ("Pode avançar"). Teste nos aparelhos reais ainda não relatado |
| 4 | Home específica para Android TV | **Aprovada** em 31/10/2026 ("Pode avançar"). TV real não testada (TV da Glaucia é Roku) |
| 5 | Navegação e gerenciamento de estado | **Aprovada** em 31/10/2026 ("Pode avançar") |
| 6 | Player (Media3) | **Aprovada** em 31/10/2026 ("Pode avançar") |
| 7 | Integração com fontes autorizadas | Em andamento em 2 partes (decisão de 31/10/2026): **Parte 1** listas M3U por link e arquivo; **Parte 2** login de servidor (padrão Xtream, testado com servidor de teste próprio) + QR Code na TV |
| 8 | Otimização de desempenho e memória | Não iniciada |
| 9 | Testes e correções | Não iniciada |
| 10 | APK de teste e documentação de compilação | Não iniciada |

---

## ETAPA 1 — Análise do ambiente e arquitetura

### 1.1 Ambiente encontrado (verificado em 29/10/2026)
| Item | Resultado |
|---|---|
| Computador | Intel Celeron N4120 @ 1,10 GHz, **3,8 GB de RAM**, 327 GB livres no C: |
| Java (JDK) | Não instalado |
| Android SDK / Android Studio | Não instalados |
| Gradle / adb | Não instalados |
| Git | Instalado |
| Projeto NUVORA existente | Não existe. Pasta criada do zero em `C:\Projetos\nuvora` |
| Arquivos da identidade VEXORA | Não encontrados no computador (procurei em Documentos, Área de Trabalho e Downloads) |
| Aparelhos de teste reais | Celular Android, tablet Android e Android TV / Google TV |

**Risco principal do ambiente:** o Android Studio pede no mínimo 8 GB de RAM; a compilação com Gradle sozinha costuma usar 2–3 GB. Com 3,8 GB, compilar localmente seria muito lento ou falharia, e emuladores (principalmente de TV) não são viáveis.

**Proposta de ambiente (decisão pendente):**
- **A — Compilação na nuvem (recomendada):** o código fica num repositório privado no GitHub; a cada alteração, o GitHub Actions compila e gera o APK para baixar. Gratuito dentro da cota mensal de repositórios privados. No computador, só instalamos o `adb` (≈ 10 MB) para instalar o APK nos aparelhos e medir memória/inicialização neles.
- **B — Compilação local:** instalar JDK 17 + Android SDK em linha de comando (sem Android Studio) e compilar com memória limitada. Possível, mas lento (primeira compilação provavelmente acima de 15 min) e com risco de travar o computador.
- Os testes de desempenho serão feitos nos **aparelhos reais**, que é o que importa para a meta de rodar bem em hardware modesto.

### 1.2 Tecnologias escolhidas e por quê
| Tecnologia | Uso | Justificativa |
|---|---|---|
| Kotlin | Linguagem | Padrão oficial do Android |
| Jetpack Compose | Interface do celular/tablet | Interface própria (não genérica), menos código, sem XML |
| Compose for TV (`androidx.tv:tv-material`) | Interface da TV | Componentes oficiais com foco, destaque e navegação por controle remoto |
| AndroidX Media3 (ExoPlayer + UI + HLS + DASH) | Player | Oficial do Google, suporta HLS, DASH, MP4, legendas, troca de áudio e Widevine para conteúdo licenciado |
| Coil 3 | Imagens | Leve, nativo para Compose, cache em memória e disco com limite configurável, redimensiona para o tamanho exibido |
| Coroutines + Flow | Estado e tarefas assíncronas | Padrão do Kotlin, sem bibliotecas extras |
| Navigation Compose | Navegação entre telas | Oficial, integrado ao botão voltar do celular e do controle |
| Room | Histórico, "continuar assistindo" e favoritos | Necessidade real: consultas por data, posição de reprodução e lista de favoritos. Configurações simples ficam no DataStore |
| OkHttp | Rede | Base do Coil e do Media3; reaproveitada para a camada de fontes |
| Baseline Profiles | Inicialização rápida | Pré-compila o caminho de abertura; ganho medido em aparelhos fracos |

**Não vamos usar:** injeção de dependência pesada (Hilt/Dagger — dependências serão criadas manualmente, num único ponto), Retrofit/serializadores grandes até sabermos o formato da fonte, Lottie, efeitos de desfoque (blur) em tempo real, vídeos automáticos nos banners.

As versões exatas das dependências (estáveis e compatíveis entre si) serão fixadas num catálogo único (`gradle/libs.versions.toml`) no início da Etapa 2 e registradas aqui.

### 1.3 Arquitetura proposta
Um **único APK** que funciona em celular, tablet e TV. Ao abrir, o app detecta se está numa TV e mostra a experiência de TV; caso contrário, a de toque. As duas compartilham dados, player e regras — só a interface muda.

```
nuvora/
├─ app/                    → ponto de entrada; decide TV ou celular; manifesto (celular + TV)
├─ core/
│  ├─ designsystem/        → cores, tipografia, formas, logo, cards, botões, estados (carregando/erro/vazio)
│  ├─ data/                → histórico, continuar assistindo, favoritos (Room) e configurações (DataStore)
│  └─ player/              → player Media3: criação, liberação, erros, reconexão, legendas e áudio
├─ source/
│  └─ api/                 → "contrato" da fonte de conteúdo (catálogo, detalhes, endereço de reprodução)
├─ feature/
│  ├─ mobile/              → telas de toque: Home, Busca, Detalhes, Favoritos, Histórico, Configurações
│  └─ tv/                  → telas de TV: Home com fileiras e foco, Busca, Detalhes, Configurações
└─ gradle/libs.versions.toml
```

São 7 módulos — o suficiente para separar responsabilidades sem pesar a compilação.

**Fluxo de dados:** Fonte (source/api) → Repositório (core/data) → ViewModel (estado em Flow) → Tela (mobile ou tv). O player recebe apenas o endereço e as informações de legenda/áudio; nunca conhece a fonte.

**Camada de fontes (Etapa 7):** só a interface (contrato) será criada antes de termos uma fonte real. Cada fonte autorizada vira uma implementação desse contrato. Credenciais ficam criptografadas no aparelho (Android Keystore), nunca no código nem em logs.

**Sem fonte conectada, a Home não mostra conteúdo inventado:** mostra um estado vazio bem desenhado ("Nenhuma fonte conectada" + botão para Configurações). Histórico e favoritos começam vazios de verdade.

### 1.4 Metas de desempenho (medidas nos aparelhos reais)
| Meta | Alvo | Como medir |
|---|---|---|
| Abertura a frio | < 1,5 s no celular; < 2,0 s na TV | `adb shell am start -W` (TotalTime), média de 5 aberturas |
| Memória na Home | < 150 MB (PSS total) | `adb shell dumpsys meminfo` |
| Memória reproduzindo | < 250 MB | idem, durante reprodução |
| Memória após sair do player | volta a ±10% do valor da Home | idem, 3 ciclos Home → player → Home |
| Rolagem | < 5% de quadros lentos | `adb shell dumpsys gfxinfo` |
| Tamanho do APK | < 15 MB | arquivo gerado |
| Cache de imagens | memória: 15% do heap; disco: 100 MB | configuração do Coil |

### 1.5 Riscos técnicos
1. **Computador com 3,8 GB de RAM** — compilação local inviável na prática. Mitigação: compilação na nuvem (opção A).
2. **TVs/TV Boxes muito fracas** (1–2 GB de RAM, Android 7–9) — mitigação: imagens no tamanho exato do card, fileiras carregadas sob demanda, sem blur, animações curtas, Baseline Profiles.
3. **Formatos e codecs variam por aparelho** — nem toda TV decodifica HEVC/4K. Mitigação: o player escolhe a faixa suportada e mostra erro amigável quando nada é compatível.
4. **Fonte de conteúdo ainda indefinida** — sem ela, Home e player não têm o que mostrar. Precisamos dos dados técnicos e da documentação da fonte autorizada.
5. **Conteúdo com DRM** — suportado apenas via Widevine com licença do operador; nunca contornado.
6. **Identidade VEXORA** — não encontrei os arquivos; sem eles a "preservação integral" não é possível.

### 1.6 Primeira proposta visual (provisória, até recebermos a VEXORA)
| Token | Cor | Uso |
|---|---|---|
| Fundo | `#070B14` (preto azulado) | fundo das telas |
| Superfície | `#121826` (grafite) | cards, menus |
| Superfície elevada | `#1B2335` | card em foco / hover |
| Azul elétrico | `#2F6BFF` | ações principais, barra de progresso |
| Ciano luminoso | `#22D3EE` | foco na TV, destaques, brilho discreto |
| Violeta | `#8B5CF6` | acentos, gradiente do logo |
| Texto principal | `#F1F5F9` | títulos |
| Texto secundário | `#94A3B8` | descrições |

- Tipografia: **Poppins** (a mesma da VEXORA), só os pesos Regular e Medium, embutida no app.
- Cards: cantos de 16 dp (celular) e 20 dp (TV); na TV o item em foco cresce 6% e ganha borda ciano com brilho suave (sombra colorida, sem blur).
- Banner principal: imagem estática com gradiente do fundo por cima para dar leitura ao texto.
- Logo: símbolo minimalista (nuvem/onda estilizada com gradiente azul → violeta) + "NUVORA" em letras espaçadas. Será desenhado em vetor na Etapa 2.

---

## ETAPA 2 — Identidade visual e componentes reutilizáveis

### O que foi feito
- Projeto Android criado com 2 módulos: `app` (abre o app e escolhe TV ou celular) e `core/designsystem` (visual e componentes). Os demais módulos da arquitetura serão criados nas etapas que os usam.
- Tema próprio sem a biblioteca Material (mais leve): cores, Poppins embutida (Regular e Medium, licença OFL em `licenses/`), tamanhos de texto e medidas separados para celular e TV.
- Logo: símbolo "N" (gradiente azul → violeta com ponto de luz ciano) + "NUVORA" espaçado. O desenho anterior parecia um "M"; foi corrigido. Ícone do app e banner da TV (320×180) em vetor.
- 13 ícones próprios (evita a biblioteca de ícones do Material, de vários MB).
- Componentes: botão principal/secundário/só ícone, categoria, título de seção com "Ver tudo", cartão vertical (capa), cartão horizontal com barra de "continuar assistindo", banner principal, menu inferior (celular), menu lateral (TV), carregamento (blocos pulsando), erro com "Tentar de novo", sem internet, e "nenhuma fonte conectada".
- Todo componente funciona com toque (celular) e com controle remoto (TV): na TV, o item em foco cresce 6% e ganha borda ciano.
- Vitrine de componentes (tela provisória, só para teste desta etapa) no celular e na TV. Não é a Home final e não usa conteúdo real.
- Repositório privado `glauciabritobeauty/nuvora` + compilação automática no GitHub Actions; a cada envio, o APK é gerado e aberto em emuladores de celular e de TV, com prints, memória e erros.

### Versões fixadas (`gradle/libs.versions.toml`)
Android Gradle Plugin 9.5.0 · Gradle 9.8.2 · Kotlin 2.4.21 · Compose BOM 2026.09.00 · activity-compose 1.13.0 · tv-material 1.1.0 · Coil 3.6.3 · compileSdk 37 (exigido pelas bibliotecas) · targetSdk 36 · minSdk 23 (Android 6, provisório até sabermos os aparelhos).

### Testes (emuladores na nuvem, versão debug, 29/10/2026)
| Teste | Celular (Pixel 5, Android 11) | TV (1080p, Android TV 11) |
|---|---|---|
| Compilação | OK | OK |
| Abre sem fechar sozinho | OK | OK |
| Toque/OK no "Assistir" registra a ação | OK | OK |
| Rolagem e navegação pelas setas | OK (4 rolagens) | OK (baixo, direita, esquerda até o menu) |
| Memória (PSS total) | 71 MB | 74 MB |
| Abertura a frio (média de 5) | ~6,4 s | ~5,1 s |
| Erros do app no registro | nenhum | nenhum |

- A abertura no emulador **não vale como meta**: é versão debug, em emulador sem placa de vídeo. A meta (< 1,5 s celular, < 2 s TV) será medida em versão otimizada nos aparelhos reais (Etapa 8).
- Problemas achados nos prints e corrigidos na mesma etapa: ícones da barra de status escuros no celular; "luz" do banner com corte reto; botão em foco cortado na TV; os 3 avisos espremidos na TV.
- Ainda **não testado em aparelho real**.

---

## ETAPA 3 — Home para celular e tablet

### O que foi feito
- Novo módulo `feature/mobile`: a vitrine de teste do celular foi **removida** (`app/.../MobileCatalog.kt`, era provisória da Etapa 2) e substituída pela Home real. A TV continua com a vitrine até a Etapa 4.
- Home: logo + lupa, banner que desliza para o lado (só monta a página visível e as vizinhas), Continuar assistindo, Categorias (filtram de verdade os recentes), Adicionados recentemente, Favoritos e Histórico. Seções vazias não aparecem.
- Estado real hoje: **"Nenhuma fonte conectada"** (não há fonte até a Etapa 7). Estados de carregando/erro/sem internet já estão ligados à Home para quando houver fonte.
- Abas do menu: Buscar (procura de verdade), Favoritos e Histórico (grade ou aviso de vazio), Ajustes (informações do app). "Ver tudo" abre a seção em grade (mais colunas no tablet). Botão voltar: fecha "Ver tudo" → volta ao Início → sai do app.
- Favoritar funciona na hora (coração cheio + fileira Favoritos), mas **ainda não fica salvo** ao fechar o app (salvamento no aparelho na Etapa 5).
- Tocar num cartão mostra o aviso "a reprodução chega na Etapa 6" (o player ainda não existe).
- **Demonstração só na versão de teste:** botão "Ver demonstração" com cartões "Exemplo N", sem capa, e faixa "Modo demonstração". O arquivo da versão final (`src/release`) não tem demonstração.
- Rotação da tela e troca de aba mantêm o estado (demonstração, favoritos, busca).

### Testes (emuladores na nuvem, 30/10/2026)
| Teste | Celular (Pixel 5) | Tablet (Pixel C) | TV |
|---|---|---|---|
| Compila (teste e versão final otimizada) | OK | OK | OK |
| Abre sem fechar sozinho / sem erros do app | OK | OK | OK |
| Estado real "Nenhuma fonte conectada" | OK | OK | — (vitrine) |
| Demonstração, favoritar, categoria "Séries" filtra | OK | OK | — |
| Busca "Exemplo 1" acha 8 resultados | OK | OK | — |
| Abas Favoritos e Ajustes, voltar → Início, "Ver tudo" | OK | OK | — |
| Deitar e voltar a tela em pé, mantendo o estado | OK | OK | — |
| Memória (PSS) | 68 MB | 79 MB | 72 MB |

- **Tamanho do APK final otimizado: 1,35 MB** (meta < 15 MB). O APK de teste tem 13 MB por não ser otimizado.
- Problema encontrado e **ainda aberto**: no emulador de celular, a "leitura da tela" usada pelos leitores de acessibilidade (TalkBack) informa o menu inferior com tamanho zero; no tablet está correto. No teste automático, as abas do celular foram tocadas pela posição. Causa ainda não identificada — investigar com o TalkBack ligado (Etapa 9 ou antes). **Pista (31/10/2026):** o mesmo acontece com a fileira de baixo dos controles do player no tablet — tudo o que fica na faixa inferior da tela aparece com tamanho zero para a leitura de acessibilidade.
- Ainda **não testado em aparelho real**.

---

## ETAPA 4 — Home específica para Android TV

### O que foi feito
- Novo módulo `feature/tv`; a vitrine de teste da TV (Etapa 2) foi **removida** e substituída pela Home real.
- Menu lateral (Buscar, Início, Favoritos, Histórico, Categorias e Ajustes embaixo), banner com Assistir e Minha lista, fileiras Continuar assistindo, Categorias (filtram de verdade), Adicionados recentemente, Favoritos e Histórico.
- Controle remoto: foco começa na primeira ação da tela; ao voltar para uma fileira, o foco volta ao último cartão visitado; Voltar leva ao menu → ao Início → sai do app.
- Busca na TV: o teclado só abre com OK no campo (passar com as setas não abre, e não digita letra sozinha).
- Telas de TV para Buscar, Favoritos, Histórico, Categorias e Ajustes, com os mesmos estados reais do celular e demonstração só na versão de teste.
- **Mudanças em partes já aprovadas (avisadas antes):** modelos e demonstração da Home movidos para o novo módulo `core/data` (celular e TV usam os mesmos; nada muda na tela do celular); componentes ganharam opção de foco inicial e de devolver o foco ao menu; cartões passaram a informar o título ao leitor de tela; título do banner na TV ficou mais largo.

### Testes (emuladores na nuvem, 30/10/2026)
- TV, só com setas/OK/Voltar (14 passos registrados com o item em foco): abertura → "Abrir ajustes"; seta baixo → "Ver demonstração"; OK → "Assistir"; Minha lista → "Na minha lista"; fileira Continuar → "Exemplo 4, Faltam 44 min"; categoria Séries selecionada; voltar à fileira de categorias devolve o foco em "Séries"; Voltar → menu "Início"; menu Favoritos; Voltar no menu → Início com foco em "Assistir"; busca digitada "Exemplo 1" sem letra extra. **Todos OK.**
- Celular e tablet continuam passando no roteiro da Etapa 3 (no celular, abas tocadas pela posição — pendência de acessibilidade ainda aberta).
- App aberto sem fechar e sem erros nos 3. Memória: celular 87 MB, tablet 91 MB, TV 76 MB (versão de teste).
- Problemas achados e corrigidos na etapa: teclado da TV abria sozinho e digitava "q"; título do banner quebrava cedo; cartões sem nome para o leitor de tela. Uma rodada do tablet foi atrapalhada por um aviso do próprio emulador ("Pixel Launcher isn't responding"); repetida e passou.
- Ainda **não testado em aparelho real**.

---

## ETAPA 5 — Navegação, estado e salvamento no aparelho

### O que foi feito
- **Favoritos e histórico salvos no aparelho** (banco SQLite do próprio Android, sem biblioteca extra). Título e capa ficam guardados junto. Nada sai do aparelho.
- **"Cérebro" compartilhado** (`core/data/NuvoraViewModel`): o mesmo para celular e TV; junta a fonte (por enquanto só a demonstração) com favoritos e histórico; sobrevive à rotação; estado de carregamento real enquanto lê o banco.
- **Continuar assistindo vem do histórico real**: aparece o que está entre 5% e 95% assistido, com "Faltam X min" arredondado para cima. A demonstração grava um histórico de exemplo (4 pela metade, 5 terminados), já que o player só chega na Etapa 6.
- **Exclusão do histórico (pedido da Glaucia):** segurar o cartão (celular) ou segurar o OK (TV) → "Remover do histórico"; botão "Limpar histórico" com confirmação na aba Histórico e em Ajustes. Também "Remover dos favoritos" do mesmo jeito. A janela abre com o foco em "Cancelar" (evita apagar sem querer na TV).
- Itens de exemplo só aparecem com a demonstração ligada (ids `demo-`).
- **Mudanças em relação ao plano da Etapa 1 (avisadas antes):** SQLite do Android em vez da biblioteca Room; navegação própria e simples em vez da biblioteca Navigation (menos peso). Novas dependências: `lifecycle-viewmodel-compose` e `lifecycle-runtime-compose` 2.11.0 (oficiais) e JUnit 4.13.2 (só para testes, não vai no app).
- Versão 0.5.0. Assinada com a chave fixa de teste: deve **atualizar por cima** da versão da Etapa 4.

### Testes (31/10/2026)
- Testes automáticos das regras do "Continuar assistindo" (5 casos: espiada, pela metade, terminado, duração desconhecida, arredondamento): **passaram**.
- Celular e tablet: Histórico mostra os itens → segurar "Exemplo 5" → Remover → some; Limpar histórico → confirmar → "Nada assistido ainda"; fechar o app à força, reabrir → favorito "Destaque de exemplo 1" continua lá. **Todos OK.**
- TV, só controle: segurar OK no cartão → janela abre no "Cancelar" → cima → "Remover do histórico" → o cartão some; "Limpar histórico" → confirmação abre no "Cancelar" → confirmar → vazio. **Todos OK.** Roteiro da Etapa 4 continua passando.
- App aberto sem fechar e sem erros nos 3. Memória: celular 63 MB, tablet 60 MB, TV 80 MB.
- Problemas do próprio emulador (aviso "Pixel Launcher isn't responding") atrapalharam duas rodadas do tablet; o teste agora esconde esses avisos de outros apps (travamentos do NUVORA continuam sendo registrados).
- Não testado ainda: atualização por cima num celular real (primeira prova é este APK).

---

## ETAPA 6 — Player (Media3)

### O que foi feito
- Novo módulo `core/player` com AndroidX Media3 1.11.1 (ExoPlayer + HLS + DASH + tela em Compose). MP4 vem junto no ExoPlayer.
- Controles próprios no visual NUVORA: sair, título, voltar/avançar 10 s, tocar/pausar, barra de progresso (tocar/arrastar no celular; ← → na TV), tempo, Áudio (só aparece se houver mais de uma opção), Legendas (com "Desligadas"), volume − / +.
- Celular: tela cheia deitada; tocar mostra/esconde controles. TV: OK/setas mostram controles; ← → com controles escondidos voltam/avançam 10 s; Voltar esconde controles e depois sai.
- Áudio e legenda preferem português automaticamente; cada idioma aparece uma vez na lista; janelas com muitas opções rolam.
- Carregando: arco girando só enquanto carrega. Erros com mensagens amigáveis (conexão caiu, vídeo indisponível, formato não suportado, outros) e "Tentar de novo"; **quando a internet volta, retoma sozinho do mesmo ponto**.
- Continua de onde parou (posição salva a cada 10 s e ao sair; se a posição salva passou do fim, começa do início). Pausa e salva ao sair do app.
- **Recursos liberados:** o player só existe com a tela aberta; ao sair é liberado (vídeo, áudio, memória). Guarda no máximo 30 s de vídeo à frente (menos memória). Tela sempre acesa só durante o vídeo.
- A Home volta exatamente como estava (rolagem e aba) ao sair do player.
- Vídeos públicos de teste (só versão de teste, autorizados): HLS da Apple (10 min, legenda), DASH "Angel One" do Google/Shaka (vários idiomas de áudio e legenda), MP4 "Big Buck Bunny" de 10 s.
- Versão de teste anota eventos do player no registro do Android (tecla, posição, falha, retomou, áudio escolhido, fechado) para os testes automáticos; a versão final não anota nada.
- Novas permissões: Internet e estado da rede. A tela não é recriada ao girar (o vídeo não reinicia).
- **APK final otimizado: 1,52 MB** (era 1,35 MB; o player acrescentou ~170 KB).

### Testes (emuladores na nuvem, 31/10/2026)
- Celular e tablet: HLS abre e anda; **modo avião no meio do vídeo → "A conexão caiu" → internet volta → retomou sozinho e continuou andando**; sair libera o player; MP4 de 10 s chega ao fim e mostra "tocar de novo"; histórico registra o que foi assistido. **Todos OK.**
- TV, só controle: abre, anda, → avança 10 s, Voltar sai e libera o player; DASH com vários idiomas: pausar, descer até "Áudio", escolher **Espanhol** → aplicado. Legenda em português escolhida sozinha. **Todos OK.**
- Memória (PSS, após limpeza de memória): celular 60 MB na Home → 79 MB após a 1ª saída do player → 84 MB após a 5ª; tablet 59 → 78 → 83; TV 81 → 92 → 94. A subida da 1ª vez é o Android carregando as peças de vídeo (uma vez só); entre a 1ª e a 5ª saída cresce menos de 7% → **sem vazamento** (meta ±10%).
- O NUVORA não fechou sozinho em nenhuma rodada. Os erros "AndroidRuntime" vistos no registro foram da ferramenta de leitura de tela do emulador (uiautomator), não do app.
- **Defeitos achados e corrigidos na etapa:** Voltar na TV não saía do vídeo; lista de áudio com idiomas repetidos; janela de opções maior que a tela da TV sem rolar; botão de voltar 10 s ilegível sobre vídeo claro; legenda no meio da tela com controles abertos.
- **Ajustes pedidos pela Glaucia antes da aprovação (31/10/2026), feitos e testados:**
  - Português do Brasil primeiro (áudio e legenda), depois qualquer português.
  - Legendas: botão sempre visível no player (avisa "Este vídeo não tem legenda disponível na fonte" quando não há); preferência em Ajustes — Automático (padrão: legenda em português quando o áudio não é português), Sempre ligadas (prefere legenda para surdos), Desligadas; a pessoa muda em qualquer vídeo. Teste: Automático → vídeo em inglês sem legenda PT começa sem legenda; "Sempre ligadas" → o mesmo vídeo começa com legenda; vídeo com legenda PT → começa em português. **OK**.
  - Simplicidade: botões do player com palavra ("Sair", "10 s", "Áudio", "Legendas", "Volume"); opção "Letra maior" em Ajustes (+20% em todos os textos, lembrada). Teste: ligar e ver a Home com letra maior. **OK**.
  - TV: com os controles na tela, uma seta/OK sempre leva o foco aos controles (antes, às vezes o OK não fazia nada). **OK** (pausar pelo controle confirmado).
- Pendências: na TV, ao sair do player às vezes nenhum item fica em foco (a primeira seta leva ao menu) — corrigir na Etapa 8/9; acessibilidade da faixa inferior (já registrada); teste em aparelho real.

---

## ETAPA 7 — Parte 1: listas M3U por link e por arquivo

### O que foi feito
- Novo módulo `source/api` com o leitor de listas M3U/M3U8: lê linha por linha (nunca a lista inteira na memória); entende logo, categoria, nome, `#EXTGRP`, BOM e quebras de linha do Windows; ignora o que não é link. 7 testes automáticos (inclui lista de 20.000 itens).
- **Cofre** (`core/data/security/CryptoBox`): chave mestra no Android Keystore protegendo uma chave de dados AES-GCM; todos os links das listas (que podem ter usuário e senha dentro) ficam cifrados no aparelho. Nada em texto puro nem em logs.
- **Banco de listas** (`fontes.db`, SQLite do Android): listas e itens; a importação grava direto no banco em uma transação (se der errado, nada muda; ao atualizar, a versão antiga continua valendo).
- Erros em português simples: sem internet, link não encontrado, acesso recusado (assinatura), não é uma lista, lista vazia, provedor com problema, arquivo ilegível.
- Novo módulo `feature/sources`: **Ajustes → Fontes de conteúdo** (listas com quantidade de itens e data, Atualizar, Remover com confirmação, Adicionar lista) e tela **Adicionar lista** (Link da lista ou Arquivo do aparelho; nome; caixa "Tenho direito de acesso a este conteúdo" com explicação de que o NUVORA é só um reprodutor; "Testar e salvar"; "Pronto! N itens encontrados" → "Ir para o Início"). Na TV só aparece o link; o foco já começa no primeiro campo.
- Home com a lista: destaques, categorias da lista, itens por categoria (amostra de até 40 por categoria, 30 categorias), Continuar assistindo, Favoritos e Histórico. Busca direto no banco (até 100 resultados). Tocar abre o player com o endereço decifrado só naquele momento.
- **Capas e logos** com Coil + rede (OkHttp): no máximo 15% da memória do app e 100 MB no armazenamento, apagando as mais antigas.
- Listas e provedores em **http** liberados (configuração de rede). Risco explicado à Glaucia: nesses casos a comunicação com o fornecedor não é criptografada.
- Campo de busca com nome para leitor de tela; Ajustes rola; campos de texto da TV (`NuvoraTextField`) não prendem o foco.
- **APK final: 2,38 MB** (era 1,52 MB; +0,86 MB do leitor de listas, banco, capas pela internet).

### Testes (emuladores na nuvem, 31/10/2026; listas servidas pelo próprio computador de teste em http)
- Celular e tablet: adicionar lista por link → "Pronto! 4 itens encontrados" → Home com as categorias da lista e logos → tocar item da lista (player abre e anda). **OK.**
- **Lista gigante de 20.000 itens: importada em 1,9 a 4,9 s; memória não subiu** (≈107 MB → ≈101 MB depois de importar e abrir a Home). Busca "Item 19999" achou o item. **OK.**
- Link errado → "Não encontramos a lista nesse endereço. Confira o link." **OK.**
- TV só com controle: Ajustes → Adicionar lista → digitar nome e link → marcar direito de acesso → Testar e salvar → "Pronto! 4 itens" → Início com a lista. **OK.**
- **Defeitos achados e corrigidos:** na TV, com o teclado fechado, as setas não saíam do campo de texto (a pessoa ficava presa) — corrigido também na busca da TV; Ajustes não rolava (itens ficavam fora da tela).
- Não testado: importação por **arquivo** (precisa do seletor de arquivos do Android, difícil de automatizar) — testar no celular real; listas reais de fornecedores.
- **Correção depois do teste real da Glaucia (31/10/2026):** um link real do tipo `get.php?username=...&password=...&type=m3u_plus` deu "Não foi possível ler a lista". O link não foi aberto por mim (fonte não verificada como autorizada; contém senha). Correções: o app se apresenta como navegador (lista e player); espera até 2 min pela resposta; entende códigos de limite de conexões/assinatura (429, 456, 458, 509, 884); mostra "Detalhe técnico" (ex.: HTTP 456) embaixo do erro; mostra o andamento ("já li N itens"); nunca fecha por causa de lista estranha; player segue redirecionamento http→https. Testes com servidor que imita provedores: lento (45 s), exige navegador, redireciona, limite 456, link inexistente (404) — **todos OK** no celular e tablet. Aguardando a Glaucia testar de novo e, se falhar, mandar o "Detalhe técnico".
- **2º teste real (31/10/2026):** "SSLPeerUnverifiedException" (certificado do https do provedor inválido) e, pelo http, "SocketException" (provedor cortou a conexão no meio). Correções: (1) se o certificado do https for inválido, o app tenta o mesmo endereço pelo http sozinho — na lista e no vídeo (autorizado pela Glaucia: "sim"; risco explicado: sem criptografia com aquele provedor; o app continua recusando certificado falso no https); (2) a lista é baixada inteira para um arquivo temporário antes de organizar, mostrando "Baixando a lista... X MB", com até 3 tentativas se a conexão cair. Testes com servidor imitando certificado inválido (https real com certificado de outro site) e corte da conexão no meio 2 vezes: **OK** (entrou na 3ª tentativa).
- Pendências: o título "Adicionados recentemente" não faz sentido para listas — trocar por "Da sua lista" (pequeno, pedir ok); TV ao sair do player às vezes sem foco; acessibilidade da faixa inferior.

---

## ETAPA 7 — Parte 2 (em andamento)

### Feito e testado (31/10/2026)
- **Provedor, Usuário e Senha** (padrão de mercado conhecido como "Xtream", o "Login with Xtream Codes API" de apps como o VU que a Glaucia usava): confere o acesso (`player_api.php`) e busca canais, filmes e séries em pedidos separados (mais leve e confiável que uma lista gigante), lendo as respostas aos poucos. Itens agrupados como "TV · …", "Filmes · …", "Séries · …". Dados de acesso cifrados no cofre.
- Mensagens: "Usuário ou senha incorretos", "A sua assinatura venceu" (com a data no detalhe técnico), "Esse endereço não respondeu como um provedor".
- Colar o link completo (get.php?username=…&password=…) no campo Provedor: o app separa Provedor/Usuário/Senha ao tocar em "Testar e salvar" (3 testes automáticos).
- **Séries:** ao abrir uma série, aparece a janela "Escolha o episódio" (T1 E1 · …); o episódio toca e entra no histórico/continuar assistindo.
- **Boas-vindas (pedido da Glaucia, "pode"):** na 1ª vez o app abre em "Bem-vindo ao NUVORA! Como você recebeu o seu acesso?" com Provedor/usuário/senha, Link da lista e Arquivo (só celular); fica até "Pronto! N itens" → "Ir para o Início"; depois sempre abre direto na Home. Trocar/adicionar lista continua em Ajustes. Defeito achado e corrigido: ao salvar, a tela pulava para Ajustes.
- TV: foco começa na primeira opção ao adicionar lista.
- Testes (servidor que imita um provedor Xtream): senha errada, assinatura vencida, login certo ("Pronto! 5 itens"), canal ao vivo tocando, série → escolher episódio → tocar até o fim. Celular, tablet e TV: **0 falhas** na rodada final (154/178/14 verificações OK).
- Base técnica: o formato Xtream não tem documentação oficial; foi implementado pelo padrão público amplamente usado e testado só contra o servidor de teste. **Falta testar com um provedor real.**

### Falta
- QR Code na TV para digitar os dados pelo celular (autorizado).

---

## Decisões
| Data | Decisão |
|---|---|
| 29/10/2026 | Projeto em `C:\Projetos\nuvora` (fora do OneDrive, para não sincronizar arquivos de compilação) |
| 29/10/2026 | Testes reais em celular e tablet da Glaucia (a TV dela é Roku — ver "Compatibilidade de aparelhos") |
| 29/10/2026 | Referência VEXORA recebida (imagem do painel de marca): fundo azul-marinho, azul → violeta, logo em "V" com gradiente, tipografia **Poppins**. Fonte trocada de Manrope para Poppins na proposta |
| 29/10/2026 | Apresentados os modelos (desenhos, não código) da Home do celular e da Home da TV, mais a explicação de leveza. Aguardando opinião da Glaucia |
| 29/10/2026 | Celular e tablet usam o mesmo desenho de Home aprovado pela Glaucia (menu inferior). No tablet a tela mais larga mostra mais cartões por fileira, sem mudar o layout. Ajuste anotado: o rótulo "Ao vivo" quebrou em duas linhas no desenho; no app as categorias ficam numa linha só |
| 29/10/2026 | TV usa o desenho de Home próprio aprovado pela Glaucia: menu lateral, banner, fileiras e item em foco com borda ciano. Mesma identidade visual do celular |
| 29/10/2026 | Regra: **todo aparelho com sistema de TV** (Android TV, Google TV, TV Box) abre o layout de TV; **todo o resto** (celular, tablet, etc.) abre o layout de celular. Escolha automática pelo próprio sistema, sem a pessoa configurar nada |
| 29/10/2026 | Compilação na nuvem (opção A): repositório privado no GitHub + GitHub Actions gerando o APK; no computador, só o `adb` |

## Testes executados
| Data | Teste | Resultado |
|---|---|---|
| 29/10/2026 | Verificação do ambiente (Java, SDK, Studio, adb, RAM, CPU, disco) | Feito — ver 1.1 |
| 31/10/2026 | **Celular real da Glaucia** (APK da Etapa 4) | "Ficou bom". Problema: o APK novo **não instalou por cima** do antigo; foi preciso desinstalar e instalar de novo. Causa: cada compilação na nuvem assina o app com uma chave de teste diferente. Correção **feita** (autorizada em 31/10/2026): chave de teste fixa guardada como segredo do GitHub (`NUVORA_TESTE_CHAVE`/`NUVORA_TESTE_SENHA`). Duas compilações seguidas saíram com a mesma assinatura (SHA-256 `8b878a1b…500d6f`). Desinstalar uma última vez e instalar `apk/nuvora-etapa4-assinatura-fixa.apk`; depois disso, atualiza por cima |
| 29/10/2026 | Login no GitHub (`gh auth status`) | OK — conta `glauciabritobeauty`, permissões de repositório e de automação (workflow) |

Nenhum teste de aplicativo foi executado ainda (não há código).

## Requisitos levantados para a Etapa 5 (ainda não implementados)
- 30/10/2026 — Glaucia perguntou sobre excluir os últimos assistidos. Proposta: "Remover" item a item (Histórico e Continuar assistindo; toque longo no celular, botão de opções na TV) + "Limpar histórico" com confirmação na aba Histórico e em Ajustes. **Autorizado em 30/10/2026 — IMPLEMENTADO na Etapa 5 (31/10/2026) e avisado.**

## Compatibilidade de aparelhos (30/10/2026)
- Funciona: Android TV, Google TV, Chromecast com Google TV, TV Box Android, celulares e tablets Android.
- **Testar na Etapa 9 (autorizado pela Glaucia):** Amazon Fire TV e Fire Stick (Fire OS é baseado em Android; o APK deve rodar, mas ainda não foi testado).
- 31/10/2026 — **Correção:** a TV da Glaucia é **Roku** (não roda o app). Hoje não há aparelho de TV Android real para teste; a TV só foi testada em emulador. Plano da Glaucia: pedir para um familiar com TV Android testar depois. Até lá, TV testada só em emulador. (Alternativa, se precisar: aparelho de teste barato — Fire Stick ou Chromecast com Google TV.) Para o familiar instalar, preparar na Etapa 9 um link do APK e um passo a passo simples.
- Não funciona: Roku, Samsung (Tizen), LG (webOS), Apple TV. App para Roku seria um projeto separado — ideia futura, não está no escopo.
- Computador (31/10/2026, pergunta da Glaucia): fora do escopo atual. Chromebook roda o APK. Windows/Mac: ideia futura — versão de computador reaproveitando parte do código (Compose Multiplatform) ou versão web; seria um projeto à parte. **Preferência da Glaucia: versão para navegador (abrir no Chrome, sem instalar).** Avaliar depois do app Android pronto: listas M3U/servidores costumam usar http e bloqueios de CORS, o que no navegador pode exigir um servidor intermediário — estudar antes de prometer.

## Princípio de produto: simples para todos (31/10/2026)
- Pedido da Glaucia: "seja bem simples, até os senhores que não têm muita facilidade com tecnologia consigam mexer".
- Regras a seguir daqui em diante: textos curtos e claros em português; botões com palavra junto do ícone sempre que couber; poucas escolhas por tela; nada que apague sem confirmar; mensagens que dizem o que fazer; letra grande e foco bem visível na TV.
- Revisar telas existentes com esse olhar (ideias, **aguardando autorização**): rótulos nos botões do player que hoje são só ícone (voltar/avançar 10 s, volume); opção "Letra maior" em Ajustes; teste com uma pessoa mais velha antes da Etapa 10.

## Idioma e legendas (31/10/2026)
- Glaucia confirmou: padrão **português do Brasil**. Ajuste aprovado: pedir primeiro áudio/legenda **pt-BR**, depois qualquer português.
- Acessibilidade (Glaucia: "tem bastante gente com deficiência auditiva"): proposta de opção em Ajustes — Legendas **Automático** (só quando o áudio não é português) / **Sempre ligadas** (prefere legendas para surdos, quando o vídeo tiver) / **Desligadas** — e o app lembra a escolha feita no player. **Aguardando a Glaucia definir o padrão.**
- Pedido da Glaucia (31/10/2026): a pessoa escolhe **no próprio vídeo** se quer legenda ou não. Proposta: botão "Legendas" **sempre visível** no player; se o vídeo não tiver legenda, avisa "Este vídeo não tem legenda disponível na fonte"; canais ao vivo podem ter legenda oculta (closed caption) embutida, que o player já consegue mostrar. Legenda gerada automaticamente (reconhecimento de voz): **descartada pela Glaucia** ("nada de pesar o app") — não fazer.

## Requisitos levantados para a Etapa 7 (ainda não implementados)
- 29/10/2026 — A pessoa pode adicionar **as próprias listas** (o app não vem com nenhuma lista nem conteúdo). Formatos **confirmados pela Glaucia**: link M3U/M3U8, arquivo M3U salvo no aparelho, e login de servidor (endereço + usuário + senha).
- 31/10/2026 — Glaucia: "muitos é com link e muitos são **Provedor / Usuário / Senha**". Na Parte 2, os campos terão exatamente esses nomes (Provedor, Usuário, Senha), como os clientes recebem dos fornecedores.
- Login de servidor: se a pessoa colar um link de lista que já traz usuário e senha dentro, o app preenche servidor/usuário/senha sozinho. Opção futura (só se a Glaucia tiver um fornecedor autorizado próprio, com documentação): servidor já configurado no app, e a pessoa digita só usuário e senha — **decisão pendente**.
- 29/10/2026 — O login da lista **fica salvo** até a pessoa sair: dados criptografados no aparelho (Android Keystore), nunca em texto puro nem em logs; botão "Sair" apaga tudo.
- Aviso no cadastro da lista: a pessoa declara ter direito de acesso ao conteúdo.
- Fluxo previsto (explicado à Glaucia em 31/10/2026): Ajustes → Fontes de conteúdo → Adicionar → escolher tipo (link, arquivo, servidor) → preencher → aviso de direitos → "Testar conexão" → salvar → Home preenche. Várias listas, editar e remover.
- **QR Code na TV — autorizado pela Glaucia em 31/10/2026** ("se não for ficar pesado"), para usuário e senha (e o link, que vem junto): a TV mostra um QR Code que abre uma página no celular (mesmo Wi-Fi, sem servidor externo) para digitar os dados. Regras para não pesar e ser seguro: o mini-servidor só existe com essa tela aberta e fecha ao receber os dados ou em 5 min; código de uso único no QR; nada fica salvo fora do cofre do aparelho. Custo estimado de tamanho: ~100–200 KB no APK final (medir e informar na Etapa 7; se passar disso, avisar antes). Risco a explicar: no Wi-Fi local os dados trafegam sem criptografia durante esses segundos.

## Pendências (dependem da Glaucia)
1. Aprovar (ou ajustar) a arquitetura e as tecnologias da Etapa 1.
2. ~~Escolher o ambiente de compilação~~ — resolvido em 29/10/2026: nuvem (GitHub).
3. ~~Enviar referência VEXORA~~ — recebida em 29/10/2026. Falta aprovar os modelos de Home (celular e TV).
7. ~~Conta no GitHub~~ — resolvido em 29/10/2026: ferramenta GitHub CLI instalada e login feito na conta `glauciabritobeauty`. O repositório privado só será criado no início da Etapa 2, após aprovação.
4. Informar a fonte de conteúdo autorizada (tipo, documentação, formato dos dados) — necessária só na Etapa 7, mas define o que a Home poderá mostrar.
5. Informar modelo e versão do Android da TV, do celular e do tablet (define a versão mínima do Android e os aparelhos de referência).
8. Testar o APK da Etapa 2 no celular, tablet e TV reais e aprovar (ou pedir ajustes) no visual.
9. Identificador do app (`com.nuvora.app`) — pode ser trocado agora sem custo; depois de publicado na Play Store fica permanente.
6. ~~Vídeos públicos de teste~~ — autorizado em 31/10/2026. (Texto original: autorizar o uso de vídeos públicos de teste (ex.: os exemplos oficiais de HLS/DASH do Google), sempre identificados como "Conteúdo de teste".

## Diagnóstico de desempenho (31/10/2026) — aguardando aprovação, nada alterado ainda
Pedido da Glaucia: manter 100% o visual do NUVORA e chegar na rapidez do CasaTV (≈8 MB, roda bem com internet de 8 Mega).

Causas encontradas no código (por ordem de impacto):
1. **Provedor: baixa tudo antes de mostrar qualquer coisa.** `XtreamClient.importAll` baixa a lista completa de canais, filmes e séries (provedores grandes: dezenas de MB) antes de abrir a Home. Com 8 Mega (~1 MB/s) isso leva minutos. Apps leves fazem login (resposta pequena), mostram as categorias e só carregam os itens da categoria aberta.
2. **APKs de teste são "debug"** (16,8 MB, sem R8, depuráveis, sem Baseline Profile). Compose em debug é várias vezes mais lento, principalmente em TV fraca. A versão final (release + R8 + Baseline Profile) deve ficar perto de 6–9 MB e bem mais fluida.
3. **Cifra item por item.** Cada endereço de canal/filme é cifrado separadamente na importação (dezenas de milhares de operações). No provedor, o certo é guardar só o número do canal e cifrar o login uma vez.
4. **Tudo numa transação só:** a Home só aparece no fim. Dá para gravar em lotes e mostrar a Home com as primeiras categorias enquanto o resto chega.
5. **Home faz varredura da tabela inteira a cada abertura** (`loadCatalog` com GROUP BY em todos os itens). Falta índice composto e uma tabela de categorias pronta.
6. **Player:** itens de lista vão sem tipo (mimeType nulo) — endereços sem extensão podem demorar ou falhar; não há reaproveitamento do player para trocar de canal.
7. **Erro do provedor real:** "não encontramos a lista" no modo provedor é mensagem errada; falta plano B (get.php com o mesmo usuário/senha) e falha rápida (timeouts de 20 s/120 s e 3 tentativas com espera).
Observação: muitos apps como o CasaTV vêm com o servidor do revendedor já embutido (por isso pedem só usuário e senha) — hipótese, não verificada.

Plano proposto (visual intocado): A) versão de teste otimizada (release + R8 + Baseline Profile); B) provedor sob demanda (login → categorias → itens por categoria, cache e atualização em segundo plano, plano B get.php, mensagens certas); C) lista por link progressiva e enxuta; D) Home instantânea (índices, categorias prontas, última Home guardada); E) player mais rápido (tipo do vídeo, buffer de ao vivo, troca de canal sem recriar); F) medição com provedor grande simulado e internet limitada a 8 Mega no emulador.

## Otimização — Fase A (versão otimizada) — em andamento (31/10/2026)
Autorizada pela Glaucia com condições: sem redesign, uma etapa por vez, versão de segurança antes, medições antes/depois
(abertura, autenticação, categorias, início do vídeo) com internet de 8 Mega, APK para testar antes da próxima fase,
nenhuma função removida.
- **Versão de segurança:** etiqueta git `seguranca-0.8.1-antes-otimizacao` (código exatamente como estava) e o APK
  `apk/seguranca/nuvora-0.8.1-antes-otimizacao.apk`. Para voltar: reinstalar esse APK (mesma chave) e/ou voltar o código à etiqueta.
- **Estado antes:** 0.8.1 (versionCode 13), APK de teste "debug" de 16,8 MB.
- **O que muda na Fase A:** nova versão de teste "otimizada" (R8, sem modo de depuração, perfis de inicialização das bibliotecas),
  com a mesma chave, a demonstração e as anotações de teste (só tempos e eventos, nunca senhas/endereços). Visual e funções iguais.
- **Medição:** provedor grande simulado (`ci/provedor_grande.py`: 8.000 canais, 30.000 filmes, 6.000 séries) com todo o tráfego
  limitado a 8 Mbit/s e 60 ms de atraso; vídeos locais (HLS e MP4 de 2 Mbit/s). `ci/medir.sh` mede as duas versões no mesmo emulador.
- Os testes de funcionamento (`ci/telas.sh`) passam a rodar na versão otimizada (a que vai para o aparelho).
- **31/10 — resultado parcial (1ª rodada de medição, celular):** APK 16,8 MB → 2,4 MB (com perfis de inicialização dentro);
  1ª abertura 5,9 s → 2,0 s; memória na Home 64 MB → 34 MB; abertura com conteúdo 2,1 s → 2,0 s (ainda sem lista grande).
  Login, conteúdo, Home e início do vídeo NÃO foram medidos: os scripts de teste estavam com quebra de linha do Windows (corrigido).
  Testes de funcionamento da versão otimizada ainda NÃO rodaram pelo mesmo motivo. APK ainda não entregue.
- **Bloqueio:** o GitHub parou de rodar a nuvem ("pagamentos recentes falharam ou limite de gastos precisa ser aumentado"):
  os minutos grátis do mês da conta acabaram. Decisão da Glaucia pendente.

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
- **31/10:** a Glaucia autorizou deixar o repositório PÚBLICO (minutos grátis). Voltar a privado antes de começar a vender. Conferido antes: nenhuma senha, link real ou chave no histórico.
- **31/10 — Fase A concluída (aguardando teste da Glaucia no aparelho).** APK `apk/nuvora-0.9.0-otimizada.apk` (0.9.0, versionCode 14).
  Testes de funcionamento (telas.sh) na versão otimizada: celular, tablet e TV OK, sem travamentos nem erros
  (no celular, 24 toques no menu de baixo foram feitos pela posição — limitação conhecida da leitura de tela do emulador).
  Medições (emulador, provedor grande 44.000 itens, 8 Mbit/s), antes (debug) → depois (otimizada):
  | | celular | TV |
  |---|---|---|
  | APK | 16,8 → 2,4 MB | 16,8 → 2,4 MB |
  | 1ª abertura (tela pronta) | 2,7 → 1,3 s | 2,5 → 1,3 s |
  | abertura com conteúdo | 1,56 → 1,48 s | 1,82 → 1,53 s |
  | login (autenticação) | 0,55 → 0,51 s | 0,09 → 0,07 s |
  | baixar e organizar conteúdo | 22,0 → 20,5 s | 21,1 → 20,1 s |
  | montar a Home (categorias) | 46 → 22 ms | 84 → 52 ms |
  | memória na Home | 80 → 50 MB | 81 → 51 MB |
  | 1º quadro do canal ao vivo | 4,3 → 2,2 s | 2,3 → 1,0 s |
  | 1º quadro do filme (MP4) | 9,1 → 8,2 s | — |
  Conclusões: a Fase A deixou o app menor, mais leve e abre/começa o canal mais rápido; a espera do conteúdo (~20 s, quase toda
  download de 17,5 MB a 8 Mega) só cai com a Fase B. Filme MP4 demorando ~8 s → investigar na Fase E.
  Fluidez da rolagem: o emulador da nuvem desenha por software (sem placa de vídeo), então a medição não é confiável
  (celular não registrou quadros; TV ~99% lentos nas duas versões). Fluidez real será conferida no aparelho da Glaucia.

## Correção de rumo (31/10/2026): entrada, login com provedor e carregamento — diagnóstico, sem código ainda
Pedido da Glaucia: tela de entrada inspirada no CasaTV (organização do lado direito), login com provedor funcionando de verdade, Home sem esperar a lista inteira. Visual do NUVORA preservado.

Por que as correções anteriores não resolveram:
- Login: só mudei mensagens e acrescentei o "detalhe técnico"; nunca vi uma resposta real do provedor dela. Os testes foram contra o servidor de teste, que segue o padrão Xtream clássico.
- Carregamento: a Fase A foi só a compilação otimizada; a mudança de carregamento (Fase B) ainda não tinha sido feita. O código ainda baixa get_live_streams, get_vod_streams e get_series inteiros, numa única transação, antes de liberar o "Pronto!".

Teste no servidor real dela, SEM usuário/senha (só dados falsos), em 31/10/2026:
- O servidor NÃO é um painel Xtream clássico: é um intermediário (nginx/1.27.1) que responde em JSON próprio.
- player_api.php com usuário/senha inválidos → HTTP 400 {"error":"Bad Request"} (o Xtream clássico responde 200 com auth=0). Sem parâmetros → HTTP 422.
- get.php com dados inválidos → HTTP 404 (vira "Não encontramos a lista").
- /live/x/y/1.m3u8 e xmltv.php → HTTP 400. Raiz → 307 para google.com. https não responde.
- Conclusão: o NUVORA trata 400/422 como "erro genérico" e 404 como "não encontrado", então qualquer recusa desse servidor vira mensagem errada. A resposta com os dados verdadeiros só pode ser vista no aparelho dela.

Plano aprovado aguardando "Pode avançar":
1. Relatório de conexão no app (sem senha): cada passo, código HTTP, tipo e formato da resposta, redirecionamentos e tempo; a Glaucia copia e me envia.
2. Login entende os formatos de erro desse servidor (400/422 JSON) e tenta o plano B (get.php) se o player_api falhar.
3. Carregamento sob demanda no provedor: login → categorias (segundos) → itens de cada categoria só quando abrir/rolar, guardados no aparelho e atualizados em segundo plano; Home abre com o cache.
4. Link M3U: ler enquanto baixa e gravar em lotes; Home liberada com os primeiros itens; o resto continua em segundo plano com aviso discreto.
5. Nova tela de entrada: marca à esquerda e formulário à direita (TV/deitado), marca em cima no celular em pé; escolha do tipo por botões no topo do formulário; sem campo "Nome" (nome automático); botão "Entrar".
6. Teste em emulador com um servidor que imita esse provedor (400/422/404) + provedor grande a 8 Mega; APK para ela confirmar no aparelho.

## Etapa 1 da correção: login com provedor + relatório de conexão (v0.9.1) — concluída nos testes, aguardando teste no aparelho
Versão de segurança: tag `seguranca-0.9.0-antes-login` + `apk/seguranca/nuvora-0.9.0-otimizada.apk`.

Alterado:
- Relatório de conexão (ConnectionReport.kt): cada pedido (endereço com usuário/senha/tokens trocados por •••), código HTTP, tipo, tamanho, tempo, formato do conteúdo (só campos não pessoais), tipo do problema e conclusão. Botões "Ver relatório de conexão" e "Copiar relatório". Testes automáticos garantem que usuário e senha não aparecem.
- Tipos de problema separados: Rede, Autenticação, Formato do pedido, Resposta do servidor, Carregamento da lista.
- Login (XtreamClient.connect): 1) player_api; 2) se 400/405/422, o mesmo em POST; 3) plano B get.php (m3u_plus/hls e m3u_plus). Se a lista vier, usuário e senha estão certos e a lista é importada pelo link.
- Erros novos: ServerNotFound, NotRecognized (400/422 + 404 → autenticação provável), RequestRejected.
- Campos de usuário/senha/endereço sem corretor automático e sem maiúscula automática; espaços no começo/fim removidos (causa comum de "senha errada" no celular).
- Nome da lista automático (endereço do provedor) se ficar vazio.
- TV: a mensagem de resultado aparece na tela e o foco vai para o botão logo abaixo; o relatório rola com as setas.
- Recursos do provedor: o relatório diz se o provedor aceita pedir canais de uma categoria só (base da etapa 2).

Testes (CI, emulador, APK otimizada 0.9.1), servidor `ci/provedor_intermediario.py` que responde igual ao provedor real (422/400 "Bad Request", get.php 404, raiz 307, "nginx/1.27.1"):
- Celular: 45 ok, 0 falhas. TV: 20 ok, 0 falhas. 0 erros registrados.
- Cenário 1 (dados não reconhecidos): mensagem certa, "Autenticação", relatório com 5 passos, senha/usuário fora da tela e das anotações.
- Cenário 2 (só get.php funciona): 25 itens, Home com categorias, canal tocando (vídeo andou).
- Cenário 3 (padrão funciona): 25 itens, consulta por categoria "aceita" (5 de 20), Home, canal tocando.
- Regressão (telas.sh): celular/tablet/TV sem travar e sem erros; celular com as 24 falhas conhecidas do menu de baixo (recuperadas pela posição).
Ainda não verificado: o provedor real com os dados verdadeiros (só no aparelho da Glaucia, pelo relatório).

## Nova tela de entrada (v0.9.2) — aprovada pela Glaucia em prévia, testada no emulador, aguardando teste no aparelho
Versão de segurança: tag `seguranca-0.9.1-antes-entrada` + `apk/seguranca/nuvora-0.9.1-login.apk`.
- Entrada direta (estilo CasaTV, visual NUVORA): logo, "Entre com seu usuário e senha", USUÁRIO, SENHA com olho, "+ Usar outro servidor (DNS)", ENTRAR largo; "Outras formas: Link da lista · Arquivo M3U" discreto (arquivo só no celular).
- Sem servidor padrão inventado: ENTRAR sem servidor abre o campo Servidor (DNS) com explicação. Servidor do último login que deu certo fica salvo cifrado ("Servidor: x · Trocar").
- Link inteiro colado em Usuário ou Servidor é separado sozinho. Confirmação de direito virou aviso embaixo do ENTRAR. Nome da lista automático.
- Telas deitadas/tablet/TV: marca à esquerda, formulário à direita. TV: foco começa em USUÁRIO; olho é um botão ao lado (seta para a direita); teclado: Próximo/Ir.
- Mesmo mecanismo de login da etapa anterior (relatório, plano B).
Testes CI (APK otimizada 0.9.2): login celular 42 ok / TV 20 ok, 0 falhas; telas tablet 154 ok, TV 0 falhas, celular só as 24 conhecidas do menu de baixo; 0 erros registrados. Numa rodada intermediária o tablet teve 1 falha passageira ("vídeo não andou depois que a internet voltou") que não se repetiu.

## Etapa 2: tela de escolha + carregamento sob demanda (v0.9.3) — testada no emulador, aguardando teste no aparelho
Versão de segurança: tag `seguranca-0.9.2-antes-carregamento` + `apk/seguranca/nuvora-0.9.2-entrada.apk`.
- Login confere a senha e busca só as categorias (3 pedidos pequenos) + a 1ª categoria da TV; vai direto para a tela de escolha (sem "Pronto!").
- Tela de escolha (estilo CasaTV, visual NUVORA): "Olá!" + usuário · vencimento · telas; blocos TV ao vivo / Filmes / Séries; Atualizar e Sair da conta; aviso honesto "carregando em segundo plano: X de Y categorias".
- Dentro de cada parte (referência mandada pela Glaucia): voltar, título, busca da parte, categorias em linha (★ Favoritos, Todos, ...), capas 3 por linha com nome e nota ★ (filmes/séries) ou lista número+logo+nome (TV ao vivo).
- Categoria aberta é buscada na hora (passa na frente); o resto carrega em segundo plano (TV, depois filmes, depois séries). Se o provedor ignorar o filtro por categoria, aproveita a parte inteira. Plano B (só lista completa): lida direto da conexão em lotes de 2.000, com a tela já liberada.
- Banco v2 (categorias, parte, nota, conta, completa); listas antigas migradas. Downloads com arquivo temporário próprio.
- TV: "Categorias" saiu do menu (a tela de escolha leva às categorias).

Medição a 8 Mega (emulador, provedor grande: 180 categorias, 44.000 itens), versão otimizada:
| | Celular | TV |
|---|---|---|
| Antes (Fase A): esperar tudo depois da senha | 20,5 s | 20,1 s |
| Agora: senha + categorias (anotação do app) | 0,94 s | 0,51 s |
| Agora: tocar ENTRAR até ver a tela de escolha (cronômetro do teste, lê a tela a cada ~5 s) | ≤ 6 s | ≤ 3 s |
| Abrir TV ao vivo até ver os canais | 2 s | 2 s |
| Lista inteira no aparelho (em segundo plano, sem travar) | 42 s | 38 s |
| Memória na tela de escolha | 33 MB (antes 50) | 34 MB (antes 51) |
| Canal começar | 3,0 s (antes 2,2) | 2,3 s (antes 1,0) |
Canal um pouco mais lento que na Fase A: a investigar na Fase E (player). O filme pela busca não foi medido nesta rodada (o roteiro não achou a aba Buscar no emulador).
Testes: login celular 47 ok / TV 22 ok, 0 falhas; telas tablet 154 ok, TV 0 falhas, celular só as falhas conhecidas do menu de baixo (recuperadas pela posição); 0 erros registrados.

## Fluidez (v0.9.5) — resposta a "tudo demora" no aparelho da Glaucia; aguardando o relatório do aparelho dela
Versão de segurança: tag `seguranca-0.9.3-antes-fluidez` + `apk/seguranca/nuvora-0.9.3-escolha.apk`.
Causas encontradas no código e corrigidas:
- Banco sem WAL: toda leitura (abrir categoria, voltar, tela de escolha) esperava as gravações do segundo plano → WAL ligado; gravação em lotes de 1.000; rede e cifragem fora da trava.
- Segundo plano usando a internet sem parar (disputa com categoria aberta, logos e vídeo; provedor de 1 tela) → agora pausa enquanto vídeo toca ou categoria está sendo aberta, respira 250 ms entre pedidos e roda em linha de baixa prioridade.
- Voltar do vídeo remontava a lista lendo o banco → memória das telas de cada parte.
- Abrir o app esperava a amostra da Home antiga → não espera mais. Login não busca mais a 1ª categoria.
- "Todos os canais" limitado a 5.000 → sem limite prático.
- Relatório de conexão guardado em Ajustes, com "Desempenho neste aparelho" (abrir o app, cada parte/categoria — do aparelho ou do provedor — e início de cada vídeo).
Medição a 8 Mega (emulador, 180 categorias / 44.000 itens), cronômetro do app (anotação) e do teste (lê a tela, ≈1 s por leitura):
| | Celular | TV |
|---|---|---|
| Senha + categorias (app) | 0,83 s | 0,40 s |
| Tocar ENTRAR até ver a tela de escolha (teste, inclui ~1-3 s das leituras de tela) | 5,7 s | 3,1 s |
| Abrir TV ao vivo até ver os canais, com o segundo plano ainda carregando | ≤ 1,6 s | ≤ 1,6 s |
| Voltar do canal para a lista, com o segundo plano carregando | ≤ 1,8 s | ≤ 1,6 s |
| Lista inteira no aparelho (segundo plano educado) | 96 s (antes 42) | 91 s (antes 38) |
| Canal começar | 3,6 s | 1,4 s |
Testes: login 47/22 ok sem falhas; telas tablet e TV sem falhas, celular só as conhecidas do menu de baixo; 0 erros. Filme pela busca segue sem medição (roteiro não acha a aba Buscar no emulador).

### Primeiro relatório do aparelho real (01/11/2026, 0.9.5, Android 16, celular) — "melhorou bastante"
- Provedor real ((provedor A)) funciona no jeito padrão: login 368 ms (https redirecionado para http), categorias 52/41/30 em 0,2–0,3 s cada; aceita pedir por categoria (42 itens em 518 ms).
- Leituras no aparelho: 1–50 ms (abrir parte, categoria já baixada, "Todos").
- Conta de teste (is_trial=1) vence em 01/11/2026.
- Ajuste para a próxima rodada: o cronômetro anota "N categorias em X ms" a cada lote que chega (polui o relatório e empurra para fora as linhas de abrir o app e de vídeo) → anotar só a 1ª abertura de cada parte.

## Fase E (parte 1): vídeo começando mais rápido (v0.9.6) — testada no emulador, aguardando cronômetro do aparelho
Versão de segurança: tag `seguranca-0.9.5-antes-player` + `apk/seguranca/nuvora-0.9.5-fluidez.apk`.
- Provedor que desvia https → http no mesmo site (o da Glaucia faz isso): o endereço final é guardado e usado direto em pedidos e vídeos (sem desvio nem aperto de mão de segurança). Vale para quem entrar de novo ou tocar em Atualizar.
- Tipo do vídeo pelo endereço (m3u8/mp4/mkv/ts/mpd); começa com 1 s guardado (antes 1,5 s); canais .ts podem começar sem esperar o quadro inicial.
- Cronômetro: cada parte só uma vez por sessão; vídeo anota tipo (canal/filme/episódio) e http/https.
- Servidor de teste agora atende Range (como os reais): a medição anterior do filme (8 s) era do teste, que mandava o arquivo inteiro ao continuar de onde parou.
Medição a 8 Mega (3 aberturas cada, emulador):
| | Celular | TV |
|---|---|---|
| Filme começar | 1,9 / 3,3 / 2,3 s (antes ~8 s) | 0,7 / 1,3 / 1,5 s |
| Canal (HLS) começar | 3,1 / 4,7 / 5,5 s | 3,0 / 3,0 / 1,8 s |
Canal no emulador varia muito entre rodadas (na rodada anterior TV 3,8 / 1,0 / 0,8 s); precisa do cronômetro do aparelho real para decidir o próximo passo (ex.: manter o player aberto ao trocar de canal).
Testes: login 47/22 ok; telas sem falhas novas; 0 erros.

### Cronômetro do aparelho real (01/11/2026 16:26, 0.9.6, Android 16, celular)
- Abrir o app até a tela de escolha: 0,30 s. Login 1,06 s (o servidor salvo ainda é o https digitado; a 1ª ida desvia para http) + categorias 0,15–0,31 s.
- Categoria de filmes buscada no provedor: 0,41 s (448 itens). Categoria já no aparelho: 23 ms (1.067 séries). "Todos" da TV: 31 ms (906 canais).
- Vídeo: canal 0,88 s; episódio 1,33 s; filmes 2,45 s e 3,59 s.
- Ideia pequena para depois: guardar como "servidor salvo" o endereço final (http) para o login também pular o desvio (~0,5–0,7 s).

## Link M3U rápido (v0.9.7) — testada no emulador
Versão de segurança: tag `seguranca-0.9.6-antes-link` + `apk/seguranca/nuvora-0.9.6-player.apk`.
- Link de provedor (get.php com usuário e senha dentro): entra pelo caminho rápido do Provedor/Usuário/Senha (categorias primeiro).
- Outros links M3U: confere só o começo (erros como 404/456 continuam aparecendo na hora), vai direto para a tela de escolha e lê a lista aos poucos por trás (lotes; recomeça se a conexão cair). Atualizar também é por trás. Segundo plano atende uma lista por vez, incluindo as adicionadas no meio do caminho.
- Servidor errado ou sem porta (ex.: DNS de STB que a Glaucia tentou): mensagem "confira o servidor (DNS) e a porta" em vez de "não aceitou o pedido".
- Relatório real (provedor B, link M3U colado no servidor na 0.9.6): login 0,35 s, categorias 0,3–0,5 s; canal 2,4–2,8 s; filme 4,5–6,2 s (reabrindo 8 s); episódio 5,7 s (reabrindo 8,7 s). Vídeos deste provedor saem de outro endereço (server_info (servidor de vídeo do provedor B)). Próximo: cronômetro detalhado do vídeo + "desviar uma vez só".
Testes: login 47/22 ok; telas tablet 162 ok sem falhas, TV sem falhas, celular só as conhecidas do menu de baixo; link de provedor e listas difíceis ok; 0 erros. DNS de "STB / Smart UP" não é compatível (outro sistema, por MAC).

## Fase E (parte 2): vídeo (v0.9.8) — testada no emulador, aguardando cronômetro do aparelho
Versão de segurança: tag `seguranca-0.9.7-antes-video` + `apk/seguranca/nuvora-0.9.7-link.apk`.
- Filme/episódio: desvio do provedor feito uma vez (pedido de 1 byte, até 4 s) e o player recebe o endereço final; sites sem desvio ficam lembrados. Canais ao vivo de fora (endereço final costuma expirar).
- Servidor salvo = endereço que vale de verdade (login sem o desvio https → http).
- Cronômetro: 1º quadro uma vez só por vídeo (o "começou 5 vezes" do episódio era o aviso repetido a cada pulo/carregamento); ao fechar: travadas, tempo parado, pulos e tempo assistido.
Medição (emulador, provedor de teste SEM desvio — o ganho do desvio só aparece em provedor real): filme celular 1,7/3,3/3,0 s, TV 0,9/1,0/1,0 s; canal celular 6,3/5,0/5,3 s (emulador instável; no aparelho real o canal deu 0,8–1,3 s), TV 3,5/1,2/1,3 s. APK 2,5 MB; memória 35–37 MB.
Testes: login 47/22 ok; telas sem falhas novas; 0 erros.

## Tela de detalhes (v0.9.9) — testada no emulador, aguardando aparelho
Versão de segurança: tag `seguranca-0.9.8-antes-detalhes` + `apk/seguranca/nuvora-0.9.8-video.apk`.
- Teste da Glaucia: mesmo filme do provedor B levou 13 s no CasaTV (NUVORA 9,6 s) → a demora é do servidor do provedor. O CasaTV mostra uma tela de detalhes antes do filme (ideia aprovada em prévia).
- Filmes/séries abertos pelas categorias passam pela tela de detalhes: capa, ano, duração, nota, gênero, sinopse, direção, elenco (get_vod_info/get_series_info; só o que o provedor mandar), Assistir e Favoritar; série com temporadas e episódios na tela. Canais abrem direto.
- Enquanto os detalhes estão abertos, o desvio do provedor é resolvido e o player é preparado sem tocar (PlayerWarmup); Assistir usa o player já pronto; ao voltar do vídeo prepara de novo; sair dos detalhes libera.
- Cronômetro: filme com desvio não aparece mais como "canal".
Medição a 8 Mega (emulador): filme pela tela de detalhes após 3 s "lendo": 0,93 s celular / 0,49 s TV (antes 1,7–3,3 s / 0,9–1,0 s). Testes: login 49/22 ok, telas sem falhas novas, 0 erros; print da tela de detalhes conferido.

### Cronômetro real da 0.9.9 (01/11/2026 21:00, celular Android 16)
- provedor B (servidor de vídeo lento): filmes pela tela de detalhes 4,5 s e 2,4 s (antes 4,3–9,6 s; CasaTV 13 s); canal 1,9 s; nenhum vídeo travou.
- provedor C (o provedor que falhava no começo do projeto) agora entra e toca: canal 1,1 s; filmes 1,1 s e 1,0 s; episódio 1,4 s; categoria nova do provedor 0,4–1,0 s.
- Abrir o app até a tela de escolha: 0,33 s.

### Revisão de segurança (Etapa 9, 01/11/2026)
- Nenhum usuário ou senha da Glaucia está nos arquivos do projeto nem no histórico do GitHub (conferido por busca). Pastas `captures/` e `apk/` ficam fora do GitHub.
- Os endereços dos provedores reais apareciam neste arquivo; foram trocados por "provedor A/B/C" daqui em diante (versões antigas do arquivo ainda os têm no histórico: o projeto volta a ser privado na Etapa 10).
- Versão final (release): anotações de teste desligadas (`nuvora_diagnostico=false`); o relatório de conexão nunca guarda usuário, senha ou tokens.

# NUVORA — Registro de progresso

Arquivo vivo: decisões, etapas aprovadas, testes e pendências. Atualizado a cada entrega.

## Regras de trabalho (combinadas em 29/10/2026)
- Uma etapa por vez. Só avanço quando a Glaucia responder expressamente que podemos avançar.
- Cada entrega segue o formato: Etapa atual · Objetivo · O que foi implementado · Arquivos criados ou alterados · Testes executados · Resultado · Pendências · Próxima etapa prevista.
- Nada é declarado "funcionando" sem teste real. Problemas são explicados com causa e proposta antes de continuar.
- Sem refatorações desnecessárias, sem funcionalidades fora do escopo sem autorização, sem alterar partes aprovadas sem avisar.
- Só fontes de conteúdo autorizadas. Nada de APIs, servidores ou credenciais inventados; nada que contorne DRM.

## Onde paramos (30/10/2026, noite)
- Etapa 4 (Home da TV) **entregue**: a última rodada de testes passou (detalhes na seção da Etapa 4). Falta a Glaucia aprovar.
- Ao retomar: apresentar a entrega da Etapa 4 no formato combinado e pedir aprovação. APK: `apk/nuvora-etapa4-assinatura-fixa.apk`.

## Situação das etapas
| Etapa | Descrição | Situação |
|---|---|---|
| 1 | Análise do ambiente, arquitetura e estrutura | **Aprovada** em 29/10/2026 ("Pode avançar") |
| 2 | Identidade visual e componentes reutilizáveis | **Aprovada** em 29/10/2026 ("Pode avançar"). Teste nos aparelhos reais ainda não relatado |
| 3 | Home para celular | **Aprovada** em 30/10/2026 ("Pode avançar"). Teste nos aparelhos reais ainda não relatado |
| 4 | Home específica para Android TV | Entregue em 30/10/2026 — **aguardando aprovação** |
| 5 | Navegação e gerenciamento de estado | Não iniciada |
| 6 | Player (Media3) | Não iniciada |
| 7 | Integração com fontes autorizadas | Não iniciada |
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
- Problema encontrado e **ainda aberto**: no emulador de celular, a "leitura da tela" usada pelos leitores de acessibilidade (TalkBack) informa o menu inferior com tamanho zero; no tablet está correto. No teste automático, as abas do celular foram tocadas pela posição. Causa ainda não identificada — investigar com o TalkBack ligado (Etapa 9 ou antes).
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
- 30/10/2026 — Glaucia perguntou sobre excluir os últimos assistidos. Proposta: "Remover" item a item (Histórico e Continuar assistindo; toque longo no celular, botão de opções na TV) + "Limpar histórico" com confirmação na aba Histórico e em Ajustes. **Autorizado pela Glaucia em 30/10/2026 — incluir obrigatoriamente na Etapa 5 e avisá-la quando for implementado.**

## Compatibilidade de aparelhos (30/10/2026)
- Funciona: Android TV, Google TV, Chromecast com Google TV, TV Box Android, celulares e tablets Android.
- **Testar na Etapa 9 (autorizado pela Glaucia):** Amazon Fire TV e Fire Stick (Fire OS é baseado em Android; o APK deve rodar, mas ainda não foi testado).
- 31/10/2026 — **Correção:** a TV da Glaucia é **Roku** (não roda o app). Hoje não há aparelho de TV Android real para teste; a TV só foi testada em emulador. Plano da Glaucia: pedir para um familiar com TV Android testar depois. Até lá, TV testada só em emulador. (Alternativa, se precisar: aparelho de teste barato — Fire Stick ou Chromecast com Google TV.) Para o familiar instalar, preparar na Etapa 9 um link do APK e um passo a passo simples.
- Não funciona: Roku, Samsung (Tizen), LG (webOS), Apple TV. App para Roku seria um projeto separado — ideia futura, não está no escopo.
- Computador (31/10/2026, pergunta da Glaucia): fora do escopo atual. Chromebook roda o APK. Windows/Mac: ideia futura — versão de computador reaproveitando parte do código (Compose Multiplatform) ou versão web; seria um projeto à parte. **Preferência da Glaucia: versão para navegador (abrir no Chrome, sem instalar).** Avaliar depois do app Android pronto: listas M3U/servidores costumam usar http e bloqueios de CORS, o que no navegador pode exigir um servidor intermediário — estudar antes de prometer.

## Requisitos levantados para a Etapa 7 (ainda não implementados)
- 29/10/2026 — A pessoa pode adicionar **as próprias listas** (o app não vem com nenhuma lista nem conteúdo). Formatos **confirmados pela Glaucia**: link M3U/M3U8, arquivo M3U salvo no aparelho, e login de servidor (endereço + usuário + senha).
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
6. Para testes do player antes da fonte real: autorizar o uso de vídeos públicos de teste (ex.: os exemplos oficiais de HLS/DASH do Google), sempre identificados como "Conteúdo de teste".

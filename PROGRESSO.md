# NUVORA — Registro de progresso

Arquivo vivo: decisões, etapas aprovadas, testes e pendências. Atualizado a cada entrega.

## Regras de trabalho (combinadas em 29/10/2026)
- Uma etapa por vez. Só avanço quando a Glaucia responder expressamente que podemos avançar.
- Cada entrega segue o formato: Etapa atual · Objetivo · O que foi implementado · Arquivos criados ou alterados · Testes executados · Resultado · Pendências · Próxima etapa prevista.
- Nada é declarado "funcionando" sem teste real. Problemas são explicados com causa e proposta antes de continuar.
- Sem refatorações desnecessárias, sem funcionalidades fora do escopo sem autorização, sem alterar partes aprovadas sem avisar.
- Só fontes de conteúdo autorizadas. Nada de APIs, servidores ou credenciais inventados; nada que contorne DRM.

## Situação das etapas
| Etapa | Descrição | Situação |
|---|---|---|
| 1 | Análise do ambiente, arquitetura e estrutura | **Aprovada** em 29/10/2026 ("Pode avançar") |
| 2 | Identidade visual e componentes reutilizáveis | Em andamento (iniciada em 29/10/2026) |
| 3 | Home para celular | Não iniciada |
| 4 | Home específica para Android TV | Não iniciada |
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

## Decisões
| Data | Decisão |
|---|---|
| 29/10/2026 | Projeto em `C:\Projetos\nuvora` (fora do OneDrive, para não sincronizar arquivos de compilação) |
| 29/10/2026 | Testes reais em celular, tablet e Android TV / Google TV da Glaucia |
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
| 29/10/2026 | Login no GitHub (`gh auth status`) | OK — conta `glauciabritobeauty`, permissões de repositório e de automação (workflow) |

Nenhum teste de aplicativo foi executado ainda (não há código).

## Pendências (dependem da Glaucia)
1. Aprovar (ou ajustar) a arquitetura e as tecnologias da Etapa 1.
2. ~~Escolher o ambiente de compilação~~ — resolvido em 29/10/2026: nuvem (GitHub).
3. ~~Enviar referência VEXORA~~ — recebida em 29/10/2026. Falta aprovar os modelos de Home (celular e TV).
7. ~~Conta no GitHub~~ — resolvido em 29/10/2026: ferramenta GitHub CLI instalada e login feito na conta `glauciabritobeauty`. O repositório privado só será criado no início da Etapa 2, após aprovação.
4. Informar a fonte de conteúdo autorizada (tipo, documentação, formato dos dados) — necessária só na Etapa 7, mas define o que a Home poderá mostrar.
5. Informar modelo e versão do Android da TV, do celular e do tablet (define a versão mínima do Android e os aparelhos de referência).
6. Para testes do player antes da fonte real: autorizar o uso de vídeos públicos de teste (ex.: os exemplos oficiais de HLS/DASH do Google), sempre identificados como "Conteúdo de teste".

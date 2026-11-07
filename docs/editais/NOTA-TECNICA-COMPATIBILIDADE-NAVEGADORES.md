# Nota técnica — Compatibilidade de navegadores, gatilhos e linguagem

**Kit de edital — Ziip SAN** · Tarefa **ED-08** (origem 18h-E12) · Depende de P0.22 (busca e listas, paginação keyset) e P1.12 (frontend robusto), já entregues.

Esta nota é a evidência técnica da **compatibilidade de navegadores** para a **alínea a do roteiro VISAN (itens POC-01 a POC-03)**. O conteúdo de cada item POC é o do roteiro VISAN vigente, que esta nota **não reproduz**: ela registra o que o produto faz, como o CI prova e como a demonstração se repete diante do avaliador.

> **Inauguração do kit:** até esta entrega não havia uma pasta de kit de edital no repositório. Esta nota inaugura `docs/editais/` como o local do kit; as próximas notas de edital entram na mesma pasta.

---

## 1. Matriz de navegadores

| Alvo do edital | Motor | Projeto Playwright | Como executa | Evidência por execução |
|---|---|---|---|---|
| Microsoft Edge 88 | Chromium | `edge-88` | Canal `msedge` do Playwright (Edge instalado), ou executável fixo via `E2E_EDGE_EXECUTABLE` | Relatório do job `e2e-navegadores` + anexos `user-agent.txt` e `browser-version.txt` do spec |
| Google Chrome 100 | Chromium | `chrome-100` | Canal `chrome` do Playwright (Chrome instalado), ou executável fixo via `E2E_CHROME_EXECUTABLE` | Idem |
| Mozilla Firefox 116 | Gecko | `firefox-116` | Firefox do Playwright, ou executável fixo via `E2E_FIREFOX_EXECUTABLE` | Idem |
| (referência interna) | Chromium | `chromium` | Build Chromium do Playwright — é o projeto que a suíte E2E já usava antes da ED-08 | Idem |

Configuração: `frontend/e2e/playwright.config.ts`. Spec da matriz: `frontend/e2e/navegadores.spec.ts`. Execução no CI: job **`e2e-navegadores`** em `.github/workflows/ci.yml` (compila o frontend, instala os navegadores dos canais, serve o build com `vite preview` e roda `npm run test:matriz` no pacote `frontend/e2e`).

### 1.1 O que a matriz prova

Em **cada** projeto, sem login e só com o frontend servido, o spec prova que:

1. a tela de entrada da aplicação **carrega e renderiza** naquele motor (o botão "Entrar" fica visível);
2. **não há erro de script não tratado** (`pageerror`) durante o carregamento — o sintoma clássico de o pacote usar recurso de linguagem acima do que o navegador entende;
3. o motor executado é o da **família do projeto** (token do user-agent: `Edg/`, `Chrome/`, `Firefox/`);
4. as **APIs do piso de linguagem** da seção 3 existem naquele motor.

Com usuário de teste e a stack completa, os specs pré-existentes (`bff.spec.ts`, da P0.14, e `version-banner.spec.ts`, da P0.39) rodam nos mesmos projetos — a matriz cobre então também login, sessão BFF e aviso de versão nos três navegadores-alvo.

### 1.2 Divergência declarada: versão-alvo × build executado

O Playwright **não distribui binários históricos**: seus canais entregam os builds atuais de Chromium, Firefox, Chrome e Edge, e não existe arquivo oficial automatizável do Edge 88 (2021) nem do Chrome 100 (2022) para baixar no CI. Afirmar "testado no Edge 88" seria falso.

O que a matriz faz, honestamente:

- o **nome do projeto é o alvo do edital**; o **build real** de cada execução é registrado nos anexos do relatório (`user-agent.txt`, `browser-version.txt`), conferível pelo avaliador;
- se um build da versão-alvo compatível com a automação for provisionado na máquina ou no CI, ele é usado sem mudar o spec: basta apontar `E2E_EDGE_EXECUTABLE`, `E2E_CHROME_EXECUTABLE` ou `E2E_FIREFOX_EXECUTABLE`;
- a compatibilidade com o alvo antigo é defendida pela combinação de **família do motor** (os três projetos) + **piso de linguagem** (seção 3, com teste automático por navegador) + **nenhum erro de script** no carregamento.

O item do aceite "matriz verde no CI" se comprova na **rodada do job `e2e-navegadores`**; até a primeira rodada verde, a matriz está **pronta e configurada, não comprovada** (ver seção 6).

---

## 2. Gatilhos de revalidação

A compatibilidade declarada nesta nota vale enquanto nenhum gatilho abaixo disparar. Disparado um gatilho, a matriz roda de novo e esta nota é revisada **antes** de a compatibilidade voltar a ser afirmada em edital:

| # | Gatilho | Por quê |
|---|---|---|
| G1 | Edital novo com piso de navegador **maior** que Edge 88 / Chrome 100 / Firefox 116 | O alvo muda; projetos e piso de linguagem são revistos |
| G2 | Adoção, no código do frontend, de API ou sintaxe **acima do piso** da seção 3 (ex.: `Array.prototype.at`, `structuredClone`, top-level await) | O pacote pode parar de carregar no navegador mais antigo sem erro em desenvolvimento |
| G3 | Mudança do alvo de build do Vite ou troca de framework/biblioteca principal do frontend | O nível de linguagem do pacote gerado muda junto |
| G4 | Atualização major do Playwright ou dos canais de navegador no CI | O build executado em cada projeto muda; a evidência anterior não vale para o novo build |
| G5 | Falha de qualquer projeto da matriz no CI | A compatibilidade está refutada até a causa ser corrigida e a matriz voltar a verde |
| G6 | Inclusão de um quarto navegador em edital (ex.: Safari) | A matriz não cobre o que não executa |
| G7 | Mudança em cookie de sessão, CSP ou fluxo de login (BFF, P0.14/P0.16) | Comportamento difere por motor; os specs do BFF entram na revalidação |

---

## 3. Linguagem

### 3.1 Piso de linguagem de programação (JavaScript e CSS)

O navegador mais antigo da matriz é o **Edge 88** (Chromium 88, 2021); o Firefox 116 (2023) e o Chrome 100 (2022) são posteriores. O piso é, portanto, o do Chromium 88:

- **Seguro no piso** (presente nos três alvos e conferido pelo spec da matriz): `fetch`, `Promise.allSettled`, `String.prototype.replaceAll`, `IntersectionObserver`, `ResizeObserver`, CSS grid e propriedades customizadas (variáveis CSS). Optional chaining e nullish coalescing (ES2020) também são anteriores ao piso.
- **Acima do piso — proibido sem disparar o gatilho G2**: `Array.prototype.at` (Chrome 92), `Object.hasOwn` (Chrome 93), top-level await em módulos (Chrome 89), `structuredClone` (Chrome 98 — existe no Chrome 100, **não** no Edge 88). Quem precisar de um deles usa detecção de recurso com alternativa, polyfill avaliado, ou sobe o piso com revisão desta nota.
- O `vite.config.ts` atual **não fixa** um `build.target` abaixo do padrão do Vite: o piso é defendido pela matriz e pelo teste de APIs, não pela transpilação. Se um edital exigir o binário histórico, fixar o alvo de build passa a fazer parte da resposta ao gatilho G1/G3.

### 3.2 Linguagem da interface

- A interface opera em **português brasileiro**: rótulos, mensagens e títulos de problema seguem o padrão do produto (os códigos de problema da API são em inglês por contrato, P0.33; `title`, `detail` e mensagens ao usuário são em português).
- Os textos novos desta entrega seguem o mesmo padrão: "Itens por página", "Ir para página" e o aviso "Digite uma página entre 1 e N." na paginação; nenhuma mensagem nova foi criada em outro idioma.

---

## 4. Paginação keyset: seletor de tamanho e "ir para página"

A P0.22 entregou a **paginação keyset (cursor)** nas listas grandes do servidor. A ED-08 completa a parte de interface:

- **Seletor de tamanho de página** no componente `frontend/src/components/ui/Pagination.tsx`: opções **10, 25, 50, 100** — o teto de 100 é o do servidor (P0.16: `size` máximo nas listas; padrão 25). O seletor aparece nas listas que o ligam (nesta entrega: `CustomerList` — clientes — e `CatalogCrud` — catálogos e hierarquia); mudar o tamanho volta à primeira página.
- **"Ir para página"** no mesmo componente: campo numérico + botão "Ir" (ou Enter). Página fora do intervalo **não navega** e avisa o intervalo válido no lugar — nunca pede ao servidor uma página inexistente.
- **Sobre keyset, "ir para página" NÃO é offset.** A API keyset não aceita salto direto: o navegador de cursores em `frontend/src/utils/keysetPagination.ts` implementa o salto por **avanço de âncoras** — guarda o cursor que abre cada página visitada; para a frente, avança cursor a cursor; para trás, reusa a âncora guardada; se a lista acabar antes do alvo, para na última página e declara `reached: false`. Trocar o tamanho da página descarta os cursores (eles dependem do tamanho) e recomeça da página 0. Nenhum cursor é montado no cliente e nenhum offset é enviado.
- **Testes** (Vitest, suíte do frontend): `Pagination.test.tsx` (seletor, ir-para-página válido/inválido, Enter, regressão de Anterior/Próxima) e `keysetPagination.test.ts` (avanço por cursores, reuso de âncora, fim da lista, troca de tamanho) — os testes do navegador keyset registram cada cursor pedido e provam que só cursores emitidos pelo servidor são usados.

---

## 5. Como demonstrar (alínea a — POC-01 a POC-03)

1. Abrir a rodada mais recente do workflow **CI** no GitHub e mostrar o job **`e2e-navegadores`** verde.
2. Abrir o relatório do Playwright dessa rodada e, **para cada projeto** (`edge-88`, `chrome-100`, `firefox-116`), mostrar os dois testes do spec `navegadores.spec.ts` verdes e os anexos com o user-agent e a versão do build executado — declarando a divergência da seção 1.2 se o avaliador perguntar pela versão exata.
3. Na aplicação, abrir uma lista grande (Clientes) e demonstrar: o **seletor de tamanho de página** (10/25/50/100), o **"ir para página"** válido e o aviso ao digitar página fora do intervalo.
4. Para demonstração ao vivo fora do CI: subir o frontend (`npm run build` + `vite preview`, ou a stack completa para os specs com login) e rodar `npm run test:matriz` em `frontend/e2e`, conforme o `README.md` da pasta.

---

## 6. Pendências declaradas

- **Matriz verde não comprovada nesta entrega:** o ambiente de produção desta tarefa não executa navegadores nem o GitHub Actions. A matriz está configurada, os specs existem e a lógica de frontend está testada em Vitest; o verde da matriz depende da **primeira rodada do job `e2e-navegadores`** (ou de rodada local equivalente no PC do dono, com os navegadores dos canais instalados).
- **Versões históricas exatas:** ver a divergência da seção 1.2 — registrada, não mascarada.
- As demais listas do frontend que usam `Pagination` sem o seletor (o componente é retrocompatível: sem `onPageSizeChange`, nada muda) podem ligá-lo uma a uma; as duas listas entregues aqui são a prova do aceite.

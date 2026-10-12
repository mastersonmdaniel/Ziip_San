# Frontend do cadastro comercial (S1 a S6)

Data: 11/10/2026. Escopo: `frontend/` (React + TypeScript + Vite + Tailwind). Nada fora de `frontend/` foi alterado, além deste arquivo. O backend (`commercial/sigsan-registry`) é implementado em paralelo por outra pessoa; esta entrega fala com ele pelo contrato da seção 6.2 do relatório `07` e **não foi testada contra um backend real** (ele não existe ainda; só contra uma API de mentira nos testes).

## 1. Resultado real da verificação

Rodado em cópia local fora do Drive (`npm install`, `tsc --noEmit`, `npm run build`, `vitest run`). Node v24.21.0, npm 11.19.0, TypeScript 5.9.3, Vite 5.4.21, Vitest 1.6.1.

| Verificação | Resultado |
|---|---|
| `npm install` | 515 pacotes, sem erro (avisos de pacotes deprecated, nenhum bloqueante). `npm audit` **não foi rodado**. |
| `tsc --noEmit` (`strict: true`, `noUnusedLocals`, `noUnusedParameters`) | **0 erros** |
| `npm run build` (`tsc && vite build`) | **passou**, 2.413 módulos. Maior chunk: `MapCanvas` (maplibre, 803 kB, carregado sob demanda); chunk principal 201 kB. |
| `vitest run` | **7 arquivos, 95 testes, 0 falhas** |
| Conferência visual (Vite dev + API de mentira local, fora do repositório) | Pesquisa de imóveis, assistente (etapa 1, lista suspensa da localidade) e diálogo de catálogo renderizaram nos temas claro e escuro. Não foi feita revisão visual de todas as telas nem teste em leitor de tela real. |
| ESLint | **Não rodado**: o projeto não tem configuração do ESLint (`.eslintrc`). O script `lint` do `package.json` foi removido por apontar para algo inexistente. |

Os testes de acessibilidade (`vitest-axe`) cobrem as telas principais, mas o axe em jsdom **não mede contraste de cor** (não há motor de renderização). O contraste foi calculado à mão (seção 8). Teste com NVDA/VoiceOver e navegação por teclado em navegador real continuam **pendentes**.

## 2. O que estava quebrado (confirmado) e como ficou

| Defeito | Correção |
|---|---|
| `App.tsx` importava 17 páginas que não existem (`Login`, `Dashboard`, `Billing`...), tinha `</Routes>` sobrando e exportava nomeado enquanto `main.tsx` importava default | Reescrito. Só existem rotas de telas que existem. `export function App` e `export default App`. |
| `Consumers.tsx` chamava `GET /consumers`, mostrava colunas inexistentes (`nome`, `cpf_cnpj`, `categoria`), botões Ver/Editar/Excluir sem ação, exclusão física | Removido e substituído por S1 (`PropertySearch`). Exclusão passou a ser lógica, com motivo. |
| `tailwind.config.js` com chave não fechada (o build de CSS nem partia) | Corrigido. |
| `services/api.ts` com sintaxe inválida (`axios.create({...}` sem `)`, getter `client` duplicando o campo, `private`) e ~200 linhas de clientes para módulos sem backend (`billing`, `mtr`, `losses`...), todos tipados com `any` | Reescrito: instância axios + interceptores + `authApi`. Os clientes de módulos inexistentes foram removidos (voltam quando o backend existir). |
| `Layout.tsx` com `</aside>` fechado por `</div>`, `<main>` dentro de `<main>`, menu com 17 itens para telas inexistentes, sino de notificações com contador **fixo "3"** (dado falso), `h1` derivado do caminho da URL | Reescrito: 4 itens de menu (todos com tela), um só `main`, sem contador falso, "Pular para o conteúdo", foco no conteúdo ao trocar de rota. |
| `components/ui/*`: `Card`, `Table` e `Label` com JSX inválido (`</div>` sem abertura, `<label>` sem `{...props}`); `Button` importava `@radix-ui/react-slot` (não instalado) | Reescritos (`Table` agora exige `caption`). |
| `main.tsx` importava `@tanstack/react-query-devtools` (não instalado) e usava `var(--toast-bg)` sem `hsl()` | Corrigido. |
| `utils/cn.ts` importava `tailwind-merge` (não instalado) | `cn` usa só `clsx`. |
| `package.json`: `@vis.gl/react-maplibre ^1.0.0` **não resolve** (só existem versões 1.0.0-alpha e 8.x), então `npm install` falhava; `lint` sem configuração | Dependência removida (não é usada; o mapa usa `maplibre-gl` direto). |
| `useAuth` tratava a resposta do axios como dado (`const { access_token } = await authApi.login(...)`) | Corrigido (`authApi` devolve `data`). |
| `tokens de cor`: `--card` e `--popover` nunca foram definidos; primário `199 89% 48%` com texto claro dá **2,73:1** (reprova AA) | Tokens redefinidos (seção 8). |

## 3. Rotas (só o que existe)

| Rota | Tela |
|---|---|
| `/login` | Entrada (ver ressalva abaixo) |
| `/cadastro/imoveis` | S1 Pesquisa de imóveis |
| `/cadastro/imoveis/novo` e `/cadastro/imoveis/:id/editar` | S2 Assistente de imóvel (6 etapas). `?rascunho=<id>` retoma um rascunho. |
| `/cadastro/imoveis/:id` | S3 Ficha do imóvel (cabeçalho fixo + abas Cadastro, Clientes, Histórico) |
| `/cadastro/clientes`, `/novo`, `/:id`, `/:id/editar` | S4 Cliente (lista, formulário, ficha com "Revelar documento") |
| `/cadastro/territorio/:nivel` (`localidades`, `setores`, `quadras`, `rotas`) | S5 Hierarquia territorial |
| `/cadastro/catalogos/:tipo` | S6 Catálogos (`:tipo` = nome do recurso REST) |
| `*` | "Página não encontrada" |

`/` redireciona para `/cadastro/imoveis`. As rotas antigas (`/dashboard`, `/billing`...) foram removidas e caem em "Página não encontrada" (há teste disso).

**Ressalva do `/login`:** o módulo `platform/sigsan-auth` está vazio, então `POST /api/auth/login`, `GET /api/auth/me` e `/auth/logout` **não existem**. A tela de entrada foi escrita contra o contrato herdado do código anterior (`authApi`), e hoje mostrará o erro real de rede. Sem backend de autenticação, nenhuma tela do cadastro é alcançável. O campo "Empresa" grava `tenant_id` (usado no cabeçalho `X-Tenant-ID`, que o `TenantFilter` do backend lê).

## 4. Estrutura

```
src/services/api.ts                    axios, interceptores (Bearer, X-Tenant-ID, refresh), authApi
src/services/registry/types.ts         tipos do contrato (ConsumerUnit, Customer, Locality, CommercialSector, Block, ReadingRoute, Category, Subcategory, WaterStatus, SewerStatus...)
src/services/registry/errors.ts        ApiError, problem+json (errors[], warnings[]), 409 = conflito
src/services/registry/client.ts        createResourceClient: GET lista paginada, GET id, POST, PUT (version), DELETE lógico
src/services/registry/drafts.ts        rascunho no servidor + validate-step
src/services/registry/resources.ts     instâncias por recurso
src/utils/validators/                  cpf, cnpj (numérico e alfanumérico), document, cep, phone, inscription, matricula, geo
src/components/registry/               LookupField, InscriptionInput, CpfCnpjInput, CepInput, PhoneInput, MapPointPicker (+MapCanvas), Wizard, ConfirmWarning (+useConfirmable), ConflictAlert, Timeline, CatalogCrud (+crudMeta), CatalogSelect, AddressFields, DeleteReasonDialog
src/components/ui/                     Button, Field (rótulo+erro), Input/Select/Textarea/Checkbox, Alert, Dialog, Tabs, Table, Pagination, Card, Badge, PageHeader
src/pages/cadastro/                    S1..S6
src/test/                              setup, render, fixtures (SÓ de teste, prefixo "TESTE")
```

## 5. Contrato esperado do backend

Base: `/api/registry` (o proxy do Vite **não** remove mais o prefixo `/api`). Todas as chamadas levam `Authorization: Bearer` e `X-Tenant-ID`. Campos em inglês camelCase, como no relatório `07`. **Os itens marcados SUPOSIÇÃO não estão no relatório e precisam ser confirmados com o backend.**

### 5.1 Regras gerais (relatório 6.2)

| Item | Contrato usado |
|---|---|
| Lista | `GET /api/registry/{recurso}?q=&page=&size=&sort=` + filtros. Tela usa `size=25`. |
| Página (SUPOSIÇÃO) | `{ content[], page|number, size, totalElements, totalPages }`. Um array puro também é aceito. Ajuste em um lugar: `normalizePage` (`client.ts`). |
| Item | `GET /{recurso}/{id}` |
| Criar / alterar | `POST /{recurso}` e `PUT /{recurso}/{id}`. O PUT leva **`version` no corpo**. |
| Excluir | `DELETE /{recurso}/{id}` com corpo `{ "reason": "..." }` (SUPOSIÇÃO do formato do motivo). Lógico (`deleted_at` + motivo), nunca físico. |
| Erro | `application/problem+json`: `errors[{field,code,message}]` e `warnings[{code,message,confirmToken}]`. `field` em notação de caminho: `customers[0].customerId`. |
| Aviso confirmável | Resposta com **só** `warnings[]` (qualquer status 4xx). A tela mostra o aviso; ao confirmar, **reenvia a mesma requisição** com `confirmTokens: ["..."]` no corpo (SUPOSIÇÃO: nome e lugar do campo; o relatório diz apenas "reenviar com confirmToken"). |
| Concorrência | HTTP **409 sem `errors`/`warnings`** = "o registro mudou". A tela mantém o digitado e oferece "atualizar a versão e manter o que digitei" ou "descartar e recarregar". |
| Filtro de ativos (SUPOSIÇÃO) | `active=true` nas listas de catálogo usadas em seletores; `includeDeleted=true` nas pesquisas. |

### 5.2 Recursos

| Recurso (`/api/registry/...`) | Usado por | Campos principais |
|---|---|---|
| `consumer-units` | S1, S2, S3 | Lista: `ConsumerUnitSummary` (`registration`, `registrationDv`, `inscription{locality,sector,block,lot,sublot}`, `userName`, `userDocumentMasked`, `addressText`, `mainCategoryName`, `waterStatusName`, `sewerStatusName`, `economies`, `deleted`). Filtros da lista: `q`, `localityId`, `sectorId`, `blockId`, `waterStatusId`, `sewerStatusId`, `categoryId`, `profileId`, `routeId`, `cep`, `numberFrom`, `numberTo`, `includeDeleted`, `sort=inscription,asc` (SUPOSIÇÃO do sort). A inscrição completa é enviada **formatada em `q`** (`001.002.003.0004.005`), onde o servidor a reconhece. Detalhe: `ConsumerUnit` (relações como `Ref{id,code,name}`). Payload: `ConsumerUnitInput` (ids, números como número, `physical`, `externalIds.iptu`, `draftId`). Ver `types.ts`. |
| `consumer-units/{id}/customers` (SUPOSIÇÃO) | S3 aba Clientes | `UnitCustomerLink[]`: `customerId`, `customerName`, `customerDocumentMasked`, `relation` (`OWNER`/`USER`/`RESPONSIBLE`), `startDate`, `endDate`, `endReason`, `showOnBill`. |
| `consumer-units/{id}/history` (SUPOSIÇÃO) | S3 aba Histórico | `HistoryEvent[]`: `occurredAt`, `type` (`STATUS`/`INSCRIPTION`/`AUDIT`), `title`, `actor`, `changes[{field,from,to}]`. |
| `consumer-units/validate-step` (SUPOSIÇÃO) | S2 | `POST {step (0-5), data: <fatia do ConsumerUnitInput>, targetId, confirmTokens?}`. 200 = ok; 422 problem+json com `errors[]`/`warnings[]`. Regras de servidor esperadas: inscrição única (cita a matrícula existente), sequência na rota ocupada (aviso), limite de economias (aviso), situação x rede da quadra, imóvel principal na mesma quadra, IPTU único, ≥1 Usuário, exatamente 1 nome na conta, documento do Usuário obrigatório (parâmetro), município do endereço x setor. |
| `consumer-units/drafts` (SUPOSIÇÃO) | S2 | `POST {step, targetId, data}` -> `{id, version, step, targetId, data, updatedAt}`; `PUT /drafts/{id}` com `version` no corpo; `GET`/`DELETE /drafts/{id}`. `data` é **JSON opaco** (o estado do formulário). Gravar o imóvel envia `draftId` para o servidor descartar o rascunho. |
| `customers` | S4 | `CustomerSummary` na lista (`documentMasked`); `Customer` no detalhe (**o documento nunca volta, só `documentMasked`**); `CustomerInput` com `document` (CPF/CNPJ sem máscara; em edição, ausente = manter) e `documentExemptionReason`. Filtros: `q` (nome ou documento), `nameMatch` (`contains`/`startsWith`), `personType`, `customerTypeId`, `city`, `includeDeleted`. |
| `customers/{id}/reveal-document` | S4 | `POST` sem corpo -> `{ document, expiresInSeconds? }`. 403 sem permissão. O servidor registra o log. |
| `localities`, `commercial-sectors`, `blocks`, `reading-routes` | S5 e seletores | Campos em `types.ts` e `pages/cadastro/territoryDefinitions.ts`. Filtros de cascata: `localityId` (setores/rotas), `sectorId` (quadras), `blockId` (faces). |
| `block-faces` | S2 etapa 1 | Só se `useBlockFace`. `blockId`, `code`. |
| Catálogos: `categories`, `subcategories`, `property-profiles`, `water-statuses`, `sewer-statuses`, `customer-types`, `activity-branches`, `bill-delivery-modes`, `sewer-discharges`, `water-sources`, `well-types`, `pavements` | S6 e seletores | Campos completos para os 5 primeiros (relatório T11); os demais são "nome + ativo" (SUPOSIÇÃO). Lista em `pages/cadastro/catalogDefinitions.ts`. As situações de água/esgoto precisam expor `billable` (esgotamento obrigatório quando a situação de esgoto fatura). Subcategorias expõem `category{id,name}`. |
| `settings` (SUPOSIÇÃO) | S2 | `{ useBlockFace, requireReadingSequence }`. Sem a API, assume `false` e a validação do servidor decide. |
| `addresses/cep/{cep}` (SUPOSIÇÃO) | `CepInput` | `{cep, streetName, neighborhood, city, state}`; 404 = desconhecido. Via backend, para o navegador não mandar CEP a terceiros. |

### 5.3 Suposições que mais pesam

1. Formato da página e do `sort`.
2. Nome do campo `confirmTokens` e o comportamento "só `warnings[]` = confirmável".
3. `validate-step`, `drafts`, `history`, `customers` do imóvel, `settings`, `addresses/cep`.
4. Nome do campo da matrícula (`registration` + `registrationDv`; no banco é `matricula`/`dv`).
5. PUT do imóvel envia `customers[]` com **o conjunto desejado de vínculos vigentes** (não há tela de "encerrar relação").
6. DELETE com corpo `{reason}`.

## 6. Componentes comuns (seção 6.2)

| Componente | Observações |
|---|---|
| `LookupField` | Combobox ARIA 1.2 (setas, Enter, Esc, anúncio de resultados). Mostra "código · nome" no campo. **Substitui os popups do GSAN** por lista; não há modal de busca separado. Valor do formulário: `{id,label,code}`. |
| `InscriptionInput` | `LLL.SSS.QQQ.LLLL.SSS`; só dígitos; zeros à esquerda só na exibição (`parseInscription` devolve números). Faixas: localidade/setor/quadra 1-999, lote 1-9999, sublote 0-999. |
| `CpfCnpjInput` | Máscara; DV de CPF, CNPJ numérico e **alfanumérico**; maiúsculas automáticas; valor entregue sem pontuação; erro ligado ao campo. |
| `CepInput` | `00000-000`; consulta opcional ao completar; estados "consultando", "não encontrado", "indisponível". |
| `PhoneInput` | Máscara `(00) 00000-0000`; valor canônico E.164 (`toE164`). Não valida a tabela de DDDs. |
| `MapPointPicker` | Campos lat/long **sempre** visíveis (alternativa por teclado); par completo e dentro do Brasil. O mapa MapLibre só aparece com `VITE_MAP_STYLE_URL` (nenhum provedor de tiles foi embutido). Caixa envolvente aproximada (lat -33,75..5,28; lng -73,99..-28,83): **A CONFIRMAR** com o IBGE; o servidor é a autoridade. SIRGAS 2000 e WGS84 diferem menos que a precisão do cadastro. |
| `Wizard` | Indicador de etapas (`aria-current="step"`), foco no título ao trocar de etapa, Voltar / Salvar rascunho / Próximo. O rascunho vive no servidor. |
| `ConfirmWarning` + `useConfirmable` | Aviso confirmável vs erro bloqueante; reenvio com tokens acumulados. |
| `ConflictAlert` | Fluxo do 409 sem perder o digitado. |
| `Timeline` | Lista ordenada; mudanças como "campo: de X para Y" em texto. |
| `CatalogCrud` | CRUD genérico dirigido por metadados (`CrudMeta`/`FieldMeta`); esquema Zod gerado dos metadados; serve S5 e S6. |

### Validadores e fontes

* **CPF e CNPJ numérico**: módulo 11 (pesos 10..2 / 11..2 e 5,4,3,2,9,8,7,6,5,4,3,2 / 6,5,...,2). Valores de teste calculados à mão e documentados nos testes (529.982.247-25; 111.444.777-35; 11.222.333/0001-81; 11.444.777/0001-61).
* **CNPJ alfanumérico**: 14 posições (12 alfanuméricas + 2 dígitos), valor do caractere = código ASCII - 48 (A=17...Z=42), mesmos pesos e regra de resto do numérico. Fonte citada: Receita Federal, "CNPJ Alfanumérico" (perguntas e respostas): https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/perguntas-e-respostas/cnpj/cnpj-alfanumerico.pdf . O vetor `12.ABC.345/01DE-35` foi **recalculado à mão** (DV1: soma 459, resto 8, DV 3; DV2: soma 424, resto 6, DV 5) e confere. **O texto do PDF oficial não pôde ser lido por máquina nesta sessão** (a busca confirmou o algoritmo e o vetor por fonte secundária); portanto o item segue **A CONFIRMAR contra o PDF** antes de produção. A Receita recomenda evitar I, O, Q e F nas letras emitidas; a validação aceita A-Z.
* **Matrícula**: módulo 11 com pesos 2..9 da direita para a esquerda (relatório `01`, comportamento do GSAN). **A CONFIRMAR com o serviço `MatriculaDv` do backend**; por isso a interface **não bloqueia nem rejeita** matrícula com base nele. O validador existe e está testado, sem uso na UI ainda.
* **CEP**: 8 dígitos, não zerado, sem DV.

## 7. LGPD e dados pessoais

* CPF/CNPJ **sempre mascarados** nas listas e fichas (`***.123.456-**`; valor vindo da API). "Revelar documento" chama `POST .../reveal-document`, mostra por no máximo **15 s** (menor entre o limite local e `expiresInSeconds`), tem "Ocultar agora" e some ao sair da tela. Teste confirma: nada é gravado em storage nem em `console`.
* O documento só existe no estado do formulário (digitação) e no corpo do POST/PUT. Nunca em `localStorage`/`sessionStorage`/URL do navegador; a busca de S1/S4 não é espelhada na URL.
* Nenhum `console.log` no código da aplicação.
* Mínimo necessário: RG sob "use só se não tem CPF", data de nascimento e nome da mãe opcionais com a justificativa na tela; **sexo, profissão e nome abreviado não foram implementados** (marcados DESCARTAR no relatório); aviso de dado de terceiros no "nome do contato" e nos moradores; aviso de que coordenada de domicílio é dado de localização.
* **Risco aberto:** o contrato manda o documento em `GET ...?q=` (busca livre). A URL da **API** aparece em logs de servidor/proxy. Recomendação ao backend: aceitar `POST /api/registry/{recurso}/search` para consultas com documento. A tela hoje segue o contrato.
* O token de acesso continua em `localStorage` (decisão herdada). Alternativa melhor: cookie `HttpOnly` emitido pelo backend.

## 8. Acessibilidade (WCAG 2.1 AA / LBI)

Feito: `lang="pt-BR"`; "Pular para o conteúdo"; `h1` único por tela (`PageHeader`); rótulos associados a todos os campos (`Field`: `label/for`, `aria-describedby`, `aria-invalid`, `aria-required`); erros com ícone **e** texto (não só cor); resumo de erros com foco ao falhar a etapa; `role="alert"`/`status` conforme o caso; tabelas com `caption` e `th scope`; abas WAI-ARIA (setas, Home, End); diálogo modal com foco preso, Esc e retorno do foco; combobox ARIA; foco visível global (`:focus-visible`); `prefers-reduced-motion`; selos com texto ("Excluído", "Cadastro provisório"); mapa com alternativa textual completa.

Contraste calculado à mão (fórmula WCAG; razões):

| Par | Claro | Escuro |
|---|---|---|
| Texto / fundo | 17,9 | 18,0 |
| Texto do botão primário / primário | 5,95 | 8,28 |
| Texto secundário (`muted-foreground`) / fundo | 8,06 | 9,08 |
| Destrutivo (texto) / fundo | 6,96 | 7,09 |
| Aviso (texto) / fundo tingido | 7,03 | 9,68 |
| Borda de campo / fundo (mínimo 3:1) | 5,67 | 6,29 |

O primário anterior (`199 89% 48%` + texto claro) tinha 2,73:1 e foi escurecido. Badges usam as paletas `green/amber/blue` do Tailwind em tom 100/900 (alto contraste, não medido um a um).

Testes axe: S1, S2 (etapa 1), S3 (abas Cadastro, Clientes e Histórico), S4 (lista, formulário, ficha), S5, S6 (lista e diálogo aberto), Login e a aplicação inteira com menu e landmarks. O axe achou uma violação real na primeira rodada (ordem de títulos `h1 > h3` na ficha), já corrigida.

## 9. O que NÃO foi feito

* **S7 (hidrômetros) e S8 (recadastramento)**: fora do escopo (P1).
* **Hierarquia**: regionais, unidades de negócio e faces de quadra (S5 prevê, o relatório marca P1/"só se o cliente usar"). Campos que dependem de catálogos inexistentes ficaram de fora: município/porte/classe da localidade, grupo de faturamento/cobrança, tipo de leitura e empresas da rota, distrito/DMA e perfil da quadra.
* **Filtros de S1 por município, bairro e logradouro**: dependem do endereço normalizado (P1). Há CEP e faixa de número.
* **Ordenar por coluna** e **exportar CSV** na lista (o relatório pede CSV com permissão e log).
* **Ações do menu da ficha**: "Alterar inscrição", "Encerrar relação de cliente" e "Ações de campo" (dependem de endpoints e de ligações/OS, P1). A ficha tem só Editar e Excluir (lógico, com motivo). Abas "Ligações e medição", "Faturamento", "Débitos", "Atendimentos" e "Documentos" não existem de propósito (regra: só quando existirem).
* **"Novo cliente" em gaveta dentro do assistente**: há um link que abre o cadastro de cliente em outra aba.
* **Consentimento do e-mail** (`consentId`): a tela só preserva o vínculo; criar consentimento é do módulo LGPD (sem tela).
* **Campo "Ir para matrícula/inscrição" na barra superior**: não implementado porque levaria o termo (que pode ser documento) por URL ou estado de navegação.
* **RG: órgão expedidor** (catálogo inexistente).
* **Teste de assistente completo** (6 etapas até gravar) e testes do formulário de cliente com envio: só a etapa 1 do assistente e os fluxos de aviso/erro/409 do CRUD genérico têm teste de interação.
* **Navegação com leitor de tela real** e **verificação em navegador móvel**.
* Autenticação do backend (ver seção 3).

## 10. Dependências

| Mudança | Motivo |
|---|---|
| Removido `@vis.gl/react-maplibre` | `^1.0.0` não resolve no npm (só 1.0.0-alpha e 8.x); `npm install` falhava. Não é usado. |
| Removidos do `manualChunks` o `maps`, `charts` e `ui` | Referenciavam o pacote removido e módulos não usados. |
| Mantidos `react-router-dom`, `@tanstack/react-query`, `react-hook-form`, `zod`, `@hookform/resolvers`, `axios`, `clsx`, `lucide-react`, `date-fns`, `maplibre-gl` | Já estavam no `package.json` e são usados. Zod e React Hook Form **já existiam**; nada foi acrescentado para formulários. |
| `zustand`, `recharts`, `react-hot-toast`, `@hello-pangea/dnd` | Já presentes, **sem uso nesta entrega**. Podem ser removidos se continuarem sem uso. |
| Acrescentados (dev): `vitest-axe`, `@testing-library/react`, `@testing-library/dom`, `@testing-library/user-event`, `@testing-library/jest-dom`, `jsdom` | Testes de componente e de acessibilidade (a regra 5 pede `vitest-axe`). |
| Acrescentado `package-lock.json` | Instalação reproduzível. |
| `vite.config.ts`: proxy sem `rewrite`; `test` (jsdom) | O contrato é `/api/registry/...`. |

## 11. Como rodar

O `node_modules` é grande e o Drive é lento: copie `frontend/` para uma pasta local (fora do Drive) e rode lá; edite sempre no Drive e sincronize.

```bash
cd <copia-local>/frontend
npm install
npx tsc --noEmit        # tipos
npm run build           # tsc + vite build
npx vitest run          # testes (95)
npm run dev             # http://localhost:3000, /api -> http://localhost:8080
```

Variáveis (ver `.env.example`): `VITE_API_BASE_URL` (padrão `/api`) e `VITE_MAP_STYLE_URL` (sem ela, sem mapa).

## 12. Testes (resumo)

* `utils/validators/validators.test.ts`: CPF, CNPJ numérico e alfanumérico (vetor oficial), CEP, inscrição, matrícula, telefone, coordenadas e máscara LGPD. Cada valor esperado tem o cálculo à mão no comentário.
* `services/registry/registry.test.ts`: problem+json, classificação de erros, 409, página, `version` no PUT, `confirmTokens`, DELETE lógico.
* `components/registry/inputs.test.tsx`: `CpfCnpjInput` e `InscriptionInput` (digitação, máscara, erro ligado ao campo, axe).
* `components/registry/confirmFlow.test.tsx`: aviso confirmável (mostra, não grava, reenvia com o token), erro de campo do servidor, 409 sem perder o digitado, estados vazio/erro, exclusão com motivo.
* `pages/cadastro/propertyForm.test.ts`: regras por etapa do assistente e payload.
* `pages/cadastro/PropertyWizard.test.tsx`: `validate-step` + rascunho no servidor + avanço; aviso confirmável e erro bloqueante dentro do assistente.
* `pages/screens.test.tsx`: axe nas telas, menu honesto, S1 sem dado falso, "revelar documento" (tempo, 403, sem storage/console), validação da etapa 1.

As fixtures (`src/test/fixtures.ts`) são marcadas como de teste (prefixo "TESTE", documentos já mascarados) e só são importadas por arquivos `*.test.*`.

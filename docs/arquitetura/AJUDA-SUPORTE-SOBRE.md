# Ajuda, Suporte e Sobre

Decisões em ADR-0020 (ajuda e suporte) e ADR-0021 (Sobre, versão e changelog). Dependências: ADR-0010 (exceção do
ClamAV), ADR-0014 (outbox), ADR-0016 (pacote de marca), ADR-0018 (Central Ziip), ADR-0019 (cobrança por contrato),
RNF-10/11/26/40/52/54. O prefixo `urn:sigsan:problema:` passa a `urn:ziipsan:problema:` com a renomeação do P0
(plano P0.33). Todas as rotas seguem o prefixo `/api/v1` (plano P0.10); as do operador ficam sob `/api/v1/platform`,
como as demais da Central.

## 1. Objetivos

1. Resolver a dúvida antes do chamado: ajuda por tela, tour, dica por campo e erro explicado.
2. Chamado com protocolo, histórico, prazo contratual e medição.
3. Acesso do suporte a dados do tenant só com consentimento, prazo e auditoria.
4. Versão gerada no build e changelog do cliente versionado.
5. Sobre como fonte única de identificação do produto, do fornecedor e dos documentos aceitos.

## 2. Fronteira com o atendimento ao consumidor (R7)

| | Suporte Ziip (este documento) | Atendimento do tenant (R7) |
|---|---|---|
| Relação | Ziip → tenant (B2B, contrato SaaS) | Tenant → consumidor final |
| Base | Contrato e SLA (ADR-0019) | NR 11 e regras de SAC |
| Dados | Schema `suporte` no banco do core | Schema do tenant |
| Protocolo | `SUP-AAAA-NNNNNN`, sequência da plataforma | Formato e sequência do tenant |
| Prazos | Contratuais, por prioridade | Legais, por tipo de manifestação |

Os dois não compartilham tabelas, filas, numeração nem atendentes. Compartilham componentes: máquina de estados,
gerador de protocolo parametrizado, pipeline de anexos (validação, varredura, armazenamento), notificação pela outbox,
cálculo de prazo em dias úteis com feriados por município, componente de conversa e CSAT. Um chamado ao Ziip pode citar
o número de um protocolo R7 como referência, sem copiar dados do consumidor.

## 3. Onde ficam os conteúdos

| Conteúdo | Local | Formato |
|---|---|---|
| Artigos | `conteudo/ajuda/artigos/<modulo>/<slug>.md` | Markdown com cabeçalho: `id`, `titulo`, `modulo`, `rotas`, `papeis`, `publico` (tenant ou interno), `versao_minima`, `palavras_chave` |
| Tours | `conteudo/ajuda/tours/<rota>.yaml` | Passos com `alvo` em atributo `data-tour`, nunca seletor CSS |
| Dicas | `conteudo/ajuda/dicas/<modulo>.yaml` | Chave estável do campo (`faturamento.leitura.consumo`) → texto |
| Erros explicados | `conteudo/ajuda/erros/*.yaml` | Código exato → título, explicação, causas, ações, artigo, sugestão de chamado |
| Changelog do cliente | `conteudo/novidades/<versao>.yaml` | ADR-0021 |
| Chamados, estado de leitura | Banco, schema `suporte` | Dados transacionais |
| Aceites de documentos, implantações | Banco, schema `platform` (`CENTRAL-ZIIP.md` §4) | Dados transacionais |

O time de conteúdo edita por pull request (editor web do GitHub), com revisão do produto. O CI valida cabeçalho,
links, rotas, alvos `data-tour` existentes no código e cobertura do catálogo de erros, e compila `ajuda-bundle.json`.
Conteúdo tem pipeline próprio, sem nova imagem: o job `migrate` carrega o pacote nas tabelas de leitura, com busca em
português (`tsvector`, `unaccent`). No React, o hook `useAjudaContextual()` lê a rota atual (`useLocation`) e busca
artigos, tour e dicas; `<Dica id>` exibe a dica do campo.

## 4. Modelo de dados

Schema `suporte` no banco do core (o mesmo do schema `platform`), com `tenant_id` e RLS em toda tabela. Fica fora do
schema do tenant para que a fila da Central Ziip funcione entre clusters (ADR-0005). Versões no plano §3: core `V9_3`
(conteúdo de ajuda), `V9_4` (chamados), `V9_5` (novidades vistas, com o aceite de documento), `V10_2` (SLA e CSAT),
`V10_3` (acesso assistido) e `V10_4` (página de status).
`aceite_documento` e `implantacao` ficam no schema `platform`, junto do contrato (`CENTRAL-ZIIP.md` §4); o aceite de
documento não se confunde com `parameter_acceptances`, que é o aceite do conjunto de parâmetros (ADR-0002).

| Tabela | Campos principais |
|---|---|
| `artigo`, `artigo_versao` | id, slug, módulo, rotas[], papéis[], público, versão do conteúdo, hash, texto, `tsvector` |
| `artigo_avaliacao` | artigo, usuário, útil (sim/não), comentário, rota de origem |
| `tour_concluido` | usuário, tour, versão do tour, data |
| `erro_explicado` | código (`problema:<codigo>`, `nfage:<cStat>`, `nfse:<codigo>`), textos, artigo |
| `chamado` | id UUIDv7, protocolo, tenant, solicitante, assunto, categoria (dúvida, incidente, requisição, melhoria, segurança, privacidade), módulo, rota e versão de origem, `correlation_id`, prioridade P1–P4, estado, política de SLA, prazos de primeira resposta e resolução, pausa, violação, nível (N1, N2, engenharia), responsável, marcos de tempo, reaberturas, `contem_dado_pessoal`, `expurgar_em` |
| `chamado_mensagem` | chamado, autor (solicitante, agente, sistema), visibilidade (pública ou interna), corpo sanitizado, data |
| `chamado_anexo` | mensagem, nome, MIME detectado, tamanho, SHA-256, chave do objeto, estado da varredura |
| `chamado_evento` | transições de estado, prioridade, atribuição e SLA |
| `sla_politica` | nome, calendário (24x7 ou útil), alvos por prioridade; referenciada pelo contrato (ADR-0019) |
| `csat_resposta` | chamado, nota 1–5, comentário, data |
| `consentimento_acesso` | tenant, chamado, concedente, escopo (leitura ou leitura e escrita), módulos, início, fim, revogação |
| `sessao_acesso_assistido` | consentimento, agente, início, fim |
| `novidade_vista` | usuário, versão, data |
| `status_componente`, `status_incidente`, `status_atualizacao`, `janela_manutencao` | página de status |

## 5. Fluxos

**Abrir.** O formulário sugere artigos enquanto o usuário digita. Ao enviar, grava rota, versão, módulo e, vindo de
uma tela de erro, o `correlation_id` e o código do problema, sem payload. Gera o protocolo, sugere a prioridade
(impacto × urgência), calcula os prazos pela política do contrato e publica as notificações na outbox. P1 fica para
faturamento ou portal parado e suspeita de incidente de segurança.

**Estados.** `novo` → `em_atendimento` → `aguardando_cliente` (relógio pausado) → `resolvido` → `fechado`. Sete dias
sem resposta em `aguardando_cliente` levam a `resolvido`; `resolvido` fecha em 7 dias, e resposta nesse prazo
reabre. Fechado, abre-se outro chamado vinculado. O solicitante pode cancelar antes do atendimento.

**Responder e escalar.** Resposta pública ou nota interna (nunca visível ao tenant). Escalar muda nível e responsável
sem reiniciar o relógio. A 80% do prazo, alerta ao responsável; no estouro, ao coordenador.

**Fechar e CSAT.** Ao resolver, o solicitante recebe pedido de avaliação por 7 dias; nota 1 ou 2 abre revisão.

**Acesso assistido.** O agente pede acesso no chamado com motivo, escopo e prazo; o administrador do tenant (ou quem
tiver `suporte.autorizar_acesso`) aprova. O agente entra com a própria identidade e MFA (ADR-0007), com o
consentimento no token; não existe login como o usuário. Padrão: leitura, 2 horas, máximo 72; escrita só com escopo
explícito. Revelar documento (ADR-0008) e exportar ficam bloqueados. O tenant vê a faixa de acesso ativo, pode revogar
e recebe o relatório das ações. Toda requisição vai ao `audit_log` com o id do consentimento.

**Notificações.** Na aplicação e por e-mail, pela outbox. O e-mail leva protocolo, estado e link, sem conversa nem
anexos.

## 6. APIs principais

Lado do tenant (BFF, sessão do usuário):

- `GET /api/v1/ajuda/contexto?rota=` (artigos, tour e dicas da tela); `GET /api/v1/ajuda/artigos?q=&modulo=`;
  `GET /api/v1/ajuda/artigos/{slug}`; `POST .../avaliacao`; `GET /api/v1/ajuda/erros/{codigo}`;
  `PUT /api/v1/ajuda/tours/{id}/concluido`.
- `POST|GET /api/v1/suporte/chamados`; `GET .../{protocolo}`; `POST .../{protocolo}/mensagens`, `/cancelar`,
  `/reabrir`, `/csat`.
- `POST /api/v1/suporte/anexos` (etapa própria; devolve id e estado da varredura); `GET /api/v1/suporte/anexos/{id}`.
- `POST /api/v1/suporte/consentimentos`; `DELETE /api/v1/suporte/consentimentos/{id}`.
- `GET /api/v1/sobre`; `GET /api/v1/sobre/licencas`; `GET /api/v1/novidades`; `POST /api/v1/novidades/{versao}/vista`.

Lado do operador (Central Ziip, MFA obrigatório): `GET /api/v1/platform/suporte/fila`;
`PATCH /api/v1/platform/suporte/chamados/{protocolo}` (estado, prioridade, nível, responsável); `POST .../mensagens`
(com visibilidade); `POST .../pedidos-de-acesso`; `POST /api/v1/platform/acesso-assistido/{consentimento}/sessoes`;
incidentes e janelas da página de status.

Público, sem autenticação e com cache: `GET /api/v1/public/status` (mesmo prefixo público de
`/api/v1/public/brand`, ADR-0016).

## 7. Integração com a Central Ziip (ADR-0018)

A Central é o lado do operador: fila por prioridade, prazo e tenant; visão do tenant (contrato, SLA, versão, módulos,
erros recentes pelo `correlation_id`); respostas-modelo; pedido de acesso assistido; incidentes da página de status;
painel de conteúdo (buscas sem resultado, artigos mal avaliados, erros mais exibidos); FRT, MTTR, SLA cumprido,
reabertura, CSAT e deflexão. A Central não lê o schema do tenant fora de uma sessão de acesso assistido.

## 8. Página pública de status

Site estático em hospedagem separada da produção, para continuar no ar quando ela cair. Componentes: backoffice,
portal do consumidor (2ª via e Pix), autorizadores fiscais, PSP Pix e notificações. O estado vem das sondas dos SLOs
(RNF-10, RNF-11) e dos incidentes publicados na Central; manutenção aparece com 72 horas de antecedência (RNF-12).
Histórico de 90 dias e relato pós-incidente, sem nome de tenant. Na aplicação, uma faixa repete o incidente ativo.

## 9. Sobre e versionamento

Regras no ADR-0021. Pipeline:

1. O CI deriva a versão da tag (`v1.4.2`) ou, sem tag, `1.5.0-dev.N`, com build metadata `+<sha7>`.
2. Maven recebe `-Drevision`; `spring-boot-maven-plugin` gera `build-info.properties` e
   `git-commit-id-maven-plugin`, `git.properties`.
3. O Vite recebe versão, commit e data por variável de build e grava `version.json`.
4. CycloneDX (Maven e npm) gera o SBOM do RNF-35; dele sai `licencas-terceiros.json` (componente, versão, licença
   SPDX), embalado na imagem e no SPA. Licença fora da lista permitida bloqueia o CI.
5. `conteudo/novidades/*.yaml` é validado e embalado como `novidades.json`.
6. O job `migrate` grava a linha de `platform.implantacao`; essa é a data exibida.

Toda resposta da API leva `Ziip-Versao` e `Ziip-Versao-Minima-Cliente`. Servidor com versão diferente da do SPA gera
aviso de recarga; SPA abaixo da mínima gera aviso bloqueante, depois de preservar rascunhos.

O Sobre mostra produto e empresa desenvolvedora (razão social, CNPJ, contato, encarregado), vindos do pacote de marca
(ADR-0016); versão, commit, ambiente e data de implantação; Termos, Política, Contrato e DPA com a versão vigente e a
aceita, com data e autor do aceite; licenças de terceiros; relação de atualizações. "Novidades" abre uma vez por
versão por usuário, só com os itens que afetam o tenant.

## 10. O que se aproveita do Ziip Cloud

Aproveita-se: anexo validado no servidor com lista fixa sem SVG e HTML e baixado sempre como anexo; auditoria de cada
evento; tours por rota como dados (`ZIIP_TOURS`); dicas por campo com suporte a toque; estrutura e conteúdo do
`CatalogoErros` para a NFS-e (ADR-0003); a regra de registrar segurança, legislação e evidência legal; novidades uma
vez por versão; versão do documento legal gravada no aceite. Do `PACOTE-SUPORTE.md`: construção própria na mesma
pilha (rota B), estados com pausa de SLA, CSAT e avaliação de artigos.

Não se aproveita: anexo em `bytea` com tipo pela extensão; e-mail com conteúdo e anexos; resposta única sem protocolo
nem prazo; versão manual em três lugares; changelog em HTML no `app.js`; "visto" por instalação; implantação igual à
subida do processo; desenvolvedor e links fixos; catálogo por palavra-chave; tours por seletor CSS. O assistente de IA
do pacote vai para o P4.

## 11. Segurança e LGPD

- Anexos: até 5 por mensagem, 10 MB cada (RNF-26); tipo pelo conteúdo (PNG, JPEG, WEBP, PDF, TXT, CSV, XML), sem
  compactados; EXIF removido; anexo em quarentena até a varredura pelo ClamAV, que roda junto do `worker` (exceção do
  ADR-0010, substituível pelo serviço equivalente da hospedagem); infectado permanece bloqueado e gera alerta; objeto em
  `suporte/<tenant>/<chamado>/<anexo>` cifrado com a chave do tenant (RNF-32); download por URL pré-assinada curta,
  como anexo e com `nosniff`. Mensagens sanitizadas no servidor; cota e limite de taxa por tenant.
- Minimização: o formulário e o campo de anexo orientam a não incluir nem anexar dado pessoal desnecessário e a citar
  a ligação em vez do consumidor. CPF, CNPJ, e-mail ou telefone no texto geram alerta e marcam `contem_dado_pessoal`;
  listas mostram o texto mascarado.
- O usuário vê os próprios chamados; o administrador do tenant, os do tenant; agentes, a fila, com registro de acesso
  (RNF-41). Logs sem dado pessoal (RNF-52); dados no Brasil (RNF-44).
- Ziip é controlador dos dados dos usuários do tenant na relação contratual e operador dos dados de consumidores que
  apareçam em chamado ou acesso assistido, nos termos do DPA.
- Retenção decidida pelo dono do produto em 12/10/2026 (substitui a proposta de 5 anos e 180 dias): chamados **e**
  anexos, pela vigência do contrato mais 5 anos; `expurgar_em` é fixado quando o contrato termina. Consentimentos e
  sessões de acesso assistido seguem o prazo do `audit_log`. Registro na tabela do RNF-40. Efeitos LGPD: minimização
  (item acima); acesso restrito ao solicitante, ao administrador do tenant e aos agentes, com registro (RNF-41);
  eliminação no fim do prazo, com contagem. Chamados entram na exportação de fim de contrato (RNF-42), mas não na
  eliminação do tenant: o certificado de descarte registra a exceção e a data prevista, e o DPA precisa prevê-la.
- A versão exata só aparece para usuário autenticado.

## 12. Fases e critérios de aceite

Tarefas correspondentes no plano: P0.39–P0.42; P1.20–P1.24; P2.15, P2.16; P3.17–P3.19; P4.8.

| Fase | Entregas | Critério de aceite |
|---|---|---|
| P0 | Versão pelo build, `build-info`, `version.json`, cabeçalhos de versão e aviso de recarga; licenças pelo SBOM; estrutura de `conteudo/novidades` com validação no CI; `GET /api/v1/sobre` mínimo | Nenhuma versão escrita à mão no repositório; dois builds do mesmo commit dão a mesma versão; SPA antigo mostra aviso após implantação em homologação |
| P1 | Estrutura de `conteudo/ajuda` e pipeline de publicação; busca e ajuda contextual; hook de tour e componente de dica; catálogo para todo `urn:...:problema:*`; chamados com protocolo, conversa, estados, anexos com varredura e notificações; fila v0 na Central; Sobre completo; `aceite_documento`; novidades por usuário | Teste falha se um código de problema não tiver explicação ou se um alvo de tour não existir; EICAR é bloqueado e um PDF limpo é liberado; usuário de outro tenant recebe 404 em chamado alheio; e-mail sem conteúdo do chamado |
| P2 | SLA por contrato com calendário útil, pausa, alertas e escalonamento; CSAT; rejeições da NFAg-e e da NFS-e no catálogo; notas de versão filtradas por tenant; métricas de conteúdo | Prazo calculado bate com casos de teste com feriado municipal e pausa; relatório mensal de FRT, MTTR, SLA e CSAT gerado na Central |
| P3 (antes do go-live) | Página pública de status com sondas e janelas; acesso assistido com consentimento; retenção pela vigência do contrato mais 5 anos e expurgo; exportação no fim do contrato; pentest cobrindo o módulo; RIPD atualizado; plantão e manual de atendimento | Acesso sem consentimento é negado e auditado; revogação encerra a sessão em até 1 minuto; status muda sozinho com a sonda fora; ensaio de expurgo com contagem |
| P4 | Resposta por e-mail; assistente de deflexão com citação e transbordo humano; assinatura de status; notas do tenant; editor visual; NPS | A definir no P4 |

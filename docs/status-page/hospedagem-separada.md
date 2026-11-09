# Página pública de status em hospedagem separada — LC.7

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).

Aceites que se encontram aqui:

- **LC.7:** "página pública de status da P3.18 em hospedagem separada
  da produção".
- **P3.18:** "publicação em hospedagem separada da produção na LC.7" —
  a P3.18 entrega o artefato (página estática + endpoint) e remete a
  publicação a esta tarefa.
- **ADR-0020, item 7:** "Página pública de status em hospedagem
  separada, alimentada pelas sondas dos SLOs e pelos incidentes da
  Central."

## 1. O que é publicado, e de onde vem

| Peça | Origem | Natureza |
|---|---|---|
| `index.html` da página | P3.18 (`docs/status-page/index.html`) | estático, só exibe o JSON do endpoint |
| `GET /api/v1/public/status` | P3.18 (`PublicStatusController`) | servido pela aplicação; visão derivada, sem dado de tenant |
| Fatos de sonda | LC.7 (`sondas-sinteticas.json`) | gravam `last_probe_*` nos componentes (V10_4) |
| Resumos pós-incidente | P3.11 (modelo, bloco §7) → publicação da P3.18 | só após incidente `RESOLVED` e publicação expressa |

A página **não** é regenerada por este pacote: o artefato da P3.18 é
publicado como está. Na hospedagem separada, a constante
`STATUS_API_URL` da página aponta para a base pública da API de
produção (o próprio cabeçalho do `index.html` da P3.18 prevê isso).

## 2. Separação exigida (configuração da hospedagem)

A hospedagem separada deve satisfazer, por construção:

1. **Fora do ambiente de produção:** conta/projeto de hospedagem
   estática distinto do ambiente de produção da LC.2; a queda da
   produção **não** derruba a hospedagem, e vice-versa.
2. **Só artefato estático:** publica-se apenas o `index.html` (e
   ativos estáticos, se surgirem). Nenhum servidor de aplicação,
   nenhum segredo e nenhuma credencial de banco nesta hospedagem.
3. **Domínio próprio da página:** hostname público da página
   `[A DEFINIR]` (domínio/subdomínio de status, decisão da
   implantação com a LC.2/P2.27) com TLS — distinto dos hostnames do
   backoffice e do portal.
4. **Leitura, nunca escrita:** a página consome o endpoint público
   `GET /api/v1/public/status`. Se o endpoint cair junto da produção,
   a página continua no ar e exibe "Não foi possível carregar o estado
   do serviço" (comportamento já implementado na P3.18) — é exatamente
   para esse momento que a separação existe.
5. **Publicação por pipeline:** a página só muda por deploy do
   artefato versionado (novo `index.html` da P3.18); status, incidentes
   e janelas **nunca** são editados na hospedagem — são dados derivados
   servidos pelo endpoint.

## 3. Parâmetros de implantação

| Parâmetro | Valor |
|---|---|
| Provedor da hospedagem estática | `[A DEFINIR]` (P-LC7-06) — qualquer hospedagem estática fora da conta/ambiente de produção; a escolha é da implantação (LC.2) |
| Hostname da página | `[A DEFINIR]` (P-LC7-06) |
| `STATUS_API_URL` na página publicada | `[A DEFINIR — base pública da API de produção]` |
| Certificado TLS da página | emitido para o hostname da página na própria hospedagem (fora da borda de produção) |

## 4. Não executado

A publicação real exige a conta de hospedagem e o domínio: **não
executada neste bloco** (sem AWS/contas — regra dura), pendência
P-LC7-06. Nenhuma URL de status é alegada como existente.

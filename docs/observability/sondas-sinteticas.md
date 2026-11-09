# Sondas sintéticas de produção — LC.7

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).
Definições executáveis: `sondas-sinteticas.json` (neste diretório).

Aceite LC.7: "sondas sintéticas do backoffice e do portal (RNF-10,
RNF-11)". ADR-0020, item 7: a página pública de status é "alimentada
pelas sondas dos SLOs e pelos incidentes da Central".

## 1. Sondas definidas

| probe_key | Alvo | O que prova |
|---|---|---|
| `backoffice` | entrada pública do backoffice | aplicação administrativa respondendo (login/redirect + saúde pública, se exposta) |
| `portal` | entrada pública do portal do consumidor | portal respondendo (página pública de acesso + saúde pública, se exposta) |
| `api` | `GET /api/v1/public/status` (P3.18) | a base da própria página de status respondendo |

As sondas são **externas e sem autenticação**: exercitam o caminho
público (borda da LC.2 → serviço), nunca conteúdo autenticado nem dado
de tenant. Nenhuma credencial é usada — e nenhuma é necessária.

## 2. Como a sonda vira status (P3.18, sem atalho)

1. Cada execução grava o **fato** no componente de mesmo `probe_key`
   em `support.status_component` (V10_4 da P3.18):
   `last_probe_outcome` (`UP`/`DOWN`) + `last_probe_at`, sempre juntos.
2. O status exibido é **derivado** a cada leitura pelo
   `PublicStatusService` da P3.18 (sonda `DOWN` → `OUTAGE`; sem
   leitura → `UNKNOWN`). Não há flag manual: sonda fora, status muda
   sozinho — o comportamento que o aceite da P3.18 fixa e a LC.7
   alimenta em produção.
3. Sonda que não roda (executor parado) **não** grava `UP`: a ausência
   de leitura nova aparece como leitura antiga/`UNKNOWN` conforme a
   derivação da P3.18 — nunca como saúde presumida. (A P3.18 declara
   não ter limiar de "sonda velha"; nenhum foi inventado aqui.)

## 3. Executor das sondas

- Roda **fora** da produção que ele mede (mesma separação da página de
  status): serviço de sondas sintéticas do provedor gerenciado
  `[A DEFINIR]` (P-LC7-01) ou agendador externo equivalente — decisão
  de implantação registrada na ativação. Sonda hospedada dentro do
  ambiente medido não comprova o caminho público.
- A escrita do fato (§2) é feita pela integração (credencial mínima,
  só `status_component.last_probe_*` dos componentes das sondas);
  a fiação JDBC/coletor que a P3.18 declarou pendente é fechada nessa
  integração, não por este pacote.

## 4. O que fica [A DEFINIR] (sem fonte, sem invenção)

- URLs públicas reais de backoffice e portal (saídas da LC.2/P2.27).
- Cadência de execução e timeout de cada sonda: nenhuma fonte local
  os fixa (P-LC7-05).
- Os textos dos RNF-10 e RNF-11 não constam das fontes locais lidas;
  se eles fixarem cadência, regiões de origem ou cenários autenticados,
  as definições são ajustadas na ativação (P-LC7-07).

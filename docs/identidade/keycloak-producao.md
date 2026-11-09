# Keycloak de produção na AWS — configuração (LC.6)

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).
Tarefa: LC.6 "Keycloak de produção na AWS" (origem 17:O3; RNF-17;
deps LC.2, P0.29; tamanho M). Fonte única dos aceites:
`ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`
(LC.6) e `tarefas-P0.json` (P0.29, P0.35), lidos na íntegra.

> **Estado deste pacote:** configuração de produção **produzida e
> validada localmente**; **nada foi executado na AWS** (sem conta neste
> bloco — regra dura). O teste de derrubada de réplica está roteirizado
> e **declarado não executado** (pendência P-LC6-03).

## 1. Aceite (íntegra, LC.6)

"Keycloak de produção com a configuração do modo de produção da P0.29
reaplicada na AWS: `start --optimized`, TLS, `sslRequired=all` e 2
réplicas; derrubada de uma réplica sem perda de sessão (teste); tema
da P0.35 na imagem"

Aceite da P0.29 (base reaplicada): "Keycloak local com
`start --optimized`, no lugar do `start-dev` atual do
`docker-compose.yml`; TLS com certificado local e `sslRequired=all`;
duas réplicas no compose; derrubada de uma réplica sem perda de sessão
(ensaio do RNF-17); usa o PostgreSQL 17 da P0.4; Keycloak na AWS (ver
LC.6)".

Aceite da P0.35 (tema): "realm com `loginTheme`, `emailTheme` e
`displayName` gerados do pacote de marca; trocar `ZIIPSAN_BRAND` muda
login e e-mail sem alterar código (teste); o tema entra na imagem de
produção do Keycloak da P0.29".

## 2. O que foi reaplicado do modo de produção local (evidência)

O modo de produção da P0.29 existe localmente no `docker-compose.yml`
conferido em espelho de pacote local (serviços `keycloak` e
`keycloak-2`, perfil `ha`). Dele, caractere a caractere:

| Item local (P0.29) | Valor local | Na AWS (LC.6) |
|---|---|---|
| Imagem | `quay.io/keycloak/keycloak:26.8.0` | mesma imagem/tag (Dockerfile) |
| Comando | `kc.sh build && exec kc.sh start --optimized --import-realm` | build vira etapa do Dockerfile; partida `start --optimized --import-realm` |
| Banco | `KC_DB=postgres`, `KC_DB_URL=jdbc:postgresql://postgres-primary:5432/keycloak`, banco/role próprios `keycloak` | idem, URL `[A DEFINIR]` (saída da LC.3) |
| TLS | `KC_HTTPS_PORT=8443`, cert. em `/opt/keycloak/certs/tls.crt|tls.key` (certificado local) | TLS obrigatório; terminação na borda (LC.2) e/ou no serviço com certificado real `[A DEFINIR]`; `KC_HTTP_ENABLED=false` no plano público |
| Cache/sessão | `KC_CACHE=ispn`, `KC_CACHE_STACK=jdbc-ping` | idem — sessão replicada pelo banco compartilhado |
| Saúde | `KC_HEALTH_ENABLED=true`, `/health/ready` na porta 9000 | idem + `KC_METRICS_ENABLED=true` (consumo da LC.7) |
| Réplicas | 2 (`keycloak` + `keycloak-2` perfil `ha`) | 2 réplicas fixas (`valores-implantacao.yaml`) |
| `sslRequired=all` | fixado no aceite da P0.29 (campo do realm) | `sslRequired: "all"` em `realm-producao.json` |

Diferenças deliberadas local → AWS (declaradas, não silenciosas):

- `KC_HOSTNAME` local é `http://localhost:8080` (emissor dos tokens no
  PC de desenvolvimento). Em produção é `[A DEFINIR]` (domínio público
  do IdP atrás da borda da LC.2) com `KC_HOSTNAME_STRICT=true`; manter
  o valor local em produção quebraria o emissor — por isso ele **não**
  foi copiado.
- O certificado local (gerado por script, fora do repo) é substituído
  pelo certificado de produção `[A DEFINIR]` (borda da LC.2 ou segredo
  da LC.4).
- Senhas: no local vêm do `.env` fora do repo; na AWS, de referências
  ao Secrets Manager (LC.4). Nenhum literal neste pacote.

## 3. Sessão sem perda — por que o desenho sustenta o teste

As sessões do Keycloak vivem no cache distribuído Infinispan
(`KC_CACHE=ispn`) com descoberta das réplicas pelo banco
(`jdbc-ping`) sobre o PostgreSQL compartilhado. Nenhuma réplica é dona
exclusiva de sessão: derrubada uma, a outra atende com a sessão
existente. É o mesmo mecanismo do ensaio local do RNF-17; a prova em
AWS é o roteiro em `teste-derrubada-replica.md` (não executado).

## 4. Composição com a LC.2 (sem a editar)

`infra/keycloak/producao/valores-implantacao.yaml` é o arquivo de
valores do componente `keycloak` para a IaC da LC.2 (ambientes em
`sa-east-1`). A LC.6 não cria rede, balanceador, certificado nem
pipeline: consome as saídas da LC.2 (borda/registro), da LC.3 (banco)
e da LC.4 (segredos), todas referenciadas como `[A DEFINIR]`.
**O pouso da LC.6 depende do pouso prévio da LC.2** (ver LANDING.md).

## 5. Pendências (nada simulado)

- **P-LC6-01** — Realm vigente real ausente localmente
  (`infra/keycloak/realms/ziipsan-realm.json` só é referenciado pelo
  compose; o arquivo não está em nenhum pacote local). O
  `realm-producao.json` deste pacote é molde dos campos do aceite, a
  mesclar sobre o realm real na implantação. **[SEM FONTE]**
- **P-LC6-02** — Tema da P0.35 ausente localmente (nenhum artefato de
  tema nos pacotes locais). Nomes de `loginTheme`/`emailTheme` e
  `displayName` ficam `[A DEFINIR]`; build sem o tema deve falhar.
  **[SEM FONTE]**
- **P-LC6-03** — Teste de derrubada de réplica em AWS não executado
  (sem conta AWS neste bloco). Roteiro pronto em
  `teste-derrubada-replica.md`.
- **P-LC6-04** — Saídas da LC.2/LC.3/LC.4 (registro de imagens,
  hostname, certificado, URL do banco, referências de segredo, CPU/
  memória, distribuição em AZs) são `[A DEFINIR]` até os pousos
  respectivos.
- **P-LC6-05** — Procedimento de acesso administrativo de produção
  (bootstrap/MFA) `[A DEFINIR]`; nenhuma credencial neste pacote.
- **P-LC6-06** — O texto do RNF-17 não consta das fontes locais lidas
  (só a remissão "ensaio do RNF-17" nos aceites). O pacote trata o
  RNF-17 apenas pela descrição dos aceites (2 réplicas, sessão sem
  perda); nenhum requisito adicional foi presumido.

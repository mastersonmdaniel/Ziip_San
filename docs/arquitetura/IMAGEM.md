# Imagem de container ZIIPSAN (P0.5)

Uma única imagem para os três modos de execução do backend. O frontend (SPA) é
deployado à parte, como arquivos estáticos — esta imagem não o embute.

## Modos (entrypoint `docker-entrypoint.sh`)

| Modo | O que faz |
| --- | --- |
| `api` (padrão) | Processo da API. Não migra na subida (P0.6): a migração é um job separado. |
| `worker` | Mesmo processo, perfil `worker`. Modo reservado para jobs em background; hoje não há beans exclusivos de worker, então o processo sobe igual à api. O modo existe para o deploy já separar réplicas de worker. |
| `migrate` | Job de migração: perfil `migrate` + `migrate-on-startup=true`. Na P0.6 o job migra e sai (MigrateExitRunner); na P0.5 o perfil ainda não existe, então o container migra na subida e fica no ar — verifique pelo log e pare o container. |

## Build e tag

```sh
scripts/build-image.sh   # tag fixa: ziipsan-app:<versão-do-pom>-<sha-curto-do-git>
```

Nunca `:latest`: o deploy referencia a tag exata, e a tag exata corresponde a um
commit exato. Nesta VM o runtime verificado é Podman + crun + `--cgroups=disabled`
+ `--network=host` (o daemon Docker não funciona aqui); o script usa podman quando
disponível e docker como fallback.

## Segurança

- Usuário não root: `ziipsan`, UID 10001 (jar e SBOM pertencem a ele).
- Nenhum segredo embutido: senhas, URLs e chaves chegam por variável de ambiente
  em runtime. Sem elas, a subida falha listando o que falta (ver `application.yml`).
- Healthcheck: `GET /actuator/health` (saúde pública, sem token).
- Portas não são publicadas pela imagem: quem roda publica só em `127.0.0.1`
  (ou usa `--network=host` com porta própria).

## SBOM

Gerado pelo plugin CycloneDX no build da app (`platform/ziipsan-app/target/bom.json`)
e copiado para `/app/sbom/bom.json` dentro da imagem.

**Módulos fora do scan da app, presentes no SBOM e no jar:** quality, losses, gis,
mtr, lgpd, billing, metering, collection. Eles são dependências do módulo app
(módulo `platform/ziipsan-app/pom.xml`) porque as migrations e as entidades JPA de
todos os módulos compõem o schema único do tenant — mas o `scanBasePackages` do
`ZiipsanApplication` continua sendo só `app`, `core`, `platform` e `registry`.
Ou seja: estão no classpath e no SBOM, mas não são componentes Spring ativos.
Esta nota existe para ninguém ler o SBOM como "módulo X está rodando".

## Frontend: config de runtime (P0.5)

O SPA lê `frontend/public/config.json` (copiado para a raiz do `dist` pelo Vite)
ou `window.__ZIIPSAN_CONFIG__` injetado antes do bundle, antes de renderizar
(`main.tsx` aguarda `loadRuntimeConfig()`; `api.ts` e `oidc.ts` resolvem a config
em cada chamada / na criação do cliente). O arquivo commitado é um placeholder
(valores nulos = fallback para os `VITE_*` do build). No deploy, substitua o
arquivo — sem rebuild — ou injete a variável global. Nunca colocar segredo nele:
são só URLs e IDs públicos de client.

## Banco

A imagem não leva PostgreSQL. O banco vem do ambiente: compose em
`postgis/postgis:15-3.4` hoje; PG17 + PostGIS 3.7.0 local via PGDG/`ci-linux.sh`
até existir a tag `postgis:17-3.7` (P0.4, tag inexistente — nenhuma tag inventada).

## Verificação nesta VM (P0.5, 01/11/2026)

- Build e runs feitos com Podman + crun + `--cgroups=disabled --network=host`
  (o daemon Docker não funciona aqui; sem `--network=host` o netavark falha com
  `setns: Operation not permitted`; o storage foi recriado com `vfs` porque o
  banco de storage anterior era `overlay` — ver `scripts/build-image.sh`).
- A JVM desta VM reseta sockets para o PostgreSQL em `127.0.0.1` (achado do
  turno 3, também no `ci-linux.sh`): os containers só conectaram com
  `JAVA_OPTS=-Djava.net.preferIPv4Stack=true`. É um desvio desta VM, não da
  imagem — por isso a flag não está embutida no Dockerfile.
- Achado que motiva a P0.6: rodar `api` sem a senha do migrador sobe o processo
  (liveness/readiness UP), mas o health agregado fica DOWN porque o indicador
  de saúde do `migratorDataSource` falha (senha ausente). Com a senha, o health
  agregado fica UP e `/api/v1/me` sem token dá 401. Na P0.6 a api nem cria o
  `migratorDataSource`, então o sintoma desaparece junto com a exigência.

## Verificação da P0.6 (01/11/2026, mesma receita Podman, PG17 dedicado 55447)

- Job `migrate`: saiu sozinho com código 0 (`MigrateExitRunner`), core v1+v2
  aplicada, `tenant_catalog` criado — sem precisar parar o container à mão.
- Api SEM a senha do migrador: health agregado UP e `/api/v1/me` sem token 401.
  O sintoma da P0.5 está fechado: a api não exige nem usa a credencial do migrador.

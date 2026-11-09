# Tema da P0.35 — [SEM FONTE] local (pendência P-LC6-02)

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).

Este diretório é o **ponto de montagem** do tema de login e de e-mail da
P0.35 na imagem de produção (ver `../../Dockerfile`). Ele está
**intencionalmente sem arquivos de tema**:

- O aceite da P0.35 fixa o comportamento — realm com `loginTheme`,
  `emailTheme` e `displayName` **gerados do pacote de marca** (P0.34,
  pacote `branding/ziipsan`; trocar `ZIIPSAN_BRAND` muda login e e-mail
  sem alterar código) e "o tema entra na imagem de produção do
  Keycloak da P0.29".
- Nenhum artefato do tema (diretório de tema do Keycloak, templates,
  CSS ou mensagens) existe nos pacotes locais conferidos nesta produção
  (busca por `loginTheme`/`emailTheme`/`ZIIPSAN_BRAND` e por diretórios
  de tema nos espelhos locais: nenhum resultado). A P0 é pré-retomada.

Portanto: **nenhum tema foi inventado**. Para fechar a imagem:

1. Obter o tema gerado do pacote de marca (fonte da P0.35/P0.34) e
   colocá-lo neste diretório (estrutura padrão de tema do Keycloak:
   `login/` e `email/`).
2. Fixar os nomes resultantes de `loginTheme`/`emailTheme` e o
   `displayName` em `../../realm-producao.json` (hoje `[A DEFINIR]`).
3. Rebuild da imagem e conferência visual das telas de login e de um
   e-mail transacional antes do pouso.

Enquanto o passo 1 não acontece, o build da imagem **deve falhar ou ser
explicitamente bloqueado** — subir o Keycloak de produção sem o tema da
P0.35 viola o aceite da LC.6.

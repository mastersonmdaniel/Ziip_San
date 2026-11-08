# POC-55 e SAMAE 2.24 — roteiro de ensaio (ED-39)

**Estado: PENDENTE EXTERNO.** A POC-55 (app do cliente em Android e
iOS) e o item 2.24 do roteiro do SAMAE so passam em ensaio com
aparelhos reais, contas de loja do prestador e o ambiente do tenant —
nada disso existe nesta VM. Este documento e o roteiro executavel do
ensaio; nenhum item abaixo esta marcado como feito por esta entrega.

## Pre-condicoes

1. Contas de loja do prestador ativas (Play Console organizacao e
   Apple Developer Program organizacao) e parametros
   `app.client_*_store_account` registrados.
2. Tenant sintetico de ensaio com marca cadastrada (P1.19), curva
   horaria e alertas da ED-17 configurados, e conta de consumidor
   sintetica no realm `ziipsan-portal`.
3. Pipeline executado para o tenant de ensaio; marcadores
   `__ANDROID_CERT_SHA256__` e `__APPLE_TEAM_ID__` preenchidos com os
   valores reais da conta; arquivos de associacao publicados no host.
4. Aparelhos: 1 Android e 1 iOS, com TalkBack/VoiceOver disponiveis.

### Pre-condicoes explicitas do destrave (correcao D7 da revisao)

Sem os dois itens abaixo, o login por App Link/Universal Link NAO
verifica e a POC-55 falha no passo 3 do roteiro — sao pre-condicoes,
nao detalhes:

5. **Arquivos de associacao PUBLICADOS no host do tenant de ensaio**
   (o mesmo `universal_link_host` da V11_1), servidos por HTTPS sem
   redirecionamento:
   - `https://<host>/.well-known/assetlinks.json` — o gerado pelo
     pipeline, com a digital SHA-256 REAL do certificado de assinatura
     do app Android (a do Play App Signing/upload, da conta do
     prestador) no lugar de `__ANDROID_CERT_SHA256__`;
   - `https://<host>/.well-known/apple-app-site-association` — o
     gerado pelo pipeline, com o Team ID REAL da conta Apple no lugar
     de `__APPLE_TEAM_ID__`.
   Conferencia previa ao ensaio: baixar os dois arquivos do host
   publico e conferir conteudo e `Content-Type` (JSON) — arquivo
   gerado no repo NAO conta como publicado.
6. **Clients do Keycloak CRIADOS via Admin API** na implantacao: um
   client publico por app do tenant de ensaio (um por plataforma,
   conforme o JSON gerado pelo pipeline: realm `ziipsan-portal`, so
   authorization code, PKCE S256, redirect unico
   `https://<host>/auth/callback`, sem curinga). Sem o client criado
   no realm, o navegador do sistema abre mas a autorizacao e recusada
   (`invalid_client`) — a criacao e ato da implantacao (Admin API),
   nao deste pacote, que entrega o contrato e o JSON.

## Roteiro (por plataforma)

1. Instalar o app pela loja em modo de teste (trilha interna do Play /
   TestFlight): instalacao limpa sem erro.
2. Abrir o app: nome, icone e cores sao os do tenant de ensaio.
3. Login: o navegador do sistema abre (nunca WebView), autentica no
   realm `ziipsan-portal` e volta pelo App Link/Universal Link.
4. Conferir no aparelho que nenhum token esta acessivel ao JS nem em
   armazenamento web; forcar rotacao do refresh token e relogar.
5. Consumo: com o parametro da curva ligado, a curva horaria aparece
   com instante e estado do dado; desligado, nao aparece.
6. Disparar os alertas da ED-17 (continuo 24h e pico, sobre massa
   sintetica): o push chega com texto generico — inspecionar o payload
   no servidor de push: nenhum dado pessoal.
7. Acessibilidade (SG-782): percorrer as telas principais com
   TalkBack/VoiceOver, fonte do aparelho ampliada e reducao de
   movimento ligada, sem perda de funcao.
8. Atualizacao remota: publicar bundle web de mesmo proposito (aplica)
   e um de proposito alterado (recusado).
9. Registrar versao/build aprovados na V11_1 (`markPublished`).

## SAMAE 2.24

O item 2.24 do roteiro do SAMAE e conferido no mesmo ensaio, sobre o
roteiro oficial do edital (que nao esta nesta VM): o resultado e do
ensaio, registrado pela comissao — esta entrega fornece o app, as
regras e este roteiro, nao o resultado.

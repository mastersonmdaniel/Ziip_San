# ZiipSan Campo — app de campo Android (ED-18)

Base do app de campo: projeto Capacitor, somente Android, distribuido por APK
assinado (e, quando houver conta de organizacao, pela loja na regra da ED-39).

## O que esta base entrega

- **Autenticacao (ADR-0030):** login no navegador do sistema (Custom Tab) com
  PKCE S256 no realm `ziipsan`, retorno por App Link; refresh token rotativo
  **so** no Android Keystore, inacessivel ao JavaScript (o plugin nativo troca
  refresh por access token sem expor o refresh). Ver `src/auth/` e
  `src/plugins/secureToken.ts`.
- **Banco local cifrado:** chave AES-256 no Android Keystore (nao extraivel),
  banco SQLCipher. Ver `FieldSecurityPlugin.kt`.
- **Leitura offline de uma rota:** pacote de rota com `task_id` por tarefa,
  fila local e sincronizacao idempotente (reenvio nao duplica). Ver
  `src/sync/syncEngine.ts` e o servico de sincronizacao no metering.
- **Diretivas do servidor:** `BLOCK` recusa e apaga; `RETIRE` envia as
  pendencias e apaga. Ver `syncEngine.ts`.
- **OCR no aparelho:** foto do visor reconhecida on-device; so os digitos
  trafegam. Comparacao OCR x digitado x historico em `src/ocr/ocrCompare.ts`
  (espelho da regra Java do servidor, mesmos vetores de teste).
- **Atualizacao remota:** so bundle web assinado, mesmo app e mesmo proposito,
  sem Ionic Appflow. Ver `src/update/remoteUpdate.ts`.

## Testes

- Unitarios (TS): `npm test` (node --test) — rodam tambem nesta base sem SDK.
- Instrumentados (Android): `android/app/src/androidTest/` — rodam no CI, no
  emulador (job `campo-android`). Nao rodam sem SDK Android.

## Pendencias de implantacao (nao sao codigo)

- Dominio real do App Link (`APP_LINK_HOST`) e `assetlinks.json` publicados;
  client publico `ziipsan-campo` criado no realm `ziipsan`.
- Keystore de release e segredos no CI; conta de organizacao na loja com
  D-U-N-S ou verificacao governamental e titular em `app.store_account_owner`
  (ato do dono). Politica completa: `docs/campo/POLITICA-MOVEL.md`.

# Politica movel do ZiipSan — ED-18 (PLT-33, ADR-0026, ADR-0030)

Esta politica e a base que a ED-39 aplica ao app do cliente por tenant.

## 1. Toolchain movel (manutencao nos moldes da P3.14)

- O app de campo usa **Capacitor na linha com suporte ativo** do fornecedor.
  Antes de cada release, conferir a linha suportada e registrar a versao
  travada no `package-lock.json`; linha sem suporte nao vai a producao, como
  na regra da P3.14 para o Spring Boot.
- **API alvo:** a exigida pelo Google Play para apps novos e atualizacoes na
  data do release. Na data desta entrega, a exigencia registrada no aceite da
  ED-18 e **Android 16, API 36** (`targetSdkVersion = 36` em
  `campo/android/build.gradle`, conferida no APK pelo CI). A cada mudanca de
  exigencia da loja, revisar este numero, o `compileSdk` e a API do emulador
  do CI na mesma alteracao.
- Gradle/AGP/Kotlin seguem a matriz de compatibilidade da linha do Capacitor
  adotada; os numeros ficam no wrapper e nos build scripts, nao neste texto.

## 2. Conta de loja e titularidade

- Publicacao so em **conta de organizacao** do Google Play, com **D-U-N-S**
  ou verificacao de orgao governamental, conforme o aceite da ED-18.
- O titular da conta e registrado no parametro **`app.store_account_owner`**
  (definicao criada na core V11, sem padrao de fabrica: o registro e ato do
  dono na implantacao). Enquanto o parametro estiver vazio, nao ha publicacao
  em loja — a distribuicao e por APK assinado.
- Criar a conta, obter o D-U-N-S e nomear o titular **nao sao tarefas de
  codigo**: sao atos do dono/da organizacao, pendencia declarada da ED-18.

## 3. Atualizacao remota

- Permitida **somente** para codigo interpretado (o bundle web), com
  manifesto assinado, mesmo `appId` e **mesmo proposito** do app instalado
  (hash de proposito conferido em `campo/src/update/remoteUpdate.ts`).
- Mudanca nativa, de permissoes ou de proposito exige **APK novo assinado**
  pelo CI. **Sem Ionic Appflow**: a distribuicao do bundle e do proprio
  ZiipSan (servidor do tenant/plataforma).

## 4. OCR e DPA (componentes de terceiro)

- O OCR da ED-18 roda **no aparelho** (ML Kit Text Recognition, processamento
  local): a imagem do visor nao e enviada a terceiro, logo **nao ha servico
  externo de OCR a registrar no DPA nesta configuracao**.
- Se qualquer componente com servico externo for adotado (OCR em nuvem,
  analytics, crash reporting com dado pessoal), o fornecedor entra no **DPA**
  e na lista publica de subprocessadores **antes** de a funcao ser ligada, e
  esta secao e atualizada na mesma alteracao. Dependencias Android declaradas
  nesta entrega (EncryptedSharedPreferences, SQLCipher) processam localmente.

## 5. RNF-39 — matriz de evidencia

RNF-39, como a ED-39 a cita: *refresh token guardado so no Keychain ou no
Keystore*. Evidencia desta entrega:

| Controle | Onde | Prova |
|---|---|---|
| Refresh token so no Keystore | `SecureTokenPlugin.kt` (EncryptedSharedPreferences + MasterKey do Keystore) | Teste instrumentado `SecureTokenPluginInstrumentedTest` (CI) |
| Refresh token inacessivel ao JS | Contrato `SecureTokenBridge` sem metodo de leitura do refresh; troca refresh->access dentro do nativo | Teste instrumentado estrutural + revisao do contrato |
| Banco local cifrado com chave no Keystore | `FieldSecurityPlugin.kt` (AES-256 no AndroidKeyStore, nao extraivel) | Teste instrumentado `EncryptedDatabaseInstrumentedTest` (CI) |
| Aparelho nao registrado/bloqueado nao sincroniza | `FieldDeviceService.assertCanSync` + `FieldSyncService` | Testes unitarios Java |
| Revogacao em ate 5 min | Estado conferido a cada contato, sem cache alem da janela (`REVOCATION_MAX_EFFECTIVE`) | Teste unitario Java da constante e do portao |

"Verde" aqui significa: controle implementado com a prova indicada. As provas
instrumentadas rodam no CI do dono (emulador API 36); nesta VM, sem SDK
Android, elas foram escritas e nao executadas — pendencia declarada.

## 6. Remissoes a P4.4

A P4.4 foi fundida na ED-18 (duplicata pura). Documentos do plano e do repo
que remetem a P4.4 passam a ler **ED-18**. Esta entrega registra a regra; a
varredura e a troca textual nos demais documentos do repo sao trabalho
editorial sobre arquivos fora do pacote da ED-18 e ficam listadas como
pendencia de integracao (nao foram reescritos aqui).

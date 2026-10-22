# Estudo técnico: porte do motor fiscal do Ziip Cloud para Java (Ziip SAN)

- **Data:** 12/10/2026
- **Decisão de origem:** ADR-0003 (motor fiscal do Ziip Cloud portado para Java; NFAg-e e NFS-e sobre ele); emissão
  da conta em campo: ADR-0031; hospedagem: ADR-0011, aceito (AWS `sa-east-1`, KMS e S3 com Object Lock), implantada
  só no lançamento comercial, com desenvolvimento e homologação locais até lá (ADR-0033, plano v2.7); certificado da
  homologação local e data do lançamento: ADR-0033, ajuste v2.8 (plano v2.8); guarda e descarte dos XML assinados na
  homologação local: ADR-0033, ajuste v2.9 (plano v2.9)
- **Fonte lida (somente leitura):** `Ziip Clould\Ziip-cloud-source` (C#/.NET 8): README, SECURITY, `Nfse.Domain`,
  `Nfse.Application`, `Nfse.Infrastructure` (Sefin, Xml, Security, Adn, Nfe, Persistence) e `tests\Nfse.Tests`.
- **Destino lido:** `commercial/sigsan-billing`, `shared/sigsan-security`, `docs/levantamento/11`, `14` e `17`,
  `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`, ADR-0002.
- **Nomes:** módulos, pacotes e propriedades `sigsan` passam a `ziipsan` na renomeação do P0 (ADR-0016, plano P0.33);
  por exemplo, `commercial/sigsan-fiscal` vira `commercial/ziipsan-fiscal`.
- **Idioma:** tabelas, colunas, estados, propriedades e classes Java em inglês (plano §8, Idioma, decisão do dono do
  produto de 12/10/2026); nomes do C# do Ziip Cloud e campos dos leiautes oficiais ficam como na origem.

Regra deste documento: o que não está nos fontes nem nos documentos lidos aparece como **a confirmar (NT/manual
oficial)**.

---

## 1. Inventário do motor de origem

| Componente (C#) | Responsabilidade | Dependência .NET | Equivalente Java | Esforço |
|---|---|---|---|---|
| `Nfse.Domain` (`DpsId`, `ChaveNfse`, `CnpjAlfanumerico`, enums) | Id da DPS com 42 posições (IBGE 7 + tipo 1 + inscrição 14 + série 5 + número 15); CNPJ alfanumérico | nenhuma | `record` Java 21; `DocumentNumber` de `sigsan-core` já trata CNPJ alfanumérico | P |
| `MotorFiscal` | ISSQN (2% a 5%), IRRF, CSRF, INSS, PIS/COFINS próprios, IBS/CBS informativos; arredondamento half-even | `decimal` | `BigDecimal` com `setScale(2, HALF_EVEN)` | P |
| `EmissaoService` | Numeração, montagem, assinatura, persistência antes da transmissão, reenvio automático (E0120 sem IM; erro de esquema IBS/CBS sem o grupo), reconciliação pós-timeout por `HEAD /dps/{id}`, substituição | `ILogger` | serviço Spring + worker de outbox | M |
| `EventosService` | Cancelamento e101101; Id `PRE` + chave (50) + tipo (6); trata E0840 como "já cancelada" | `SecurityElement.Escape` | builder de evento + escape XML | P |
| `DpsXmlBuilder` | XML da DPS por *string template*; versão 1.00 ou 1.01 conforme o grupo IBSCBS; ordem `tpRetISSQN`/`pAliq` muda entre versões | nenhuma | JAXB (`jakarta.xml.bind`) gerado do XSD oficial + validação `javax.xml.validation` | M |
| `XmlDsigSigner` | XMLDSig enveloped, C14N 1.0, RSA-SHA256 (NFS-e) e RSA-SHA1 (NF-e 55); anti-XXE; verificação com uma única `Signature` | `System.Security.Cryptography.Xml` | `javax.xml.crypto.dsig` (JSR 105, embutido no JDK) ou Apache Santuario | M |
| `SefinClient` | mTLS, TLS 1.2/1.3, revogação online, retry exponencial, XML gzip+Base64 em JSON, extração tolerante de erros | `SocketsHttpHandler`, Polly | `java.net.http.HttpClient` com `SSLContext` por tenant (`KeyManagerFactory` sobre `KeyStore` PKCS12 em memória), Resilience4j, Jackson, `GZIPOutputStream` | M |
| `SefinClientSimulado` + `SefinRouter` | Simulação padrão com idempotência por Id da DPS | nenhuma | implementação `simulator` da mesma porta | P |
| `AesGcmCertificateVault` | PFX + senha cifrados AES-256-GCM, AAD = tenant + referência; **grava em arquivo** | `AesGcm`, `X509Certificate2` | `FieldCipher` do `sigsan-security`, gravado em coluna | P |
| `CertificadoProvider` / `CertificadoLojaWindows` | Origem cofre ou repositório do Windows (A1 instalado e A3) | `X509Store` | não porta (seção 2) | — |
| `AdnClient` | Distribuição de NFS-e por NSU (mTLS) e simulador | `HttpClientHandler` | idem `SefinClient` | M (adiável) |
| `NfeDistClient` | Distribuição DF-e de NF-e 55 e evento de ciência 210210 | `XDocument` | — | fora do escopo |
| `DanfsePdfService`, `DanfeNfePdfService` | DANFSE/DANFE em PDF com QR Code | QuestPDF, Net.Codecrete.QrCodeGenerator | Apache PDFBox ou OpenPDF; JasperReports se o leiaute vier por modelo; QR por `io.nayuki:qrcodegen` (a biblioteca .NET é porte dela) ou ZXing | M |
| `NfseDbContext` | EF Core; índice único em `Dps.Identificador` e `NotaFiscal.ChaveAcesso` | EF Core, Npgsql | Spring Data JPA + Flyway (migrations de tenant), RLS via `register_tenant_table` | P |
| `AuditTrail` | Trilha encadeada por hash | EF Core | `HashChain` já existe em `grs/sigsan-mtr` | P |
| Testes (`PrototipoTests`, `CnpjAlfanumericoTests`) | Id da DPS, motor fiscal (exemplo de Florianópolis, IBS/CBS, Zona Franca), cofre, assinatura, XXE, eventos | xUnit | JUnit 5 sobre os mesmos vetores (seção 5) | P |

Observações dos fontes que pesam no porte:

1. Leiaute da DPS "simplificado" (README), sem validação por XSD; não há XSD nem XML de referência no repositório.
2. O grupo `IBSCBS` leva só classificação (`CST`, `cClassTrib`, `cIndOp`); segundo o builder, os valores são apurados
   pela Sefin e o cálculo local serve ao DANFSE. Os códigos de reserva (`000`, `000001`, `100301`) são escolha do Ziip
   Cloud: **a confirmar (NT/manual oficial)** para saneamento.
3. CBS 0,9% e IBS UF 0,1% são padrão no código (`Dtos.cs`); o R40 e o ADR-0002 proíbem isso, e em 2027 os percentuais
   mudam.
4. A numeração faz `empresa.ProximoNumeroDps++` em memória, sem trava: emissões simultâneas colidem no índice único sem
   tratamento.
5. `IAmbienteAtual` é *singleton* em arquivo: o ambiente vale para a instalação inteira, não por tenant.
6. O retry do Polly cobre o `POST /nfse`. Se a resposta se perder num 5xx após o processamento, a retentativa pode receber
   rejeição de duplicidade e a DPS fica "rejeitada" sem reconciliar. Código da rejeição: **a confirmar**.
7. Sem contingência e sem processamento assíncrono.

## 2. O que porta, o que se adapta, o que não serve

**Porta como está (regra e teste):** `DpsId`; `MotorFiscal`; evento e101101; reconciliação antes de retransmitir; DPS
persistida antes do envio; reenvios E0120 e IBS/CBS com o mesmo número; extração tolerante de erros; gzip+Base64 em
UTF-8 sem BOM (E1229); `dhEmi` em America/Sao_Paulo (E0008); validação de chave e Id antes de compor URL; anti-XXE.

**Adapta:**

| Item | Adaptação |
|---|---|
| Cofre | Sai do arquivo e vai para coluna de `fiscal_certificates`, cifrado por `FieldCipher` com contexto `fiscal_certificates.pfx`. `FieldCipher` trabalha com `String`: cifrar o PFX em Base64 ou acrescentar variante `byte[]`. Depende do `TenantKeyProvider`: sobre o cofre local até o lançamento (plano P0.28, ADR-0033) e sobre o AWS KMS a partir dele (ADR-0011; plano LC.4); **ainda não existe** (diário, R1) |
| Abertura do certificado | `KeyStore.getInstance("PKCS12")` em memória; zerar `char[]` da senha e `byte[]` do PFX após o uso; nada em `javax.net.ssl.keyStore` global |
| mTLS | `SSLContext` por emitente, em cache com TTL curto e invalidação na troca do certificado; *truststore* próprio com as ACs ICP-Brasil (o C# embute `icp-brasil-acs.pem`) somado ao `cacerts`; revogação com `PKIXRevocationChecker`. Se a cadeia dos servidores da Sefin/SEFAZ exige ICP-Brasil: **a confirmar** |
| Ambiente | Por tenant e por emitente, com trava de plataforma (seção 3.6) |
| Builder | Classes geradas do XSD oficial e validação por schema antes de assinar, que é o limite 1 do próprio README |
| Alíquotas e códigos IBS/CBS | Vêm de `tax_rate` com vigência e aceite (R40, ADR-0002), nunca de constante |
| Arredondamento | O C# usa half-even para NFS-e ("conforme NT 007/2026"; verificado em 22/10/2026: a NT 007/2026 da SE/CGNFS-e aplica half-even e tolerância de R$ 0,01 apenas a PIS e Cofins, vPis e vCofins, e não a IBS e CBS); o `TaxWithholding` do billing usa `HALF_DOWN`, herdado do legado sem norma (relatório 08, E-20). A regra precisa ser definida por documento e tributo: **a confirmar (NT/manual oficial)**; para IBS e CBS, conferir os anexos da NT 004, as NTs 008 e 009 e o manual da NFS-e |

**Não serve ao Ziip SAN:**

- **Repositório de certificados do Windows e A3 em token/cartão.** O SaaS roda em Linux, sem leitora no servidor. Opções:
  (a) A1 por emitente, cifrado com a chave do tenant, decifrado só em memória no *worker* fiscal (recomendado para o
  piloto); (b) chave em HSM via PKCS#11 (`SunPKCS11`), viável sobre o HSM gerenciado da AWS, se ele atender em `sa-east-1` com custo aceitável (hospedagem decidida
  no ADR-0011, aceito; a confirmar);
  (c) certificado em nuvem de prestador credenciado ICP-Brasil com assinatura remota, que resolve a assinatura XML mas
  não o mTLS sem `KeyManager` próprio; viabilidade técnica e contratual **a confirmar**.
- **Certificado de demonstração autoassinado** como recurso de produto (no Java, só em teste); licenciamento,
  standalone/SQLite, Asaas e `NfeDistClient`. A distribuição ADN fica para depois da primeira produção, no lançamento
  comercial (ADR-0033).

## 3. Desenho alvo em Java

### 3.1 Módulos e camadas

Módulo novo `commercial/sigsan-fiscal`, dependente de `sigsan-core` e `sigsan-security`. O `sigsan-billing` não
depende dele: publica "conta fechada" no outbox e o fiscal consome.

Camadas: `domain` (documento, emitente, série, evento), `rules` (motor tributário puro, Id da DPS, chave de acesso,
arredondamento), `port` (emissor, assinador, cofre, repositório de XML), `application` (emissão, eventos,
reconciliação, numeração), `adapter` (`nfse`, `nfag`, `sim`, `xml`, `http`) e `persistence` (JPA e migrations de
tenant).

```java
public interface FiscalDocumentIssuer {
    FiscalDocumentType type();                   // NFAG, NFSE_NACIONAL
    AuthorizationResult authorize(SignedDocument doc, Issuer issuer);
    Optional<AuthorizationResult> query(DocumentIdentifier id, Issuer issuer); // reconciliação
    EventResult registerEvent(SignedEvent event, Issuer issuer);               // cancelamento, substituição
}
```

O roteamento escolhe a implementação por `(tenant, issuer, type, environment)`. O simulador é o padrão de todo ambiente
que não seja produção, como no C#.

### 3.2 Multi-tenant e certificado

- `fiscal_certificates(tenant_id, issuer_id, pfx_ciphertext, password_ciphertext, thumbprint, cnpj, not_after)`, com RLS
  forçada; no upload, exige chave privada, validade e CNPJ igual ao do emitente.
- Nunca em disco, log, API ou `audit_log` (que copia a linha inteira, segundo o diário). Só o *worker* fiscal decifra.
- No ambiente local, até o lançamento (ADR-0033, ajuste v2.8), não há certificado de cliente. O único certificado real
  é o e-CNPJ da própria Ziip/TGP, usado só em homologação (§3.6): fica no cofre local, fora do banco, e é lido pela
  implementação local do cofre de certificados; `fiscal_certificates` não recebe o PFX nem a senha dele, e todo acesso
  ao certificado é registrado. O desenho acima, com o PFX cifrado em coluna, vale para a produção (plano LC.4).

### 3.3 Outbox e emissão assíncrona

- Não há outbox no código; o plano v2 prevê a outbox no core `V8` (P1.9, ADR-0014: uma por banco, no lugar do antigo
  `V11_x` por tenant) e `job_runs` no core `V10` (P2.1); o fiscal usa a mesma outbox.
- *Worker* com `FOR UPDATE SKIP LOCKED`, lote por tenant, Resilience4j (retry só para erro transitório; *circuit
  breaker* e *bulkhead* por autorizador e UF, para um tenant não esgotar a cota dos outros).
- Estados: `RESERVED → SIGNED → TRANSMITTED → AUTHORIZED | REJECTED | PENDING_RECONCILIATION`, mais `CANCELLED`,
  `REPLACED` (substituído) e `VOIDED` (inutilizado).
- Timeout ou 5xx: nunca retransmitir sem consultar (`HEAD /dps/{id}` na NFS-e; consulta equivalente na NFAg-e: **a
  confirmar (MOC NFAg)**). A rejeição por duplicidade também leva à reconciliação.

### 3.4 Numeração sem lacuna e idempotência

- `fiscal_series(tenant_id, issuer_id, model, series, next_number)` com `SELECT ... FOR UPDATE` na mesma transação
  que grava o documento em `RESERVED`. Número nunca volta ao estoque.
- "Sem lacuna" significa que todo número reservado tem destino registrado: autorizado, rejeitado ou inutilizado. Se a
  NFAg-e permite reutilizar número rejeitado e como se inutiliza faixa: **a confirmar (MOC NFAg)**. Se a NFS-e Nacional
  aceita lacuna na DPS: **a confirmar (manual do contribuinte)**.
- Idempotência por `UNIQUE (tenant_id, source_type, source_id, document_type)`: a conta (`bill_id`, como pede o R39) gera no
  máximo uma NFAg-e viva; reprocessar o ciclo não emite de novo. Também ficam únicos o Id da DPS e a chave de acesso.
- Emissão em campo (ADR-0031): se o leiaute admitir chave gerada fora do autorizador, o servidor reserva uma faixa de
  numeração por aparelho e série no pacote da rota; número usado volta na sincronização, sobra é inutilizada, e a faixa
  de aparelho bloqueado (ADR-0030) é inutilizada no fechamento do ciclo. A conta de campo é única por `task_id`.

### 3.5 Eventos

Cancelamento (porte direto do e101101), substituição (DPS com `subst/chSubstda`; o `cMotivo` fixo em 1 no C# é **a
confirmar**) e, na NFAg-e, cancelamento e inutilização (R39). Códigos de evento, prazos e regra para a conta já paga:
**a confirmar (MOC NFAg)**. Todo evento passa pelo outbox, com motivo, autor e aprovação dupla para cancelamento em
lote.

### 3.6 Ambientes e trava de produção

- Ambiente por emitente: `SIMULATED`, `HOMOLOGATION` (produção restrita na NFS-e; homologação SVRS/SEFAZ na NFAg-e),
  `PRODUCTION`.
- `tpAmb=1` exige três condições: `ziipsan.fiscal.production-allowed=true` na implantação (falsa em dev, CI e
  homologação, e em todo perfil do ambiente local até o lançamento comercial, ADR-0033), *flag* do tenant aprovada por
  dois papéis distintos e certificado ICP-Brasil válido. No ambiente local e nos *clusters* de teste, o egresso para os
  hosts de produção da Sefin e das SEFAZ fica bloqueado na rede.
- A produção restrita da NFS-e Nacional e a homologação da SEFAZ (`tpAmb=2`) são usadas a partir do ambiente local,
  sem emissão real, com o e-CNPJ da própria Ziip/TGP (decisão do dono do produto, plano v2.8). É exceção explícita à
  regra de nenhum dado pessoal real na base local (ADR-0033, item 5, ajuste v2.8): o CPF contido no certificado é do
  responsável pela empresa, que consente por escrito, e não é dado de cliente; o certificado fica no cofre local, fora
  do banco, com acesso registrado (§3.2); o XML assinado leva o certificado público, e os XML de homologação gravados
  no ambiente local entram na mesma exceção e no mesmo consentimento. Se a homologação da NFAg-e aceita o CNPJ da
  Ziip/TGP como emitente de teste do modelo 75: **a confirmar (MOC NFAg e credenciamento na UF)**; se não aceitar, a
  forma da P2.6 volta ao dono do produto.
- Guarda dos XML assinados na homologação local (ADR-0033, ajuste v2.9; plano P1.8 e P3.7): o documento emitido em
  `HOMOLOGATION` com o certificado da Ziip/TGP grava o XML só numa área separada da base local, fora das tabelas do
  produto (inclusive `fiscal_documents_xml`, §3.8), da cópia com retenção imutável e dos backups locais. Ao fim de cada
  ciclo de testes (rodada de homologação encerrada com resultado registrado no diário), os XML da área são apagados, com
  a exclusão registrada (ciclo, data, quantidade e hash do lote); só ficam os vetores de referência sem assinatura, sem
  o elemento `Signature` e sem o certificado (§5). O teste de varredura da base local recusa XML com o certificado da
  Ziip/TGP fora da área separada ou depois do fim do ciclo.

### 3.7 Contingência

O C# não tem. Mínimo: fila com *backoff*, painel de pendências e reconciliação quando o autorizador volta. Emissão em
contingência da NFAg-e e entrega da conta antes da autorização: **a confirmar (MOC NFAg)**; até lá, nenhuma conta sai
como documento fiscal sem autorização.

Na emissão simultânea em campo vale o ADR-0031: a conta é calculada no aparelho pelo mesmo módulo Java do servidor e
recalculada na sincronização; se o leiaute admitir contingência com chave gerada fora do autorizador, a conta sai
impressa com a chave e o QR Code de contingência e a NFAg-e é autorizada depois, com os valores impressos; o
certificado nunca vai ao aparelho; havendo divergência, a correção é retificação (P2.20), nunca alteração silenciosa.
Se o leiaute exigir XML assinado no momento da emissão, a forma volta ao dono do produto antes da ED-19.

### 3.8 XML autorizado e retenção

- `fiscal_documents_xml(tenant_id, document_id, xml_gzip, sha256, received_at, source)` só com INSERT (*trigger*
  recusa UPDATE e DELETE, inclusive para o papel da aplicação); o hash entra na `HashChain`. `source` indica a origem:
  `ZIIPSAN` (autorizado pela emissão do Ziip SAN) ou `LEGACY_IMPORT` (importado na migração, com o sistema ou emissor
  de origem).
- XML anteriores à primeira produção (ADR-0022, item 7; plano P2.19): o importador confere assinatura, protocolo de
  autorização, emitente igual ao do tenant e duplicidade pela chave de acesso; o aceito é gravado como recebido, sem
  regerar nem reformatar, e segue a mesma retenção imutável; o recusado vai para o relatório do lote. No ambiente local
  o importador é testado só com XML sintéticos assinados com certificado de teste; os XML reais do piloto, que trazem
  dado pessoal do destinatário, são importados só na produção, no lançamento (plano LC.10; ADR-0033).
- No ambiente local, `fiscal_documents_xml` e a cópia WORM recebem só XML do simulador ou sintéticos assinados com
  certificado de teste; os XML autorizados na homologação com o e-CNPJ da Ziip/TGP ficam fora delas, na área separada
  do §3.6, porque são apagados ao fim de cada ciclo de testes (ADR-0033, ajuste v2.9). A restauração do XML autorizado
  é testada com os sintéticos.
- Cópia com bloqueio de retenção (WORM): até o lançamento, no emulador de S3 local (exceção do ADR-0010, ajuste v2.7;
  plano P2.18); a partir dele, em S3 com Object Lock na hospedagem decidida (ADR-0011, aceito; plano LC.5).
- Prazo de retenção é parâmetro do tenant (G6), com piso de 120 meses para o histórico de faturamento (ADR-0015); o
  C# cita "5+ anos"; prazo fiscal **a confirmar**.
- O XML autorizado é servido no portal do consumidor e na área do tenant exatamente como gravado, com conferência do
  SHA-256; nunca é regerado (ADR-0022).

## 4. NFAg-e

**O que os documentos do projeto dizem** (relatório 11, R39 a R44; relatório 14):

- **Modelo 75**, projeto nacional com ambiente na SVRS (`dfe-portal.svrs.rs.gov.br/Nfag`): XML assinado e autorizado
  pela SEFAZ, DANFAG com QR Code, grupos IBS/CBS, consulta por chave. Anexo II (DANFAG) v1.02 de 25/08/2026; NT
  2026.004 RTC citada.
- Obrigatória em **01/12/2026** (Ato Conjunto RFB/CGIBS nº 4/2026, quatro fontes secundárias); a SVRS ainda mostrava
  "minuta" em 12/10/2026. **A confirmar no DOU.** (verificado em 22/10/2026: data confirmada pela Receita Federal e pelo texto do ato, art. 1º, XVI; o documento foi instituído pelo art. 114 do Decreto 12.955/2026 e pelo regulamento do IBS, e não há Ajuste SINIEF que o institua.)
- Substitui a Nota Fiscal/Conta de Água; o destino do modelo 29 (código do SPED Fiscal) **não está confirmado**.
- Saneamento na alíquota padrão (LC 214); 2026 com IBS 0,1% e CBS 0,9% em teste; 2027 com CBS plena e IBS
  0,05% + 0,05%; *cashback* do CadÚnico na cobrança (R41); fato gerador alterado pela LC 227/2026, texto não lido (R42).
- Parâmetros previstos: `fiscal_doc_type` (`NFAG`, `LEGACY_29`, `NONE`), `sefaz_uf`, `nfag_credentials_ref`,
  `tax_profile`, `tax_rate[]`.

**O que falta saber:** MOC e XSD vigentes; protocolo (SOAP ou REST) e modo (síncrono ou por lote); formato da chave de
acesso; algoritmo de assinatura (a NF-e 55 usa RSA-SHA1, conforme o próprio `XmlDsigSigner`); eventos, inutilização e
contingência; se SAAE (autarquia municipal) é obrigado; se religação e ligação nova vão na NFAg-e ou em NFS-e (R44);
credenciamento por UF; se a conta pode ser o próprio DANFAG; base de cálculo do IBS/CBS na fatura. Tudo **a confirmar
(MOC NFAg/ato oficial)**.

**Reaproveita da NFS-e Nacional:** cofre A1 e abertura PKCS12; fábrica mTLS; assinador (com SHA-1 ou SHA-256 por
documento); validação XSD; outbox, numeração, idempotência e reconciliação; tabelas de classificação IBS/CBS (`CST`,
`cClassTrib`), que são nacionais da RTC (a confirmar se a NFAg-e usa as mesmas); `TaxEngine` com vigência;
simulador; trilha de auditoria.

**É novo:** leiaute e builder do modelo 75; cliente do autorizador por UF; chave de acesso e DV; DANFAG; inutilização;
contingência; vínculo com a conta (`bill_id`, chave na cobrança para o *split payment*, R17); *cashback*;
`tax_event_date` separado de vencimento e emissão (R42); volume. A meta do relatório 17 é 100 mil contas em menos de
2 h por tenant; a 10 autorizações por segundo isso dá cerca de 2,8 h, então o lote ou o paralelismo aceito pelo
autorizador decide se a meta é viável (**a confirmar**).

Ponto técnico do Java 21: desde o JDK 17 o modo de validação segura do JSR 105 vem ligado e recusa assinatura XML com
SHA-1. Se a NFAg-e exigir RSA-SHA1, a assinatura funciona, mas a autoverificação local precisa de política própria e
restrita a esse documento.

A restrição de banco `total_value = water + sewer + debits − credits − taxes` em `bills` (V4_1) trata tributo como
retenção. O destaque de IBS/CBS (informativo em 2026, por dentro ou por fora do preço depois) obriga a revisá-la antes
do R4: **a confirmar (tributarista do tenant e MOC)**.

## 5. Estratégia contra divergência C# × Java

1. **Repositório de vetores compartilhado** (`fiscal-contract-fixtures`), versionado por NT e versão de schema
   (ex.: `nfse/NT-007-2026/schema-1.01-20260727/`). Cada caso traz entrada (JSON), resultado tributário esperado,
   XML não assinado esperado e, quando houver, XML de referência oficial. Vetor tirado da homologação local com o
   e-CNPJ da Ziip/TGP entra sem assinatura, sem o elemento `Signature` e sem o certificado; o XML assinado é apagado ao
   fim do ciclo de testes (§3.6; ADR-0033, ajuste v2.9).
2. **Vetores iniciais** tirados dos testes C#: nota de Florianópolis (R$ 5.440,00: ISS 108,80, IRRF 81,60, CSRF 252,96,
   líquido 5.105,44), IBS/CBS sobre R$ 10.000,00 (CBS 90,00, IBS UF 10,00), Zona Franca, Simples, half-even
   (0,125 → 0,12; 0,135 → 0,14), Id da DPS com CPF e CNPJ alfanumérico, Id do evento `PRE` + 56.
3. **Comparação de XML** por C14N após normalizar `dhEmi` e `verAplic`. O builder C# lê o relógio direto
   (`AgoraBrasilia()`); para byte a byte é preciso um relógio injetável no Ziip Cloud, mudança a pedir lá, não neste
   porte.
4. **Assinatura cruzada:** o Java verifica XML assinado pelo C# e o C# verifica o assinado pelo Java. Como RSA PKCS#1
   v1.5 é determinístico, com a mesma entrada canônica e o mesmo certificado de teste o `SignatureValue` tem de ser
   idêntico; diferença indica erro de canonicalização ou de *whitespace*.
5. **Rastreio de NT:** tabela `fiscal_layout_versions(document_type, nt, schema, valid_from, valid_to)` e anotação
   `@TechnicalNote` nas classes de regra; mudança no C# (ex.: reescrita do grupo IBSCBS em 06/08/2026) só entra no Java
   com vetor novo. A matriz NT × implementação fica neste diretório.
6. **CI:** JUnit roda os vetores em todo *build*; o C# roda os mesmos arquivos quando o Ziip Cloud mudar. Divergência
   bloqueia o *merge*.

## 6. Plano em etapas

| Fase | Entregas | Critério de aceite |
|---|---|---|
| **P0** (out–nov/2026) | Esqueleto `sigsan-fiscal`; `TaxEngine` e `DpsId` com vetores; assinador JSR 105 com testes de XXE e assinatura cruzada; `CertificateVault` sobre `FieldCipher` (bloqueado até o `TenantKeyProvider` sobre o cofre local, P0.28; o KMS só no lançamento, LC.4); simulador; trava de produção; obtenção de MOC/XSD da NFAg-e e do XSD da DPS; confirmação do ato no DOU | Vetores do C# passam no Java; nenhum PFX fora da coluna cifrada; *build* sem `production-allowed` não gera `tpAmb=1` |
| **P1** (nov/2026–jan/2027) | NFS-e Nacional completa (emissão, cancelamento, substituição, reconciliação) com builder do XSD; outbox, numeração com trava e idempotência; mTLS com *truststore* ICP-Brasil; builder e validador NFAg-e contra o XSD no simulador | Emissão, cancelamento e substituição aceitos na **produção restrita**, a partir do ambiente local, com o e-CNPJ da própria Ziip/TGP lido do cofre local (§3.6); XML assinados só na área separada e apagados ao fim de cada ciclo de testes, com os vetores sem assinatura guardados (§3.6, §5); teste de concorrência sem número repetido nem perdido; queda simulada da Sefin sem nota duplicada |
| **P2 Ciclo** (jan–mar/2027) | Integração com o ciclo do R4 (conta fechada → NFAg-e); cliente do autorizador por UF; DANFAG; cancelamento e inutilização; contingência conforme MOC; teste de carga | NFAg-e autorizada em **homologação** da SEFAZ do piloto, a partir do ambiente local, com o e-CNPJ da Ziip/TGP como emitente de teste (§3.6), em amostra de contas anonimizadas na origem, sem dado pessoal real na base local (ADR-0033), com os XML assinados apagados ao fim de cada ciclo de testes (§3.6); ciclo reexecutado sem segunda NFAg-e; carga do tenant sintético dentro da meta ou desvio documentado |
| **P3** (mar–mai/2027) | Painel de rejeições e reprocesso; exportação WORM; *runbook*; pentest com foco no cofre e no isolamento; alerta de vencimento do certificado | Pentest sem achado alto aberto; restauração do XML autorizado testada com XML sintéticos (§3.8); exclusão dos XML da homologação ao fim de cada ciclo conferida pelo teste de varredura (§3.6); *runbook* exercitado |
| **Homologação local do piloto** (jun–jul/2027; antes "go-live piloto") | Faturamento paralelo local com dados sintéticos ou anonimizados, comparado com o sistema do piloto (ADR-0033) | O Ziip SAN **não emite em produção** (o legado é o emissor); só simulador ou homologação da SEFAZ (`tpAmb=2`), a partir do ambiente local |
| **Lançamento comercial** (a partir de outubro de 2028, opção A do plano §2.4; corte por volta de fevereiro de 2029) | Carga real, paralelo com dado real e corte na AWS (LC.10 a LC.13) | Durante o paralelo real o legado continua emissor; produção (`tpAmb=1`) liga no corte (LC.13), com aprovação dupla registrada e `production-allowed=true` só na implantação de produção |
| **P4, trilha de editais** (ED-19) | Emissão simultânea em campo conforme o ADR-0031: faixa de numeração por aparelho, autorização posterior em contingência, inutilização das sobras, fila de divergências ligada à retificação | Vetores comuns iguais no JVM e no emulador Android; reenvio da mesma tarefa não gera segunda NFAg-e; contingência testada na homologação da SEFAZ conforme o leiaute vigente |

**Riscos e mitigação:** obrigatoriedade em 01/12/2026 contra a primeira produção só no lançamento comercial, por volta
de fevereiro de 2029 (o piloto precisa de outro emissor no intervalo, plano §1 e §5); MOC/XSD em minuta (builder gerado do XSD, vetores por versão);
KMS só no lançamento (cofre local com dados sintéticos ou anonimizados até lá; R1 sobre o cofre local na P0.28, que
precisa fechar antes do P1); certificado da homologação local com CPF do responsável (e-CNPJ da própria Ziip/TGP no cofre local, fora do banco, com
consentimento e acesso registrado, exceção do ADR-0033, ajuste v2.8; aceitação como emitente de teste da NFAg-e a
confirmar); XML assinados da homologação local com o mesmo CPF (área separada, apagados ao fim de cada ciclo de testes,
só vetores sem assinatura guardados, ADR-0033, ajuste v2.9);
contingência da NFAg-e indefinida para a emissão em campo (ADR-0031: conferir o manual antes da ED-19); arredondamento e base de IBS/CBS sem norma lida (regra
isolada e parametrizada, vetor oficial antes da homologação); emissão acidental em produção (seção 3.6); vazão do
autorizador no ciclo (carga no P2).

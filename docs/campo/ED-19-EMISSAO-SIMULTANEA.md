# ED-19 — Emissão simultânea e impressão térmica

Documento de desenho e de integração. O relatório honesto (item a item,
provas e pendências) é o `RELATORIO-ED-19.md` do pacote.

## 1. O artefato único: `ziipsan-billing-rules`

Módulo puro (só JDK): cálculo da conta de campo, consumo com virada,
fingerprint canônico, avaliador de divergência, pacote da rota, payload
Pix (BR Code), renderizador térmico ZPL/CPCL, política de lote (a cada N),
fila de retidas e regra de saúde da impressora. Sem Spring, sem JPA, sem
Android — por isso o MESMO JAR roda na JVM do servidor e dentro do app.

**Hash conferido, nos dois sentidos:**

1. No build do app, a tarefa `verifyBillingRulesHash`
   (`campo/billing-rules.gradle`) calcula o SHA-256 do JAR resolvido e o
   grava ao lado do APK.
2. Na montagem do pacote da rota, o servidor carimba
   `rulesArtifactSha256` (o hash do JAR que ELE usa) e a versão do
   contrato (`billing-rules/1`) no `RoutePackage`.
3. No aparelho, antes de aceitar a rota, o hash embarcado é conferido
   contra o do pacote (`ArtifactHash.requireMatch`); contrato diferente
   também recusa (`RoutePackage.requireCompatibleRules`). Artefato
   diferente não emite conta — não existe "calcular mesmo assim".

## 2. Vetores de contrato (`billing-contract-fixtures`)

Arquivo único `billing-contract-fixtures.json` dentro do artefato
(recurso) + cópia byte a byte no app
(`campo/src/field/billing-contract-fixtures.json`, hash SHA-256 idêntico
conferido no pacote). O teste JVM (`ContractFixturesTest`) e o teste
instrumentado Android (`BillingRulesContractInstrumentedTest`) executam
os mesmos 6 vetores em todo build; divergência em qualquer lado bloqueia
o merge. O gerador do diferencial noturno
(`ContractFixtures.differentialCases(10_000, seed)`) produz as 10 mil
ligações determinísticas a partir dos vetores-base — a comparação com o
emulador é do job noturno (CI), pendência declarada no relatório.

## 3. Fluxo da emissão simultânea

```
pacote da rota (task_id, retrato de tarifa, versoes de parametro,
                modelos termicos, N do lote, hash do artefato)
  -> aparelho confere hash + contrato
  -> leitura + foto: critica OCR da ED-18 (REVIEW segura a emissao)
  -> tipo exige assinatura (P3.34)? certificado no aparelho no nivel?
       nao -> CERTIFICATE_MISSING (retida; nada e "assinado" por simulacao)
  -> calculo pelo ziipsan-billing-rules (plugin BillingRules)
  -> impressao termica ZPL/CPCL (modelo da P2.30) na Zebra ZQ (Bluetooth)
  -> conta retida; envio a cada N imoveis e no fim da rota
  -> servidor: idempotencia por task_id -> portoes -> recalculo com o
     MESMO modulo -> igual: CONFIRMED | diverge: fila + alerta na Central
  -> aprovacao da divergencia (permissao da P2.20; dupla acima do limite)
     -> correcao por NOVA versao; a conta impressa nunca muda
```

A conta é única por `task_id`: o registro servidor tem UNIQUE
(tenant, task_id) e `bill_id` estável — reenvio não duplica conta nem
NFAg-e (a NFAg-e da P2.6 nasce por BILL + bill_id, com a idempotência
dela sobre a mesma origem).

## 4. Impressão térmica (Zebra ZQ, Bluetooth)

O leiaute NÃO é criado pela ED-19: o corpo vem do modelo do motor de
modelos da P2.30 (tipo + versão carimbados no pacote da rota; variáveis
do catálogo do tipo). O `ThermalRenderer` do módulo puro só traduz
modelo + documento para ZPL ou CPCL, acrescentando código de barras
(Code 128) e QR Pix (payload BR Code do `PixPayload`) quando o documento
os traz. Conta, comunicado de débito e comunicado de excesso usam o
mesmo renderizador — muda o modelo. Documento gerado grava modelo e
versão (`field_print_jobs`).

O transporte Bluetooth, o pareamento com a ZQ e a leitura do autotest do
cabeçote/bateria são do app (pontes `ThermalPrinterBridge`); o veredito
pré-rota é a regra pura `PrinterHealthRule`. Ensaio com a impressora
real: pendência de hardware (relatório).

## 5. Contingência da NFAg-e e forma fiscal

A contingência segue o leiaute de contingência registrado no §2.4
(dependência textual do aceite): esta entrega fixa o comportamento
independente do leiaute (motivo obrigatório, conta marcada, transmissão
pendente, nenhum estado autorizado fingido) em `NfageContingencyPolicy`.
A geração do documento em contingência pelo §2.4, a confirmação da forma
fiscal do ADR-0031 item 4 contra o manual e o teste na homologação da
SEFAZ são ensaios externos — pendências nomeadas no relatório, não
lacunas silenciosas.

## 6. POCs e itens de edital

POC-15, POC-16 e POC-21 (roteiro VISAN) e o item 2.11 do SAMAE dependem
do fluxo completo com hardware (aparelho + ZQ + ambiente SEFAZ de
homologação). Esta entrega cobre o software que os POCs exercitam; a
passagem dos POCs é ensaio — pendência declarada, nunca dada como feita.

## 7. Integração (passos no PC/CI do dono)

1. Registrar `commercial/ziipsan-billing-rules` em `<modules>` do POM
   raiz e `ziipsan-billing` passando a depender dele.
2. Numerar a migration pendente (`db/pendente/ED-19__*.sql`) com o
   próximo slot livre real da série tenant e movê-la para
   `db/migration/tenant/`.
3. Ligar as portas (`FieldBillStore`, fila, alertas da Central, correção
   da P2.20, montador do pacote da rota a partir da tarifa da P2.5 e dos
   modelos da P2.30) na raiz de composição.
4. `cap sync` + dependência Gradle do artefato no app (billing-rules.gradle),
   registro do plugin `BillingRules` no `MainActivity`/Capacitor.
5. Rodar `scripts/ci.sh` (CI total) + o job do emulador Android com os
   vetores de contrato e o diferencial noturno.

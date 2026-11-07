# Roteiro — Homologação fiscal a partir do ambiente local (P3.7)

> **Natureza deste documento.** Este roteiro é o aparato executável da P3.7. A
> homologação **real** — transmissão à SEFAZ em `tpAmb=2` com o e-CNPJ da própria
> Ziip/TGP — é **aceite externo**: só acontece no ambiente local do piloto, com o
> certificado real no cofre local e os endpoints reais. Nada neste roteiro, e
> nenhum teste deste pacote, substitui ou finge esse aceite. Resultado de rodada
> real só existe quando registrado no diário de bordo a partir de uma execução
> real.

## 0. Pré-condições (conferir antes de abrir qualquer ciclo)

1. Ambiente local completo (P0.27): PostgreSQL, Keycloak, emulador de S3 e
   **cofre local** no ar; perfis de homologação local ativos.
2. e-CNPJ da **própria Ziip/TGP** no cofre local, fora do banco, com o
   consentimento do responsável registrado (exceção do ADR-0033). **Nenhum
   certificado de cliente** no ambiente local. CNPJ e thumbprint esperados são
   lidos do cofre no ato — nunca escritos em arquivo do repositório.
3. Área separada definida **fora** do repositório, **fora** dos backups locais
   (P0.28) e **fora** do armazenamento imutável (P2.18):
   `ZIIPSAN_HOMOLOGACAO_AREA=<volume/pasta separados>`.
4. Motores prontos: NFS-e nacional (P1.8/P3.6) e NFAg-e (P2.6) com as portas de
   assinatura (cofre) e transmissão (SEFAZ) implementadas e ligadas — sem elas,
   os documentos param em `RESERVED`/`GERADA_NAO_TRANSMITIDA` por desenho, e
   **não há ciclo a abrir**.
5. Guarda de entrada: o orquestrador do ciclo (`HomologacaoCicloService`)
   exige `HomologacaoFiscalGuard` (`tpAmb=2` + e-CNPJ da Ziip/TGP) na
   **abertura** do ciclo e de novo em **cada gravação de XML**, e a área
   separada confere o thumbprint de cada XML contra o esperado do cofre —
   XML de certificado de cliente é recusado na gravação (correção D3). As
   operações executadas diretamente pelas portas dos motores (emissão,
   cancelamento, substituição, transmissão) fora do orquestrador devem ligar
   a **mesma guarda** na integração, antes de assinar/transmitir: esse é o
   ponto de fiação declarado, não uma garantia deste pacote. Se a guarda
   recusar, o ciclo não começa — corrigir a causa, nunca contornar a guarda.

## 1. Abrir o ciclo

```bash
scripts/homologacao-fiscal/iniciar-ciclo.sh <id-do-ciclo>   # ex.: 2026-11-ciclo-01
```

Cria a pasta do ciclo na área separada e o manifesto (`tpAmb=2`). A partir
daqui, **todo** XML assinado neste ciclo é gravado **só** nessa pasta
(`AreaSeparadaHomologacao`, esquema `homologacao-fiscal-local://area-separada/`).

## 2. Executar as etapas (registrar cada uma no ciclo ao concluir)

| # | Etapa (`CicloHomologacao.Etapa`) | O que executar de verdade | Evidência |
|---|---|---|---|
| 1 | `EMISSAO_NFSE` | Emitir NFS-e de serviço avulso na produção restrita/homologação com o e-CNPJ da Ziip/TGP; guardar o XML assinado na pasta do ciclo | chave/protocolo no registro da rodada |
| 2 | `CANCELAMENTO_NFSE` | Cancelar uma NFS-e do ciclo pelo evento próprio | protocolo do evento |
| 3 | `SUBSTITUICAO_NFSE` | Substituir uma NFS-e do ciclo (DPS substituta) | vínculo substituta × substituída |
| 4 | `CICLO_FATURAMENTO_NFAGE` | Rodar um ciclo de faturamento emitindo NFAg-e (modelo 75) em `tpAmb=2` na SEFAZ do piloto; um XML por conta, sem segunda NFAg-e na reexecução (P2.6) | protocolos por conta |
| 5 | `ENSAIO_CONTINGENCIA` | Ensaio de contingência (`EnsaioContingencia`): com o serviço de homologação indisponível (janela real ou indisponibilidade provocada no dublê do ambiente), emitir em contingência com motivo e retransmitir após o retorno | registro do ensaio (JSON) |

Antes do expurgo, de cada documento que deva virar vetor de referência:
gerar o vetor com `VetorReferencia.sanearParaFixture(...)` e gravá-lo em
`commercial/ziipsan-fiscal/src/test/resources/fiscal-contract-fixtures/`
(sem `Signature`, sem certificado — §5). Conferir os acessos ao certificado:
`ConferenciaAcessosCofre.exigirConferido(acessosDoCofre, assinaturasDoCiclo)`
— uma assinatura sem acesso registrado no cofre reprova o fechamento.

## 3. Ensaio de restauração (P2.18) — só sintéticos de teste

Em paralelo ao ciclo (não usa XML da homologação): gerar XMLs **sintéticos**
assinados com o **certificado de teste** do ambiente, gravá-los no
armazenamento imutável local (emulador de S3, interface da P0.45) com hash na
`HashChain`, apagar a cópia de trabalho e restaurar por
`RestauracaoXmlAutorizado.restaurar(uri)` — o SHA-256 conferido decide.
XML com o certificado da Ziip/TGP no imutável é violação e o restaurador o
recusa: ele nunca entra lá.

## 4. Encerrar, expurgar e varrer (nesta ordem, sem atalhos)

1. Registrar o **resultado da rodada no diário de bordo** (emissão,
   cancelamento, substituição, faturamento com NFAg-e, contingência — com
   protocolos e rejeições reais da execução).
2. Encerrar e expurgar:
   ```bash
   RESULTADO_DIARIO="DIARIO-DE-BORDO.md: <referência da entrada>" \
   scripts/homologacao-fiscal/encerrar-e-expurgar-ciclo.sh <id-do-ciclo>
   ```
   Apaga os XMLs da área e grava a **exclusão registrada** (ciclo, data,
   quantidade, hash do lote e a referência do resultado no diário — os 5
   campos do record `RegistroExclusao`) fora da área. Sem resultado no
   diário, o script (e `AreaSeparadaHomologacao.expurgar`) recusa. Se houver
   qualquer arquivo além dos XMLs e do manifesto na pasta do ciclo, o script
   recusa **antes** de registrar ou apagar qualquer coisa.
3. Varrer tudo (repositório, backups locais, área separada, imutável):
   ```bash
   ZIIPSAN_THUMBPRINT_ZIIP="<thumbprint lido do cofre>" \
   scripts/homologacao-fiscal/varredura-pos-ciclo.sh <raízes...>
   ```
   A varredura detecta o certificado **de verdade** (correção D1): extrai o
   `X509Certificate` de cada arquivo, decodifica base64→DER e compara o
   thumbprint calculado (SHA-1 e SHA-256) com o esperado — não é busca
   textual pelo thumbprint (o thumbprint nunca aparece como texto num XML
   real; a busca textual permanece só como fallback fechado para
   marcadores/registros). Qualquer ocorrência do certificado da Ziip/TGP
   depois do fim do ciclo — inclusive na área separada — reprova o
   fechamento (no CI, a mesma decisão e o mesmo algoritmo são de
   `VarreduraHomologacao.exigirLimpo(achados, cicloExpurgado=true)`).
4. Anexar ao diário: referência da exclusão registrada e o resultado da
   varredura. O ciclo só está fechado com os dois.

## 5. O que este roteiro NÃO faz

- Não transmite nada sozinho: quem transmite são os motores da P1.8/P2.6/P3.6
  com as portas ligadas ao cofre e à SEFAZ reais do ambiente do piloto.
- Não gera XML autorizado de exemplo: os únicos XMLs versionados são os
  vetores sintéticos sem assinatura de `fiscal-contract-fixtures`.
- Não dispensa o aceite externo: a P3.7 só fecha quando uma rodada real,
  executada por este roteiro, estiver registrada no diário.

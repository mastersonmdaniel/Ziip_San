# Kit de habilitação, INPI e escrituração (P0.44)

Documento/checklist da parte agente da P0.44, fora do caminho crítico. **Tudo do lado
da empresa é AÇÃO DO DONO: nada abaixo afirma que qualquer item foi feito.** O
repositório não comprova nenhum documento da empresa (certidões, balanço, contrato
social, INPI, e-CNPJ): cada item está como "não comprovado" até o dono anexar a
evidência. Fontes: levantamento 18h §2.2 (que remete ao 18c §2.2, Edital SAMAE São
Ludgero 8.2, p. 11–13), plano P0.44, ADR-0023 item 6, ED-02.

## 1. Checklist — empresa licitante

| # | Item | Status | Evidência a anexar (dono) |
|---|---|---|---|
| 1 | Empresa licitante definida, com CNAE compatível com o objeto (software/saneamento) | AÇÃO DO DONO — não comprovado | Razão social, CNPJ, CNAE, contrato social vigente + última alteração (18c §2.2 a, e) |
| 2 | Contador contratado e escrituração desde o exercício de 2026 em curso | AÇÃO DO DONO — não comprovado | Contrato com o contador; escrituração 2026 em dia |
| 3 | Balanços de 2027 e 2028 (exigidos nas licitações a partir do 3º trimestre de 2029, opção A do plano §2.4) | AÇÃO DO DONO — futuro | Balanço patrimonial, DRE e demais demonstrações dos 2 últimos exercícios, assinados por contador (18c §2.2 m). Empresa criada no exercício usa balanço de abertura (art. 65, §1º, Lei 14.133/2021); com menos de 2 anos, o último exercício (aceito pelo SG) |
| 4 | Pedido de registro do programa no INPI protocolado | AÇÃO DO DONO — não comprovado | Número do protocolo INPI + comprovante |
| 5 | e-CNPJ obtido (o da Ziip/TGP também serve à homologação fiscal local, guardado no cofre local, plano §2.4) | AÇÃO DO DONO — não comprovado | Certificado no cofre local (nunca no repositório, nunca em chat); acesso registrado |
| 6 | Relatório de certificações do provedor arquivado (P0.26: ISO 9001, 27001 e 27017 são certificações; SOC 1 e SOC 3 são relatórios de asseguração, escopo `sa-east-1`, via AWS Artifact) | AÇÃO DO DONO / P0.26 — não comprovado aqui | Arquivo do relatório com escopo e data, na pasta do kit (fora do repositório se contiver dado restrito) |
| 7 | Cláusula-padrão de licença das customizações, sem cessão de direitos, redigida | MINUTA REDIGIDA NESTA ONDA (seção 3) — revisar com advogado antes de usar | Minuta da seção 3 |
| 8 | Divulgação responsável de vulnerabilidade e Trust Center público | NÃO É DESTA TAREFA — ver P3.38 (só anotado aqui) | Política pública, `/.well-known/security.txt` (RFC 9116), página de confiança (P3.38) |

## 2. Lista de certidões (levantamento 18h §2.2 → 18c §2.2, alíneas a–s)

Validade: documentos sem validade expressa valem 120 dias (Edital 8.2, item 8.5.1);
não se aceita protocolo. **Nenhuma data abaixo está preenchida: preencher com a data
real da certidão quando emitida — nunca estimar.**

| Alínea | Documento | Validade (preencher) | Emitido em (preencher) |
|---|---|---|---|
| a | Ato constitutivo/contrato social em vigor + última alteração; eleição de administradores (S.A.) | — (documento, não certidão) | |
| b | Procuração com firma reconhecida (ou equivalente) + RG/CPF de outorgante e outorgado, se houver procurador | — | |
| c | Declaração de cumprimento pleno dos requisitos de habilitação (Anexo IV) | Por licitação | |
| d | Comprovação de ME/EPP (Simples + declaração ou certidão da Junta; declaração de receita bruta — Anexo VIII) | Por licitação | |
| e | CNPJ | — | |
| f | Regularidade Fazenda Federal e Seguridade Social | | |
| g | Regularidade Fazenda Estadual (ambas as certidões, inscritos e não inscritos em dívida ativa, quando o Estado separar) | | |
| h | Regularidade Fazenda Municipal da sede | | |
| i | Inscrição estadual e/ou municipal, se houver | — | |
| j | CRF do FGTS | | |
| k | CNDT (Lei 12.440/2011) | | |
| l | Certidão negativa de falência do distribuidor da sede | | |
| m | Balanço, DRE e demonstrações dos 2 últimos exercícios (ver item 3 da seção 1) | Por exercício | |
| n | Declaração art. 7º, XXXIII, CF (Anexo IX) | Por licitação | |
| o | Declaração de reserva de cargos PcD/reabilitados (Anexo X) — sob pena de inabilitação | Por licitação | |
| p | Declaração de integralidade de custos trabalhistas (Anexo XI) — sob pena de desclassificação | Por licitação | |
| q | Declaração de ausência de condenação por trabalho infantil/escravo (Anexo V) | Por licitação | |
| r | Declaração de ausência de vínculo (Anexo VI) | Por licitação | |
| s | Dados bancários, representante legal, domicílio eletrônico e assinatura por certificação digital (Anexo III) | Por licitação | |

Recuperação judicial: dispensa das certidões f–k mediante decisão judicial, exceto a
regularidade com a Seguridade Social (Edital 8.2.7–8.2.8).

### Rotina de renovação (proposta do kit, a confirmar pelo dono/contador)

1. Revisão mensal do kit (primeira semana do mês): conferir cada validade da tabela.
2. Alerta a 30 dias do vencimento: emitir a renovação; a 15 dias, escalar ao
   responsável; a 5 dias, travar qualquer protocolo que dependa do documento.
3. Ao renovar, substituir o arquivo e atualizar a tabela com a nova validade e a data
   de emissão; manter a versão anterior arquivada (não apagar o histórico).
4. Antes de cada proposta: conferir o kit completo contra o edital específico (cada
   edital pode pedir documento adicional ou prazo distinto do de 120 dias).

## 3. Minuta — cláusula-padrão de licença das customizações (sem cessão de direitos)

> **MINUTA — revisar com advogado antes de usar em qualquer contrato. Não é
> assessoria jurídica.**
>
> **Cláusula — Licença de uso das customizações.** As customizações desenvolvidas
> para a CONTRATANTE no âmbito deste contrato, bem como o programa de computador
> Ziip SAN e suas atualizações, permanecem de propriedade exclusiva da CONTRATADA,
> não havendo cessão de direitos autorais ou de propriedade intelectual, total ou
> parcial, em favor da CONTRATANTE. A CONTRATADA concede à CONTRATANTE licença de
> uso não exclusiva, intransferível e restrita ao objeto e à vigência deste contrato,
> para utilização das customizações em sua operação, sem direito de sublicenciar,
> ceder, distribuir, descompilar ou criar obras derivadas, salvo autorização prévia
> e por escrito da CONTRATADA. Findo o contrato, cessa a licença de uso, ressalvado
> o direito da CONTRATANTE de extrair seus dados, nos termos da cláusula de reversão
> (ADR-0024), sem retenção de código-fonte, de customização ou de credencial da
> CONTRATADA.

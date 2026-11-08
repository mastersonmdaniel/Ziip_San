package br.com.ziipsan.commercial.collection.debit;

import java.util.List;

/**
 * Mapa de cobertura do aceite da ED-27: POC-29 do roteiro VISAN e item 2.14
 * do SAMAE (arrecadacao), exigencia a exigencia, apontando o mecanismo que
 * a atende NESTA entrega ou na dependencia reusada (P4.5/P3.24/P3.2).
 *
 * <p>Honestidade do mapa: o roteiro VISAN e o roteiro SAMAE NAO estao nesta
 * VM nem no repo - nenhum passo POC nem codigo SG foi inventado; o mapa e
 * por exigencia do aceite da ED-27/P3.24. "Sem falha" no ensaio e prova de
 * homologacao da camada (GL.2/LC.13), nao de teste unitario: o que e de
 * ensaio esta marcado {@code ENSAIO}, nao alegado como provado aqui.
 */
public final class DebitAcceptanceCoverage {

    public enum Proof { UNIT_TEST, REUSED_FROM_DEPENDENCY, INTEGRATION_PORT, ENSAIO }

    public record Requirement(String code, String description, String mechanism, Proof proof) {
    }

    private DebitAcceptanceCoverage() {
    }

    public static List<Requirement> poc29() {
        return List.of(
                new Requirement("POC-29/REMESSA",
                        "arquivo FEBRABAN 150 (registros B, C, E, F) gerado para autorizacoes legadas",
                        "LegacyDebitPort.generateRemittance (implementacao e leiaute: P3.24/V6_4); "
                                + "DebitAccountIntegration.generateLegacyRemittance aplica o portao e filtra os ITENS "
                                + "pela autorizacao vinculada (unitId): so itens de autorizacao ativa de mesmo banco "
                                + "chegam a porta; os demais sao excluidos e contados (correcao D1)",
                        Proof.INTEGRATION_PORT),
                new Requirement("POC-29/RETORNO",
                        "retorno do legado processado com baixa",
                        "DebitAccountIntegration.processLegacyReturn com baixa idempotente POR DEBITO "
                                + "(chave DEBIT:<identificador>, alinhada a P3.24; pre-validacao do lote e marcacao "
                                + "imediata por linha - idempotente tambem no caminho de falha, correcoes D2/D3); "
                                + "a chave do arquivo fica como atalho de repeticao completa",
                        Proof.UNIT_TEST),
                new Requirement("POC-29/NOVO-SO-PIX",
                        "autorizacao nova recusada no legado; so Pix Automatico",
                        "AccountDebitAuthorizationGate da P4.5, reusado sem regra paralela",
                        Proof.REUSED_FROM_DEPENDENCY),
                new Requirement("POC-29/DESLIGAMENTO",
                        "data de desligamento do legado parametrizada pela regra vigente do BCB",
                        "BcbDebitRegimeRules da P3.24 (FONTE UNICA): 01/01/2027 convenio de servico publico; "
                                + "01/07/2027 regime geral; DebitRegulatoryCalendar da ED-27 apenas DELEGA a ela "
                                + "como visao/calendario (correcao D3)",
                        Proof.UNIT_TEST),
                new Requirement("POC-29/RELATORIOS",
                        "relatorios de debito em conta dos dois canais",
                        "DebitAccountReport por periodo/canal/desfecho + progresso de conversao",
                        Proof.UNIT_TEST),
                new Requirement("POC-29/ARQUIVOS-REAIS",
                        "arquivos de teste conferidos no paralelo local, sem envio ao banco (GL.2)",
                        "prova com arquivos de teste e da fiacao da P3.24 + GL.2; nenhum envio a banco nesta entrega",
                        Proof.ENSAIO));
    }

    public static List<Requirement> samae214() {
        return List.of(
                new Requirement("SAMAE-2.14/DEBITO-EM-CONTA",
                        "debito em conta operavel nos dois canais enquanto o legado vigorar",
                        "Pix Automatico integrado (P4.5) + legado pela porta da P3.24 sob pay.legacy_debit_enabled",
                        Proof.INTEGRATION_PORT),
                new Requirement("SAMAE-2.14/CALENDARIO",
                        "calendario regulatorio do debito automatico e da cobranca hibrida",
                        "DebitRegulatoryCalendar.events() (visao alimentada pela fonte da P3.24, BcbDebitRegimeRules, e pela P3.2): Res. CMN 5.278/2025, Res. BCB 544/2025, 587/2026 e 565/2026",
                        Proof.UNIT_TEST),
                new Requirement("SAMAE-2.14/ENSAIO",
                        "item 2.14 do SAMAE sem falha no ensaio",
                        "ensaio de homologacao da camada integrada (o codigo cobre as exigencias funcionais do recorte de debito)",
                        Proof.ENSAIO));
    }
}

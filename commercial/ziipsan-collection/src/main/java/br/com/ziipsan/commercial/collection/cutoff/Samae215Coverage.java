package br.com.ziipsan.commercial.collection.cutoff;

import java.util.List;

/**
 * Cobertura do item 2.15 do SAMAE (SG-343 a SG-428, "Cobranca") no
 * recorte da ED-29. O item 2.15 inteiro e critico/eliminatorio na POC
 * do SAMAE e se reparte entre P4.1, ED-29, ED-30 (negativacao/protesto)
 * e ED-31 (divida ativa) — ver o roteiro 20 do SAMAE. Este registro
 * cobre SOMENTE a fatia da ED-29 (18e SS2.13, E9); as demais fatias sao
 * das suas tarefas, e este registro nao as reivindica.
 *
 * <p>Faixas (correcao D3 da revisao cruzada): dos 12 SG deste
 * registro, SOMENTE 6 estao dentro da faixa do item 2.15
 * (SG-343–428): SG-375, SG-386, SG-393, SG-394, SG-398 e SG-400.
 * Os outros 6 — SG-486, SG-501, SG-502, SG-503, SG-504 e SG-558 — sao
 * requisitos IRMAOS de 18e-E9/18f-ENT-04, fora da faixa do 2.15,
 * cobertos aqui porque o acceptance da ED-29 os atribui a esta fatia.
 *
 * <p>Cada linha liga o requisito a funcionalidade DESTE pacote que o
 * demonstra; a prova de comportamento esta nos testes funcionais — este
 * registro e o mapa, nao a prova.
 */
public final class Samae215Coverage {

    private Samae215Coverage() {
    }

    public record Entry(String sgCode, String requirement, String feature) {
    }

    public static final List<Entry> ED29_SLICE = List.of(
            new Entry("SG-375", "Cartas de cobranca com filtros (imovel, rotas, referencia, valor, quantidade de faturas, meses de atraso)",
                    "CollectionLetters (filtros de TR-390)"),
            new Entry("SG-386", "Notificacao para unidades com ligacao ja cortada",
                    "CutoffNoticeDocuments.generateForCutUnits (POC-32)"),
            new Entry("SG-393", "Prioridade das OS de corte (localizacao, vencimento, maior valor, tarifa, categoria, perfil do devedor)",
                    "CutoffDailyPlanner + CutoffExtParameters.PriorityPolicy"),
            new Entry("SG-394", "Quantidade maxima de OS de corte por dia",
                    "CutoffDailyPlanner (cota cut.daily_order_limit)"),
            new Entry("SG-398", "Dias e horarios de geracao das ordens de 1o, 2o e 3o nivel, so dentro das vedacoes da Lei 14.015/2020",
                    "CutoffGenerationGate (calendario do nivel + portao da P3.4)"),
            new Entry("SG-400", "Ao menos dois niveis de corte sucessivos (cavalete, ramal), com calendario por nivel",
                    "CutoffLevel + LevelEscalationRule + cut.level_schedule"),
            new Entry("SG-486", "Ordens de corte de certos clientes so para equipes especiais",
                    "CutoffDailyPlanner.assertCrewAllowed"),
            new Entry("SG-501", "Registrar a execucao por nivel de corte (cavalete, ramal, supressao)",
                    "CutoffLevel + LevelEscalationRule.ExecutedLevel"),
            new Entry("SG-502", "Registrar a execucao da religacao (por nivel)",
                    "SewerCutoff (esgoto) e ReconnectionRule da P3.4 (agua, exercida em WaterReconnectionTest), com nivel em CutoffLevel"),
            new Entry("SG-503", "Registrar corte de ligacao de esgoto",
                    "SewerCutoff.assertSewerCutAllowed (cut.sewer_allowed da P3.4)"),
            new Entry("SG-504", "Registrar religacao de ligacao de esgoto",
                    "SewerCutoff.evaluateSewerReconnection"),
            new Entry("SG-558", "Comunicado de cobranca de esgoto com texto parametrizado",
                    "SewerCutoff.issueReleaseLetter (modelo da P2.30)"));

    /** Codigos SG da fatia da ED-29, sem duplicidade. */
    public static List<String> sgCodes() {
        return ED29_SLICE.stream().map(Entry::sgCode).toList();
    }
}

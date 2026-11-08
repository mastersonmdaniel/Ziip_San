package br.com.ziipsan.commercial.registry.legacy;

import java.util.List;

/**
 * Perfil de importação do legado GSAN (ED-09): o importador do primeiro
 * cliente de edital, construído sobre o framework declarativo da P1.2
 * (PLT-16) — sem importador novo, como o framework manda.
 *
 * <p>Estado honesto da fonte: o aceite diz "GSAN, <b>se confirmado</b>". O
 * legado do primeiro cliente de edital ainda não está confirmado e o leiaute
 * de exportação do GSAN (nomes de coluna da origem) NÃO consta de nenhuma
 * fonte deste projeto — portanto esta classe NÃO embute leiaute nenhum. O
 * que ela faz é a régua que qualquer mapeamento GSAN precisa passar antes de
 * carregar um registro:
 * <ol>
 *   <li>o arquivo precisa ser um {@link LegacyMapping} válido do framework
 *       da P1.2 (parse estrito dele, com o vocabulário dele);</li>
 *   <li>o sistema de origem precisa ser exatamente {@code GSAN} (mapeamento
 *       de outro legado não entra por esta porta);</li>
 *   <li>a conciliação é a da P1.2 ({@link LegacyReconciliation}): a carga só
 *       é aceita para aplicação com {@code allMatch} — divergência é achado
 *       que bloqueia, nunca ajuste automático.</li>
 * </ol>
 * O arquivo de mapeamento concreto nasce com a confirmação do legado do
 * primeiro cliente (pendência nomeada no relatório da ED-09); o fixture de
 * teste desta entrega é massa sintética declarada, não o leiaute do GSAN.
 */
public final class GsanImportProfile {

    public static final String SOURCE_SYSTEM = "GSAN";

    private GsanImportProfile() {
    }

    /** Valida o arquivo de mapeamento GSAN pelo parser real da P1.2. */
    public static LegacyMapping requireGsanMapping(String mappingJson) {
        LegacyMapping mapping = LegacyMapping.parse(mappingJson);
        if (!SOURCE_SYSTEM.equals(mapping.sourceSystem())) {
            throw new IllegalArgumentException(
                    "Este perfil só aceita mapeamento do legado GSAN; o arquivo declara origem "
                            + mapping.sourceSystem() + ".");
        }
        return mapping;
    }

    /**
     * Portão de conciliação da carga GSAN: devolve o relatório da P1.2 e
     * recusa (exceção) quando há qualquer divergência — a aplicação da carga
     * é decisão posterior, sobre relatório limpo.
     */
    public static LegacyReconciliation.Report requireReconciled(
            List<StagedUnit> staged, LegacyReconciliation.LegacyTotals legacyTotals) {
        LegacyReconciliation.Report report = LegacyReconciliation.reconcile(staged, legacyTotals);
        if (!report.allMatch()) {
            long divergent = report.rows().stream().filter(r -> !r.matches()).count();
            throw new IllegalStateException(
                    "Conciliação do legado GSAN divergente em " + divergent
                            + " linha(s): a carga não é aplicável até a divergência ser resolvida "
                            + "na origem ou no mapeamento.");
        }
        return report;
    }
}

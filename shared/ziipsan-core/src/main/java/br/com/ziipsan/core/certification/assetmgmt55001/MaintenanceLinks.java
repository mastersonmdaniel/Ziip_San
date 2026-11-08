package br.com.ziipsan.core.certification.assetmgmt55001;

import java.util.Objects;
import java.util.UUID;

/**
 * Vínculos do pacote ISO 55001 com a manutenção que a ED-56 JÁ opera
 * (ED-72 consome, não reimplementa):
 *
 * <ul>
 *   <li><b>OPE-20</b> — plano preventivo de ativos NÃO LINEARES da ED-56
 *   ({@code PmPlanService.PmPlanLine}: exatamente um gatilho — tempo,
 *   horas de operação ou volume bombeado — com o intervalo do plano do
 *   tenant). O vínculo guarda a REFERÊNCIA da linha e o retrato do
 *   gatilho como lido; a régua de vencimento continua sendo a da ED-56.</li>
 *   <li><b>OPE-38</b> — manutenção por condição com peça sobressalente
 *   crítica da ED-56 ({@code ConditionMaintenance}: gatilho de leitura do
 *   parâmetro {@code maint.condition_triggers} e política de peça do
 *   {@code inv.critical_spares}). O vínculo guarda o gatilho e o estado
 *   da peça COMO LIDOS ({@code SpareStatus} da ED-56: OK,
 *   AT_REORDER_POINT, BELOW_MINIMUM); peça abaixo do mínimo levanta o
 *   sinal {@code requiresRestock} — sem recalcular estoque nem limiar.</li>
 * </ul>
 */
public final class MaintenanceLinks {

    /** Gatilhos da linha preventiva da OPE-20 (nomes da ED-56). */
    public enum PmTriggerKind { TIME, RUNNING_HOURS, PUMPED_VOLUME }

    /** Estado da peça crítica como lido da OPE-38 da ED-56 (nomes dela). */
    public enum SpareStatusRead { OK, AT_REORDER_POINT, BELOW_MINIMUM }

    /**
     * Vínculo com a linha do plano preventivo de não lineares (OPE-20):
     * referência da linha do plano da ED-56 e o gatilho como lido. A
     * amarração gatilho × intervalo é régua da ED-56 e NÃO é repetida aqui.
     */
    public record NonLinearPreventiveLink(Iso55001Activation activation, UUID assetId,
                                          String pmPlanLineRef, PmTriggerKind trigger) {
        public NonLinearPreventiveLink {
            Objects.requireNonNull(activation, "activation é obrigatória");
            activation.requireEnabled();
            Objects.requireNonNull(assetId, "assetId do vínculo OPE-20 é obrigatório");
            if (pmPlanLineRef == null || pmPlanLineRef.isBlank()) {
                throw new IllegalArgumentException("pmPlanLineRef (referência da linha do plano "
                        + "preventivo da ED-56/OPE-20) é obrigatória: o vínculo aponta para o "
                        + "plano que existe, não o recria");
            }
            pmPlanLineRef = pmPlanLineRef.trim();
            Objects.requireNonNull(trigger, "trigger lido da linha OPE-20 é obrigatório");
        }
    }

    /**
     * Vínculo com a manutenção por condição e a peça sobressalente crítica
     * (OPE-38): grandeza do gatilho e estado da peça, ambos COMO LIDOS da
     * ED-56. Peça crítica é opcional no vínculo (condição sem peça
     * associada existe na OPE-38); havendo peça, o estado lido é
     * obrigatório — estado nunca é presumido.
     */
    public record ConditionMaintenanceLink(Iso55001Activation activation, UUID assetId,
                                           String measurement, String criticalPartCode,
                                           SpareStatusRead spareStatus) {
        public ConditionMaintenanceLink {
            Objects.requireNonNull(activation, "activation é obrigatória");
            activation.requireEnabled();
            Objects.requireNonNull(assetId, "assetId do vínculo OPE-38 é obrigatório");
            if (measurement == null || measurement.isBlank()) {
                throw new IllegalArgumentException("measurement do gatilho de condição "
                        + "(OPE-38) é obrigatória");
            }
            measurement = measurement.trim();
            criticalPartCode = criticalPartCode == null || criticalPartCode.isBlank()
                    ? null : criticalPartCode.trim();
            if (criticalPartCode != null && spareStatus == null) {
                throw new IllegalArgumentException("peça crítica associada exige o spareStatus "
                        + "LIDO da ED-56 (OK/AT_REORDER_POINT/BELOW_MINIMUM): estado de peça "
                        + "nunca é presumido");
            }
            if (criticalPartCode == null && spareStatus != null) {
                throw new IllegalArgumentException("spareStatus sem peça crítica associada "
                        + "não faz sentido no vínculo OPE-38");
            }
        }

        /** Peça abaixo do mínimo lido: o plano de gestão de ativos sinaliza reposição. */
        public boolean requiresRestock() {
            return spareStatus == SpareStatusRead.BELOW_MINIMUM;
        }
    }

    private MaintenanceLinks() {
    }
}

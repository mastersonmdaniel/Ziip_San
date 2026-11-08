package br.com.ziipsan.core.certification.assetmgmt55001;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Inventário e ciclo de vida do ativo SOB a ISO 55001 (ED-72): uma VISÃO
 * sobre o ativo da P1.35 (COE-05) e o inventário regulatório do GIS da
 * ED-56 — esta classe NÃO duplica o cadastro do ativo (que é da P1.35/
 * ED-56); ela registra, por campo coincidente, qual definição prevalece.
 *
 * <p><b>Prevalência (aceite da ED-72):</b> nos campos que coincidem entre o
 * ativo da COE-05 e o inventário regulatório da ED-56, prevalece a
 * definição da NR/ANA (a do inventário regulatório). Campo só de um lado
 * usa o valor desse lado. Divergência é registrada (campo, valor COE-05,
 * valor regulatório), nunca resolvida em silêncio.
 *
 * <p><b>Risco LIDO, nunca recalculado:</b> o retrato de risco é o
 * {@code RiskView} da ED-56 com P×C DECOMPOSTO (probabilidade,
 * consequência, score, band, motivo de exclusão), copiado como lido. A
 * única conferência feita aqui é de CONSISTÊNCIA da leitura: score
 * presente com P e C presentes tem de ser P×C — leitura inconsistente é
 * recusada, jamais "corrigida" por régua paralela (proibido pelo aceite).
 * Ativo excluído da régua da ED-56 (motivo de exclusão presente) entra
 * sem score, exatamente como a ED-56 o expõe.
 *
 * <p><b>Frota FORA:</b> ativo do tipo VEÍCULO é recusado — a frota fica na
 * ED-59, que estende a manutenção da ED-56 para veículos (aceite da ED-72
 * e {@code consumed} da fonte: "ED-59 retirada das dependências").
 *
 * <p>Ciclo de vida usa os MESMOS nomes da ED-56
 * ({@code AssetInventoryModel.LifecycleStatus}): PLANNED, IN_OPERATION,
 * UNDER_MAINTENANCE, DECOMMISSIONED, DISPOSED — vocabulário lido, não
 * reinventado.
 */
public record AssetLifecycleRecord(Iso55001Activation activation, UUID assetId,
                                   String patrimonialCode, AssetKind kind,
                                   String lifecycle, List<CoincidentField> coincidentFields,
                                   RiskSnapshot risk, LocalDate registeredOn) {

    /** Natureza do ativo para a fronteira de escopo da ED-72. */
    public enum AssetKind {
        /** Ativo linear (rede) — inventário da ED-56. */
        LINEAR,
        /** Ativo não linear (equipamento de ETA/ETE/elevatória/reservatório) — OPE-20 da ED-56. */
        NON_LINEAR_EQUIPMENT,
        /** Veículo: FORA desta tarefa (frota é da ED-59). */
        VEHICLE,
        /** Outro ativo patrimonial da COE-05. */
        OTHER
    }

    /** Origem da definição que prevaleceu num campo coincidente. */
    public enum FieldSource {
        /** Definição regulatória NR/ANA do inventário da ED-56: prevalece no coincidente. */
        REGULATORY_NR_ANA,
        /** Só o ativo da COE-05 (P1.35) define o campo. */
        COE05_ONLY,
        /** Só o inventário regulatório define o campo. */
        REGULATORY_ONLY
    }

    /**
     * Um campo coincidente (ou não) entre COE-05 e o inventário
     * regulatório, com os dois valores declarados e o vigente resolvido
     * pela prevalência NR/ANA.
     */
    public record CoincidentField(String fieldName, String coe05Value, String regulatoryValue,
                                  String effectiveValue, FieldSource source, boolean divergent) {
        public CoincidentField {
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName do campo coincidente é obrigatório");
            }
            fieldName = fieldName.trim();
            coe05Value = blankToNull(coe05Value);
            regulatoryValue = blankToNull(regulatoryValue);
            if (coe05Value == null && regulatoryValue == null) {
                throw new IllegalArgumentException("campo " + fieldName
                        + " sem valor em nenhum dos lados não é campo do inventário");
            }
            boolean div = coe05Value != null && regulatoryValue != null
                    && !coe05Value.equals(regulatoryValue);
            // Prevalência do aceite: NR/ANA vence no coincidente.
            String eff = regulatoryValue != null ? regulatoryValue : coe05Value;
            FieldSource src = regulatoryValue != null
                    ? (coe05Value != null ? FieldSource.REGULATORY_NR_ANA : FieldSource.REGULATORY_ONLY)
                    : FieldSource.COE05_ONLY;
            if (!eff.equals(effectiveValue) || src != source || div != divergent) {
                throw new IllegalArgumentException("campo " + fieldName + ": a resolução declarada "
                        + "diverge da prevalência NR/ANA do aceite da ED-72 (vigente=" + eff
                        + ", origem=" + src + ", divergente=" + div + ") — use resolve()");
            }
        }

        /** Resolve um campo pela prevalência do aceite: NR/ANA no coincidente. */
        public static CoincidentField resolve(String fieldName, String coe05Value,
                                              String regulatoryValue) {
            String co = blankToNull(coe05Value);
            String rg = blankToNull(regulatoryValue);
            boolean div = co != null && rg != null && !co.equals(rg);
            String eff = rg != null ? rg : co;
            FieldSource src = rg != null
                    ? (co != null ? FieldSource.REGULATORY_NR_ANA : FieldSource.REGULATORY_ONLY)
                    : FieldSource.COE05_ONLY;
            return new CoincidentField(fieldName, co, rg, eff, src, div);
        }

        private static String blankToNull(String v) {
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    /**
     * Retrato de risco LIDO da ED-56 ({@code AssetDtos.RiskView}, P×C
     * decomposto). Nenhum cálculo de risco vive aqui: só a cópia fiel e a
     * conferência de consistência da leitura (score = P×C quando os três
     * estão presentes; ativo excluído da régua não tem score).
     */
    public record RiskSnapshot(Integer probability, Integer consequence, Integer score,
                               String band, String excludedReason) {
        public RiskSnapshot {
            if (excludedReason != null && !excludedReason.isBlank()) {
                excludedReason = excludedReason.trim();
                if (score != null || probability != null) {
                    throw new IllegalArgumentException("risco excluído na ED-56 ("
                            + excludedReason + ") não traz P/score: leitura inconsistente");
                }
            } else {
                excludedReason = null;
                Objects.requireNonNull(probability, "probabilidade lida da ED-56 é obrigatória "
                        + "quando o ativo não está excluído da régua");
                Objects.requireNonNull(consequence, "consequência lida da ED-56 é obrigatória "
                        + "quando o ativo não está excluído da régua");
                Objects.requireNonNull(score, "score lido da ED-56 é obrigatório "
                        + "quando o ativo não está excluído da régua");
                if (score != probability * consequence) {
                    throw new IllegalArgumentException("leitura de risco inconsistente: score "
                            + score + " != P×C (" + probability + "×" + consequence + ") da "
                            + "ED-56 — a ED-72 lê o risco, não o recalcula nem o corrige");
                }
                if (band == null || band.isBlank()) {
                    throw new IllegalArgumentException("band lida da ED-56 é obrigatória "
                            + "quando há score");
                }
                band = band.trim();
            }
        }

        /** Cópia fiel do {@code RiskView} da ED-56 (P×C decomposto). */
        public static RiskSnapshot readFromEd56(Integer probability, Integer consequence,
                                                Integer score, String band,
                                                String excludedReason) {
            return new RiskSnapshot(probability, consequence, score, band, excludedReason);
        }
    }

    /** Ciclos de vida com os nomes da ED-56 (vocabulário lido). */
    public static final List<String> LIFECYCLES = List.of("PLANNED", "IN_OPERATION",
            "UNDER_MAINTENANCE", "DECOMMISSIONED", "DISPOSED");

    public AssetLifecycleRecord {
        Objects.requireNonNull(activation, "activation é obrigatória: o registro só nasce "
                + "no contexto da ativação da norma");
        activation.requireEnabled();
        Objects.requireNonNull(assetId, "assetId (ativo da COE-05/P1.35) é obrigatório");
        if (patrimonialCode == null || patrimonialCode.isBlank()) {
            throw new IllegalArgumentException("patrimonialCode do ativo é obrigatório "
                    + "(identificação do ativo da COE-05, sem inventar código)");
        }
        patrimonialCode = patrimonialCode.trim();
        Objects.requireNonNull(kind, "kind do ativo é obrigatório (fronteira de escopo)");
        if (kind == AssetKind.VEHICLE) {
            throw new IllegalArgumentException("frota fica na ED-59: ativo VEÍCULO não entra "
                    + "no pacote ISO 55001 da ED-72");
        }
        if (lifecycle == null || !LIFECYCLES.contains(lifecycle)) {
            throw new IllegalArgumentException("lifecycle deve ser um dos estados da ED-56 "
                    + LIFECYCLES + ": " + lifecycle);
        }
        coincidentFields = coincidentFields == null ? List.of() : List.copyOf(coincidentFields);
        Objects.requireNonNull(risk, "risk (retrato lido da ED-56) é obrigatório");
        Objects.requireNonNull(registeredOn, "registeredOn é obrigatório");
    }
}

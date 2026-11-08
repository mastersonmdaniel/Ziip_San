package br.com.ziipsan.intelligence.losses.pressure;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parametros da gestao de pressao e do controle ativo de vazamento
 * (ED-86; OPE-04, OPE-17), SEMPRE do tenant — nenhum valor de fabrica
 * alem do que o proprio aceite nomeia:
 * <pre>
 *   pressure.min_mca / pressure.max_mca
 *       limites minimo e maximo de pressao por zona, em metros de
 *       coluna d'agua (mca). FRONTEIRA DURA do aceite: SEM valor de
 *       fabrica enquanto a ABNT NBR 12218:2017 nao for conferida —
 *       ausente = MissingParameterException (parametro faltante
 *       explicito), NUNCA uma constante presumida neste codigo.
 *   leak.repair_phase_targets_hours
 *       metas de duracao, em horas, das tres fases do reparo
 *       (conhecimento, localizacao e reparo). Codificacao declarada
 *       desta entrega (proposta de integracao): texto
 *       "AWARENESS=&lt;h&gt;,LOCATION=&lt;h&gt;,REPAIR=&lt;h&gt;" — as tres
 *       fases sao obrigatorias; fase faltante = parametro faltante.
 *   leak.survey_cycle_days_by_dmc_class
 *       ciclo da campanha de pesquisa, em dias, por CLASSE DE DMC.
 *       A classe de DMC e rotulo do tenant (nenhuma classe e nomeada
 *       pelas fontes): codificacao declarada "CLASSE=&lt;dias&gt;"
 *       separada por ';'. Classe sem ciclo declarado = parametro
 *       faltante para aquela classe, nunca um ciclo presumido.
 * </pre>
 */
public final class PressureParameters {

    public static final String MIN_MCA = "pressure.min_mca";
    public static final String MAX_MCA = "pressure.max_mca";
    public static final String REPAIR_PHASE_TARGETS = "leak.repair_phase_targets_hours";
    public static final String SURVEY_CYCLE = "leak.survey_cycle_days_by_dmc_class";

    private PressureParameters() {}

    /** Fases do tempo de reparo (OPE-17): conhecimento, localizacao e reparo. */
    public enum RepairPhase { AWARENESS, LOCATION, REPAIR }

    /** Limites de pressao de uma zona (mca): minimo &gt;= 0 e maximo &gt; minimo. */
    public record ZoneLimits(double minMca, double maxMca) {
        public ZoneLimits {
            if (Double.isNaN(minMca) || Double.isNaN(maxMca))
                throw new IllegalArgumentException("limites de pressao NaN");
            if (minMca < 0) throw new IllegalArgumentException(
                    MIN_MCA + " nao pode ser negativo: " + minMca);
            if (maxMca <= minMca) throw new IllegalArgumentException(
                    MAX_MCA + " deve ser maior que " + MIN_MCA + ": " + maxMca + " <= " + minMca);
        }
        public boolean isBelow(double pressureMca) { return pressureMca < minMca; }
        public boolean isAbove(double pressureMca) { return pressureMca > maxMca; }
        public boolean isWithin(double pressureMca) { return !isBelow(pressureMca) && !isAbove(pressureMca); }
    }

    /**
     * Limites da zona resolvidos do tenant: QUALQUER um dos dois
     * ausente e parametro faltante explicito (sem valor de fabrica —
     * ABNT NBR 12218:2017 nao conferida; ver relatorio da ED-86).
     */
    public static ZoneLimits zoneLimits(Double minRaw, Double maxRaw) {
        if (minRaw == null) throw new MissingParameterException(MIN_MCA,
                "limite minimo de pressao da zona nao configurado para o tenant; "
                        + "nenhum valor de fabrica existe enquanto a ABNT NBR 12218:2017 nao for conferida");
        if (maxRaw == null) throw new MissingParameterException(MAX_MCA,
                "limite maximo de pressao da zona nao configurado para o tenant; "
                        + "nenhum valor de fabrica existe enquanto a ABNT NBR 12218:2017 nao for conferida");
        return new ZoneLimits(minRaw, maxRaw);
    }

    /** Metas por fase do reparo, em horas (todas &gt; 0 quando declaradas). */
    public record RepairPhaseTargets(double awarenessHours, double locationHours, double repairHours) {
        public RepairPhaseTargets {
            requirePositive(awarenessHours, "AWARENESS");
            requirePositive(locationHours, "LOCATION");
            requirePositive(repairHours, "REPAIR");
        }
        private static void requirePositive(double v, String phase) {
            if (v <= 0 || Double.isNaN(v)) throw new IllegalArgumentException(
                    REPAIR_PHASE_TARGETS + ": meta da fase " + phase + " deve ser positiva: " + v);
        }
        public double targetFor(RepairPhase phase) {
            return switch (phase) {
                case AWARENESS -> awarenessHours;
                case LOCATION -> locationHours;
                case REPAIR -> repairHours;
            };
        }
        public double totalHours() { return awarenessHours + locationHours + repairHours; }
    }

    /**
     * Metas do reparo a partir do texto do parametro do tenant
     * ("AWARENESS=8,LOCATION=24,REPAIR=48"). Ausente/vazio ou com fase
     * faltante = MissingParameterException nomeando a chave.
     */
    public static RepairPhaseTargets repairPhaseTargets(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(REPAIR_PHASE_TARGETS,
                "metas das fases de reparo (conhecimento, localizacao, reparo) nao configuradas para o tenant");
        Map<String, Double> byPhase = new LinkedHashMap<>();
        for (String token : raw.split(",")) {
            String[] kv = token.split("=", 2);
            if (kv.length != 2) throw new IllegalArgumentException(
                    REPAIR_PHASE_TARGETS + " exige FASE=horas separados por virgula: " + raw);
            String phase = kv[0].trim();
            try {
                RepairPhase.valueOf(phase);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(REPAIR_PHASE_TARGETS + ": fase desconhecida: " + phase
                        + " (esperado AWARENESS, LOCATION ou REPAIR)", e);
            }
            try {
                byPhase.put(phase, Double.parseDouble(kv[1].trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(REPAIR_PHASE_TARGETS + ": horas invalidas em: " + token, e);
            }
        }
        for (RepairPhase phase : RepairPhase.values()) {
            if (!byPhase.containsKey(phase.name())) throw new MissingParameterException(REPAIR_PHASE_TARGETS,
                    "meta da fase " + phase.name() + " nao declarada para o tenant");
        }
        return new RepairPhaseTargets(byPhase.get("AWARENESS"), byPhase.get("LOCATION"), byPhase.get("REPAIR"));
    }

    /**
     * Ciclos de pesquisa por classe de DMC a partir do texto do
     * parametro ("CLASSE_A=90;CLASSE_B=180"). Ausente/vazio =
     * MissingParameterException; a classe e rotulo livre do tenant.
     */
    public static Map<String, Integer> surveyCycleDaysByClass(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(SURVEY_CYCLE,
                "ciclo de pesquisa por classe de DMC nao configurado para o tenant");
        Map<String, Integer> byClass = new LinkedHashMap<>();
        for (String token : raw.split(";")) {
            if (token.isBlank()) continue;
            String[] kv = token.split("=", 2);
            if (kv.length != 2 || kv[0].isBlank()) throw new IllegalArgumentException(
                    SURVEY_CYCLE + " exige CLASSE=dias separados por ';': " + raw);
            try {
                int days = Integer.parseInt(kv[1].trim());
                if (days <= 0) throw new IllegalArgumentException(
                        SURVEY_CYCLE + ": ciclo da classe " + kv[0].trim() + " deve ser positivo: " + days);
                byClass.put(kv[0].trim(), days);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(SURVEY_CYCLE + ": dias invalidos em: " + token, e);
            }
        }
        if (byClass.isEmpty()) throw new MissingParameterException(SURVEY_CYCLE,
                "nenhuma classe de DMC com ciclo declarado para o tenant");
        return Map.copyOf(byClass);
    }

    /** Ciclo de UMA classe: classe sem ciclo declarado = parametro faltante (nunca presumido). */
    public static int surveyCycleDaysFor(String raw, String dmcClass) {
        if (dmcClass == null || dmcClass.isBlank())
            throw new IllegalArgumentException("classe de DMC obrigatoria para resolver " + SURVEY_CYCLE);
        Integer days = surveyCycleDaysByClass(raw).get(dmcClass.trim());
        if (days == null) throw new MissingParameterException(SURVEY_CYCLE,
                "ciclo de pesquisa nao declarado para a classe de DMC '" + dmcClass + "' do tenant");
        return days;
    }
}

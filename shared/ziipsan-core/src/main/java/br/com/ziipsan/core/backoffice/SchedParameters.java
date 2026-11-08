package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Parametros da ED-36 (OS completa no backoffice), SEMPRE lidos do tenant —
 * nenhum prazo, janela ou peso e constante de negocio nesta classe:
 * <pre>
 *   sched.customer_windows       janelas de agendamento por turno definidas
 *                                pelo tenant (lista tipada entregue pelo
 *                                chamador, que le o JSON com a classe Json
 *                                do core — esta classe valida, nao parseia)
 *   sched.hard_constraints       restricoes duras da sugestao de programacao
 *   sched.soft_weights           pesos das restricoes brandas
 *   sched.replan_on_urgency      replanejar quando chega OS urgente
 *   sched.priority_weights       pesos por prioridade (catalogo: classe d,
 *                                do produto; urgencia legal sempre no topo)
 *   so.retroactive_close_days    dias maximos da baixa posterior (OS-12;
 *                                padrao de fabrica 3, classe b, registrado na
 *                                definition — aqui ausencia e erro explicito,
 *                                como na ED-35)
 *   so.material_charge_policy    NEVER | PRICE_TABLE_ERI (OS-20; padrao de
 *                                fabrica NEVER, classe b — idem)
 *   so.customer_order_auto_expire NEVER | dias (OS-13 — validado aqui;
 *                                a expiracao em si e do servico agendado,
 *                                fiacao declarada no relatorio)
 * </pre>
 */
public final class SchedParameters {

    public static final String CUSTOMER_WINDOWS = "sched.customer_windows";
    public static final String HARD_CONSTRAINTS = "sched.hard_constraints";
    public static final String SOFT_WEIGHTS = "sched.soft_weights";
    public static final String REPLAN_ON_URGENCY = "sched.replan_on_urgency";
    public static final String PRIORITY_WEIGHTS = "sched.priority_weights";
    public static final String RETROACTIVE_CLOSE_DAYS = "so.retroactive_close_days";
    public static final String MATERIAL_CHARGE_POLICY = "so.material_charge_policy";
    public static final String CUSTOMER_ORDER_AUTO_EXPIRE = "so.customer_order_auto_expire";

    private SchedParameters() {}

    /** Janela de atendimento por turno: dias da semana, faixa horaria e capacidade (OS por janela). */
    public record CustomerWindow(String id, Set<DayOfWeek> days, LocalTime start, LocalTime end, int capacity) {
        public CustomerWindow {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id da janela (" + CUSTOMER_WINDOWS + ") e obrigatorio");
            Objects.requireNonNull(days, "dias da janela sao obrigatorios");
            if (days.isEmpty()) throw new IllegalArgumentException("janela sem dia da semana: " + id);
            days = Set.copyOf(days);
            Objects.requireNonNull(start, "inicio da janela e obrigatorio");
            Objects.requireNonNull(end, "fim da janela e obrigatorio");
            if (!end.isAfter(start)) throw new IllegalArgumentException("fim da janela deve ser depois do inicio: " + id);
            if (capacity <= 0) throw new IllegalArgumentException("capacidade da janela deve ser positiva: " + id);
        }
        public boolean covers(DayOfWeek day, LocalTime time) {
            return days.contains(day) && !time.isBefore(start) && time.isBefore(end);
        }
    }

    public static List<CustomerWindow> customerWindows(List<CustomerWindow> raw) {
        if (raw == null || raw.isEmpty())
            throw new MissingParameterException(CUSTOMER_WINDOWS, "nenhuma janela de agendamento configurada para o tenant");
        Set<String> ids = new java.util.HashSet<>();
        for (CustomerWindow w : raw) {
            Objects.requireNonNull(w, "janela nula em " + CUSTOMER_WINDOWS);
            if (!ids.add(w.id())) throw new IllegalArgumentException("janela duplicada em " + CUSTOMER_WINDOWS + ": " + w.id());
        }
        return List.copyOf(raw);
    }

    /** Restricoes duras da sugestao (acceptance ED-36). */
    public enum HardConstraint {
        /** Habilitacao de seguranca do trabalho (portao de programacao da P1.31). */
        SAFETY_QUALIFICATION,
        /** Prazo da OS (deadline_hours do tipo / so.deadline_hours_by_type da P1.31). */
        ORDER_DEADLINE,
        /** Janela de agendamento do usuario (sched.customer_windows). */
        CUSTOMER_WINDOW,
        /** Equipamento — so se aplica quando a ED-59 existir. */
        EQUIPMENT_WHEN_ED59
    }

    /**
     * Conjunto de restricoes duras do tenant. SAFETY_QUALIFICATION e
     * ORDER_DEADLINE sao normativas (portao da P1.31 e prazo da agencia):
     * configuracao que as remova e RECUSADA, nao afrouxada.
     */
    public static Set<HardConstraint> hardConstraints(Set<HardConstraint> raw) {
        if (raw == null) throw new MissingParameterException(HARD_CONSTRAINTS, "restricoes duras nao configuradas para o tenant");
        Set<HardConstraint> out = raw.isEmpty() ? EnumSet.noneOf(HardConstraint.class) : EnumSet.copyOf(raw);
        if (!out.contains(HardConstraint.SAFETY_QUALIFICATION))
            throw new IllegalArgumentException(HARD_CONSTRAINTS + " nao pode remover SAFETY_QUALIFICATION (portao SST da P1.31)");
        if (!out.contains(HardConstraint.ORDER_DEADLINE))
            throw new IllegalArgumentException(HARD_CONSTRAINTS + " nao pode remover ORDER_DEADLINE (prazo da P1.31/agencia)");
        return Set.copyOf(out);
    }

    /** Pesos das restricoes brandas: agrupamento por regiao e equilibrio de carga. */
    public record SoftWeights(double regionGrouping, double loadBalance) {
        public SoftWeights {
            if (regionGrouping < 0 || loadBalance < 0) throw new IllegalArgumentException(SOFT_WEIGHTS + " nao aceita peso negativo");
            if (regionGrouping == 0 && loadBalance == 0) throw new IllegalArgumentException(SOFT_WEIGHTS + " com os dois pesos zerados nao ordena nada");
        }
    }

    public static SoftWeights softWeights(Double regionGrouping, Double loadBalance) {
        if (regionGrouping == null || loadBalance == null)
            throw new MissingParameterException(SOFT_WEIGHTS, "pesos das restricoes brandas nao configurados para o tenant");
        return new SoftWeights(regionGrouping, loadBalance);
    }

    public static boolean replanOnUrgency(Boolean raw) {
        if (raw == null) throw new MissingParameterException(REPLAN_ON_URGENCY, "replanejamento por urgencia nao configurado para o tenant");
        return raw;
    }

    public static int retroactiveCloseDays(Integer raw) {
        if (raw == null) throw new MissingParameterException(RETROACTIVE_CLOSE_DAYS,
                "janela da baixa posterior nao configurada para o tenant (padrao de fabrica 3 na definition, classe b)");
        if (raw <= 0) throw new IllegalArgumentException(RETROACTIVE_CLOSE_DAYS + " deve ser maior que zero: " + raw);
        return raw;
    }

    /** Politica de cobranca de material da OS (OS-20): nunca, ou pela tabela de precos da ERI via ED-34. */
    public enum MaterialChargePolicy { NEVER, PRICE_TABLE_ERI }

    public static MaterialChargePolicy materialChargePolicy(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(MATERIAL_CHARGE_POLICY,
                "politica de cobranca de material nao configurada para o tenant (padrao de fabrica NEVER na definition, classe b)");
        try {
            return MaterialChargePolicy.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(MATERIAL_CHARGE_POLICY + " desconhecido: " + raw);
        }
    }
}

package br.com.ziipsan.commercial.billing.crisis;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Medida excepcional por area (CAD-74): entidade com ato, vigencia e area,
 * efeitos selecionaveis. Area: localidade, setor, rota ou poligono do GIS.
 * Correcao M1 da revisao: os 4 tipos sao honrados na cobertura. SECTOR casa
 * pelo proprio codigo; LOCALITY, ROUTE e GIS_POLYGON casam pelos setores
 * membros (codigos da ED-54) DECLARADOS na area - o vinculo setor<->
 * localidade/rota/poligono e fornecido por quem registra a medida (fonte:
 * cadastro/GIS da integracao, pendencia P-ED91-02), nunca inventado aqui.
 * Sem esse vinculo a area e RECUSADA no construtor (falha alta), em vez de
 * a cobertura devolver false em silencio e a medida nao bloquear corte
 * algum. A geometria em si e do GIS, nao e duplicada aqui.
 *
 * Efeitos (texto do acceptance): suspender corte, negativacao e encargos;
 * conceder desconto ou isencao; prorrogar vencimento; aplicar bandeira ou
 * tarifa de contingencia.
 *
 * Medidas ATIVAS sao consultadas pelo bloqueio de corte da P3.4 via
 * {@link #cutBlocked(List, String, LocalDate)} (teste dedicado).
 */
public final class ExceptionalMeasure {

    public enum ActKind { EMERGENCY_DECREE, CALAMITY_DECREE, WATER_AUTHORITY_DECLARATION, AGENCY_ACT }
    public enum AreaKind { LOCALITY, SECTOR, ROUTE, GIS_POLYGON }
    public enum Effect { SUSPEND_CUT, SUSPEND_NEGATIVE_LISTING, SUSPEND_CHARGES,
                         GRANT_DISCOUNT_OR_EXEMPTION, EXTEND_DUE_DATE, APPLY_CONTINGENCY_TARIFF }
    public enum Status { DRAFT, ACTIVE, EXPIRED, REVOKED }

    /** Chave event.* (classe b) correspondente a cada efeito. */
    public static String eventKey(Effect e) {
        return switch (e) {
            case SUSPEND_CUT -> "event.suspend_cut";
            case SUSPEND_NEGATIVE_LISTING -> "event.suspend_negative_listing";
            case SUSPEND_CHARGES -> "event.suspend_charges";
            case GRANT_DISCOUNT_OR_EXEMPTION -> "event.grant_discount_or_exemption";
            case EXTEND_DUE_DATE -> "event.extend_due_date";
            case APPLY_CONTINGENCY_TARIFF -> "event.apply_contingency_tariff";
        };
    }

    public record Area(AreaKind kind, String code, Set<String> memberSectorCodes) {
        public Area {
            Objects.requireNonNull(kind, "tipo de area obrigatorio");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo da area obrigatorio");
            memberSectorCodes = memberSectorCodes == null ? Set.of() : Set.copyOf(memberSectorCodes);
            if (kind != AreaKind.SECTOR && memberSectorCodes.isEmpty())
                throw new IllegalArgumentException("area " + kind + " exige os setores membros (ED-54) que ela "
                        + "abrange - sem o vinculo setor<->" + kind + " a cobertura seria silenciosamente "
                        + "vazia (correcao M1); o vinculo vem do cadastro/GIS na integracao (P-ED91-02), "
                        + "nao e inventado aqui");
        }
        /**
         * A medida cobre o setor dado? SECTOR casa pelo codigo proprio;
         * LOCALITY, ROUTE e GIS_POLYGON casam pelos setores membros
         * declarados. Sem membros (inalcancavel pelo construtor, defesa em
         * profundidade) falha alto - nunca false silencioso.
         */
        public boolean coversSector(String sectorCode) {
            if (sectorCode == null) return false;
            if (kind == AreaKind.SECTOR) return code.equals(sectorCode);
            if (memberSectorCodes.isEmpty())
                throw new IllegalStateException("area " + kind + " sem setores membros: cobertura "
                        + "indeterminada, nao presumida vazia (correcao M1)");
            return memberSectorCodes.contains(sectorCode);
        }
        public boolean covers(AreaKind k, String c) {
            return kind == k && code.equals(c);
        }
    }

    private final UUID id;
    private final ActKind actKind;
    private final String actReference;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final Area area;
    private final EnumSet<Effect> effects;
    private Status status;
    private UUID batchOrderId;

    public ExceptionalMeasure(UUID id, ActKind actKind, String actReference,
                              LocalDate validFrom, LocalDate validTo, Area area, Set<Effect> effects) {
        this.id = Objects.requireNonNull(id, "medida obrigatoria");
        this.actKind = Objects.requireNonNull(actKind, "ato obrigatorio");
        if (actReference == null || actReference.isBlank())
            throw new IllegalArgumentException("referencia do ato obrigatoria (decreto/declaracao/ato da agencia)");
        this.actReference = actReference;
        this.validFrom = Objects.requireNonNull(validFrom, "inicio da vigencia obrigatorio");
        this.validTo = Objects.requireNonNull(validTo, "fim da vigencia obrigatorio");
        if (validTo.isBefore(validFrom)) throw new IllegalArgumentException("vigencia invertida");
        this.area = Objects.requireNonNull(area, "area obrigatoria");
        if (effects == null || effects.isEmpty())
            throw new IllegalArgumentException("ao menos um efeito selecionavel e obrigatorio");
        this.effects = EnumSet.copyOf(effects);
        this.status = Status.DRAFT;
    }

    public void activate() {
        if (status != Status.DRAFT) throw new IllegalStateException("so medida em rascunho ativa: " + status);
        status = Status.ACTIVE;
    }
    public void revoke() { status = Status.REVOKED; }

    /**
     * Transicao de expiracao (correcao m4): medida ACTIVE cuja vigencia ja
     * venceu na data dada passa a EXPIRED e devolve true; nos demais casos
     * nada muda e devolve false. Ate esta correcao o status EXPIRED existia
     * sem transicao que o atingisse - a expiracao era so implicita na
     * janela de {@link #activeOn(LocalDate)}.
     */
    public boolean expireIfValidityEnded(LocalDate date) {
        Objects.requireNonNull(date, "data obrigatoria");
        if (status == Status.ACTIVE && date.isAfter(validTo)) {
            status = Status.EXPIRED;
            return true;
        }
        return false;
    }

    public boolean activeOn(LocalDate date) {
        return status == Status.ACTIVE && !date.isBefore(validFrom) && !date.isAfter(validTo);
    }
    public boolean has(Effect e) { return effects.contains(e); }

    /**
     * Aplicacao por OS de lote: registra o lote que materializa a medida.
     * Correcao m4: a exigencia de ACTIVE vive AQUI, no proprio metodo -
     * o uso direto sem passar pelo CrisisApplicationService nao fura a
     * regra (antes so o servico exigia ACTIVE).
     */
    public void applyByBatchOrder(UUID batchOrderId) {
        if (status != Status.ACTIVE)
            throw new IllegalStateException("medida nao ativa nao se aplica por lote: " + status);
        this.batchOrderId = Objects.requireNonNull(batchOrderId, "lote de OS obrigatorio");
    }

    /**
     * Consulta do bloqueio de corte da P3.4: corte bloqueado se alguma medida
     * ATIVA na data, cobrindo o setor da unidade, tem o efeito SUSPEND_CUT.
     */
    public static boolean cutBlocked(List<ExceptionalMeasure> measures, String unitSectorCode, LocalDate date) {
        Objects.requireNonNull(date, "data obrigatoria");
        if (measures == null) return false;
        return measures.stream().anyMatch(m -> m.activeOn(date) && m.has(Effect.SUSPEND_CUT)
                && m.area.coversSector(unitSectorCode));
    }

    public UUID id() { return id; }
    public ActKind actKind() { return actKind; }
    public String actReference() { return actReference; }
    public LocalDate validFrom() { return validFrom; }
    public LocalDate validTo() { return validTo; }
    public Area area() { return area; }
    public Set<Effect> effects() { return Set.copyOf(effects); }
    public Status status() { return status; }
    public UUID batchOrderId() { return batchOrderId; }
}

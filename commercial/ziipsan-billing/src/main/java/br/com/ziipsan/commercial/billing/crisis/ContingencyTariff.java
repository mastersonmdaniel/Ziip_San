package br.com.ziipsan.commercial.billing.crisis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Componente tarifario de contingencia e tarifa sazonal (GES-07), sobre o
 * desenho VIGENTE: componente com vigencia propria aplicado sobre a
 * estrutura tarifaria com vigencia da P2.29 - NAO e uma nova estrutura
 * nem o registro unico da proposta do item 32 das DECISOES-DO-DONO
 * (proposta registrada, nao executada; ver relatorio).
 *
 * Lei 11.445/2007, art. 46; NR 13/ANA, arts. 22 a 24: ato de autorizacao
 * registrado, faixas afetadas explicitas, isencao de unidades essenciais
 * TRAVADA e comunicacao na fatura (texto obrigatorio).
 *
 * Percentual: vem do ato do tenant/agencia registrado no componente;
 * nenhum percentual de fabrica existe (sem fonte = [A DEFINIR] no ato).
 */
public final class ContingencyTariff {

    public enum Kind { CONTINGENCY, SEASONAL }

    private final Kind kind;
    private final BigDecimal pct;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final String authorizationAct;
    private final Set<String> affectedBlocks;
    private final boolean essentialUnitsExempt;
    private final String billNote;

    public ContingencyTariff(Kind kind, BigDecimal pct, LocalDate validFrom, LocalDate validTo,
                             String authorizationAct, Set<String> affectedBlocks,
                             boolean essentialUnitsExempt, String billNote) {
        this.kind = Objects.requireNonNull(kind, "tipo do componente obrigatorio");
        this.pct = Objects.requireNonNull(pct, "percentual do ato obrigatorio (sem fabrica)");
        if (pct.signum() == 0) throw new IllegalArgumentException("percentual zero nao e componente");
        if (pct.compareTo(new BigDecimal("-100")) <= 0)
            throw new IllegalArgumentException("desconto nao pode passar de 100%: " + pct);
        this.validFrom = Objects.requireNonNull(validFrom, "inicio obrigatorio");
        this.validTo = Objects.requireNonNull(validTo, "fim obrigatorio");
        if (validTo.isBefore(validFrom)) throw new IllegalArgumentException("vigencia invertida");
        if (authorizationAct == null || authorizationAct.isBlank())
            throw new IllegalArgumentException("ato de autorizacao obrigatorio (Lei 11.445, art. 46)");
        this.authorizationAct = authorizationAct;
        if (affectedBlocks == null || affectedBlocks.isEmpty())
            throw new IllegalArgumentException("faixas afetadas explicitas obrigatorias [A DEFINIR no ato se nao houver fonte]");
        this.affectedBlocks = Set.copyOf(affectedBlocks);
        if (!essentialUnitsExempt)
            throw new IllegalArgumentException("isencao de unidades essenciais e TRAVADA (classe a): nao pode ser desligada");
        this.essentialUnitsExempt = true;
        if (billNote == null || billNote.isBlank())
            throw new IllegalArgumentException("comunicacao na fatura obrigatoria (NR 13/ANA, arts. 22 a 24)");
        this.billNote = billNote;
    }

    public boolean activeOn(LocalDate date) { return !date.isBefore(validFrom) && !date.isAfter(validTo); }
    public boolean affectsBlock(String blockLabel) { return affectedBlocks.contains(blockLabel); }

    /**
     * Aplica o componente a um valor de faixa. Unidade essencial: isenta
     * (valor intacto). Faixa fora das afetadas ou fora da vigencia: intacto.
     * Contingencia exige a bandeira ligada (crisis.tariff_flag_enabled /
     * tariff.contingency_* com aceite, lidos por CrisisParameters e
     * passados aqui como fato - o componente nao presume parametro).
     */
    public BigDecimal apply(BigDecimal blockAmount, String blockLabel, boolean essentialUnit,
                            LocalDate date, boolean contingencyFlagEnabled) {
        Objects.requireNonNull(blockAmount, "valor obrigatorio");
        Objects.requireNonNull(date, "data obrigatoria");
        if (essentialUnit) return blockAmount;
        if (!activeOn(date) || !affectsBlock(blockLabel)) return blockAmount;
        if (kind == Kind.CONTINGENCY && !contingencyFlagEnabled)
            throw new IllegalStateException("bandeira de contingencia desligada para o tenant (crisis.tariff_flag_enabled)");
        BigDecimal factor = BigDecimal.ONE.add(pct.movePointLeft(2));
        return blockAmount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public Kind kind() { return kind; }
    public BigDecimal pct() { return pct; }
    public String authorizationAct() { return authorizationAct; }
    public String billNote() { return billNote; }
    public boolean essentialUnitsExempt() { return essentialUnitsExempt; }
}

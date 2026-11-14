package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.servicecatalog.ServicePriceItem;
import br.com.ziipsan.commercial.billing.tariff.CategoryRule;
import br.com.ziipsan.commercial.billing.tariff.TariffBlock;
import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerador da versao desonerada (DESENHO-PA02 secao 4.1): transformacao
 * auditavel FORA do motor — {@code TariffEngine}, {@code BillCalculator} e a
 * ordem do {@code BillComposer} nao se tocam.
 *
 * <p>Formula (F-03 confirmado pela NT 6.2.3.3): {@code preco' = preco x
 * (1 - theta)}, com theta a aliquota efectiva regulatoria resolvida por
 * precedencia ({@link ReliefRateResolver}). Copiam-se sem alteracao:
 * franquia em m3, {@code toM3} das faixas e a regra social inteira
 * (percentuais sao da estrutura — NT 6.3.1: preserva-se integralmente a
 * estrutura tarifaria vigente).
 *
 * <p>Idempotencia (secao 3.4): repetir a geracao do mesmo programa+origem
 * devolve a ligacao existente; nao duplica versoes.
 *
 * <p>Multas (NT 6.6): valor fixo so entra com {@link FineClassification};
 * sem classificacao ou indenizatoria, a geracao recusa com erro fechado.
 */
public final class RelievedVersionGenerator {

    /** Registo de ligacoes geradas (a persistencia real e a tabela da M1). */
    public static final class GenerationLedger {
        private final Map<String, ReliefLink> byKey = new ConcurrentHashMap<>();

        private static String key(UUID programId, ReliefLink.TargetKind kind, UUID sourceId) {
            return programId + "|" + kind + "|" + sourceId;
        }

        public ReliefLink find(UUID programId, ReliefLink.TargetKind kind, UUID sourceId) {
            return byKey.get(key(programId, kind, sourceId));
        }

        public ReliefLink record(ReliefLink link) {
            return byKey.putIfAbsent(
                    key(link.programId(), link.targetKind(), link.sourceVersionId()), link) == null
                    ? link : byKey.get(key(link.programId(), link.targetKind(), link.sourceVersionId()));
        }

        public List<ReliefLink> all() {
            return List.copyOf(byKey.values());
        }
    }

    /** Resultado da geracao de uma versao tarifaria desonerada. */
    public record GenerationResult(TariffVersion relievedVersion, ReliefLink link, boolean reused) {
    }

    /** Valor fixo elegivel (multa fixa/taxa fixa) com a sua classificacao previa. */
    public record ClassifiedFixedFee(String ref, BigDecimal nominalAmount,
                                     FineClassification classification) {
        public ClassifiedFixedFee {
            if (ref == null || ref.isBlank()) {
                throw new IllegalArgumentException("referencia do valor fixo e obrigatoria");
            }
            Objects.requireNonNull(nominalAmount, "valor nominal e obrigatorio");
            Objects.requireNonNull(classification, "classificacao previa e obrigatoria (NT 6.6.2)");
        }
    }

    private final GenerationLedger ledger;

    public RelievedVersionGenerator(GenerationLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger de geracao e obrigatorio");
    }

    /** Factor da formula F-03: {@code 1 - theta/100}, gravado com 6 casas para auditoria. */
    public static BigDecimal factorOf(BigDecimal ratePct) {
        Objects.requireNonNull(ratePct, "aliquota e obrigatoria");
        return BigDecimal.ONE.subtract(
                ratePct.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
    }

    /** Transforma um valor monetario nominal pelo factor, na politica dada. */
    public static BigDecimal relieveAmount(BigDecimal amount, BigDecimal factor,
                                           ReliefRoundingPolicy policy) {
        Objects.requireNonNull(amount, "valor e obrigatorio");
        BigDecimal relieved = policy.apply(amount.multiply(factor));
        if (amount.signum() > 0 && relieved.signum() == 0) {
            throw new TariffConfigurationException(
                    "Transformacao anularia o preco " + amount + " (factor " + factor
                            + "): geracao recusada — preco anulado em silencio e o pior desfecho");
        }
        return relieved;
    }

    /**
     * Gera (ou devolve, se ja gerada) a versao desonerada da versao de origem.
     *
     * @param sourceTableScale casas decimais da tabela vigente (NT 6.3.2)
     */
    public GenerationResult generateTariffVersion(ReliefProgram program, TariffVersion source,
                                                  ReliefRate rate, UUID newVersionId,
                                                  UUID runId, int sourceTableScale) {
        Objects.requireNonNull(program, "programa e obrigatorio");
        Objects.requireNonNull(source, "versao de origem e obrigatoria");
        Objects.requireNonNull(rate, "aliquota resolvida e obrigatoria");
        ReliefLink existing = ledger.find(program.id(), ReliefLink.TargetKind.TARIFF_VERSION, source.id());
        if (existing != null) {
            return new GenerationResult(null, existing, true);
        }
        // Politica efectiva (R-06): a declarada pelo tenant, ou — sem
        // declaracao — a DERIVADA da precisao da tabela de origem.
        ReliefRoundingPolicy policy = (program.roundingPolicy() != null
                ? program.roundingPolicy()
                : ReliefRoundingPolicy.tenantDefaultFor(source))
                .requireCompatibleWithSource(sourceTableScale);
        BigDecimal factor = factorOf(rate.effectiveRatePct());

        List<CategoryRule> relievedRules = new ArrayList<>();
        for (CategoryRule rule : source.rules()) {
            List<TariffBlock> blocks = new ArrayList<>();
            for (TariffBlock block : rule.blocks()) {
                blocks.add(new TariffBlock(block.toM3(),
                        relieveAmount(block.pricePerM3(), factor, policy)));
            }
            relievedRules.add(new CategoryRule(rule.categoryId(), rule.subcategoryId(),
                    rule.minConsumption(), relieveAmount(rule.minCharge(), factor, policy),
                    rule.economyFactor(), blocks));
        }
        // A vigencia cadastral da desonerada abre no corte do programa (secao 5.2);
        // a resolucao para faturacao e por data de emissao, nao por esta vigencia.
        TariffVersion relieved = new TariffVersion(newVersionId, program.effectiveFrom(), null,
                relievedRules, source.socialRule());
        ReliefLink link = new ReliefLink(UUID.randomUUID(), program.id(),
                ReliefLink.TargetKind.TARIFF_VERSION, source.id(), newVersionId,
                rate.effectiveRatePct(), factor, policy.label(), null, runId);
        return new GenerationResult(relieved, ledger.record(link), false);
    }

    /**
     * Geracao em lote (correccao R-01 da REVISAO-PA-02, parte (a)): produz a
     * versao desonerada e a ligacao para <b>cada</b> versao de origem dada —
     * todas as versoes vigentes/futuras do cadastro que cubram qualquer
     * periodo faturavel pos-corte, nao so a versao em vigor no momento da
     * publicacao. Sem isto, um periodo que atravesse uma fronteira de versao
     * pre-corte (ex.: reajuste anual com {@code valid_from} dentro do
     * periodo) fica com uma fatia sem ligacao e a resolucao por emissao ou
     * bloqueia indevidamente ou — pior — aplicaria precos de outra versao.
     *
     * @param rateFor resolve a aliquota aplicavel a cada versao de origem
     *                (precedencia da secao 3.3 fica do lado do chamador)
     */
    public List<GenerationResult> generateForAllSources(ReliefProgram program,
                                                        List<TariffVersion> sources,
                                                        java.util.function.Function<TariffVersion, ReliefRate> rateFor,
                                                        UUID runId, int sourceTableScale) {
        Objects.requireNonNull(program, "programa e obrigatorio");
        Objects.requireNonNull(sources, "versoes de origem sao obrigatorias");
        Objects.requireNonNull(rateFor, "resolvedor de aliquota por origem e obrigatorio");
        List<GenerationResult> results = new ArrayList<>();
        for (TariffVersion source : sources) {
            results.add(generateTariffVersion(program, source,
                    Objects.requireNonNull(rateFor.apply(source),
                            "aliquota resolvida e obrigatoria para a origem " + source.id()),
                    UUID.randomUUID(), runId, sourceTableScale));
        }
        return List.copyOf(results);
    }

    /**
     * Servicos de valor nominal (ED-34): preco x factor (NT 6.5.1). Servicos
     * percentuais nao passam por aqui: desoneram automaticamente pela base.
     */
    public ServicePriceItem relieveServicePrice(ServicePriceItem item, BigDecimal factor,
                                                ReliefRoundingPolicy policy) {
        Objects.requireNonNull(item, "item de servico e obrigatorio");
        BigDecimal relieved = relieveAmount(item.unitPrice(), factor, policy);
        return new ServicePriceItem(item.code(), item.description(), item.revenueNature(),
                relieved, item.validFrom(), item.validTo(), item.homologationRequired(),
                item.homologationRef(), item.sharingPct());
    }

    /**
     * Valor fixo (multa/taxa) com classificacao previa obrigatoria (NT 6.6).
     *
     * @throws TariffConfigurationException se percentual (nao se transforma),
     *         indenizatoria (exame proprio) ou nao classificada
     */
    public BigDecimal relieveFixedFee(ClassifiedFixedFee fee, BigDecimal factor,
                                      ReliefRoundingPolicy policy) {
        Objects.requireNonNull(fee, "valor fixo e obrigatorio");
        return switch (fee.classification()) {
            case NOMINAL_RECOVERY_REVENUE -> relieveAmount(fee.nominalAmount(), factor, policy);
            case PERCENTUAL -> throw new TariffConfigurationException(
                    "Valor fixo " + fee.ref() + " e percentual: nao se transforma o percentual; "
                            + "a desoneracao vem da base (NT 6.6.1)");
            case NOMINAL_INDEMNITY -> throw new TariffConfigurationException(
                    "Multa " + fee.ref() + " e indenizatoria: exame proprio (NT 6.6.2) — "
                            + "fora da geracao automatica, nunca por presuncao");
            case NOMINAL_UNCLASSIFIED -> throw new TariffConfigurationException(
                    "Multa " + fee.ref() + " sem classificacao previa (NT 6.6.2): geracao recusada");
        };
    }

    /** Data de referencia da resolucao de aliquota na geracao: o corte do programa. */
    public static LocalDate generationReferenceDate(ReliefProgram program) {
        return program.effectiveFrom();
    }
}

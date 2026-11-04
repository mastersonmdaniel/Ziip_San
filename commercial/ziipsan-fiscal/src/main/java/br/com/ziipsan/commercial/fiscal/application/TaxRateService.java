package br.com.ziipsan.commercial.fiscal.application;

import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity.TaxRateRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro e resolução de alíquotas com vigência (P2.4) sobre {@code tax_rates} (V4_5).
 *
 * <p>O único caminho de escrita de alíquota: não há constante de alíquota em código de
 * produção — a resolução (emissão fiscal e, na P2.5, a conta) lê só as linhas do tenant.
 * O cadastro valida forma (código, escopo, intervalo, faixa) e pré-checa sobreposição
 * dentro de (tributo, escopo); a restrição de exclusão da V4_5 continua sendo a barreira
 * definitiva no banco, e o IT prova as duas.
 *
 * <p>Valores normativos carregados aqui: nenhum. As linhas de 2026 (ano de teste: CBS
 * 0,9% e IBS 0,1%), a CBS plena a partir de 01/01/2027 e a transição do IBS de 2029 a
 * 2032 (LC 214/2025, art. 544) são CADASTRÁVEIS por esta classe, com os valores informados
 * pelo tenant — os testes usam valores de teste explicitamente marcados como tais, porque
 * o plano não publica o valor da CBS plena nem os percentuais da transição do IBS.
 */
public final class TaxRateService {

    /** Códigos aceitos por {@code tax_rates} (o mesmo CHECK da V4_5). */
    public static final Set<String> TAX_CODES =
            Set.of("CBS", "IBS", "ISS", "IRRF", "CSRF", "PIS", "COFINS", "INSS");

    /** Escopo padrão: alíquota nacional, sem desdobramento por UF/município. */
    public static final String NATIONAL = "NATIONAL";

    private final FiscalDocumentRepository repository;

    public TaxRateService(FiscalDocumentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "O repositorio fiscal e obrigatorio");
    }

    /**
     * Cadastra uma linha de alíquota com vigência.
     *
     * @throws IllegalArgumentException se o código, o escopo, o intervalo ou a faixa forem inválidos
     * @throws IllegalStateException se a vigência sobrepõe outra linha do mesmo tributo e escopo
     */
    public UUID register(UUID tenantId, String taxCode, String scope, LocalDate validFrom,
            LocalDate validTo, BigDecimal rate, String legalBasis) {
        Objects.requireNonNull(tenantId, "O tenant e obrigatorio");
        if (taxCode == null || !TAX_CODES.contains(taxCode)) {
            throw new IllegalArgumentException("Codigo de tributo desconhecido: " + taxCode);
        }
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("O escopo da aliquota e obrigatorio");
        }
        Objects.requireNonNull(validFrom, "O inicio da vigencia e obrigatorio");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException(
                    "O fim da vigencia (" + validTo + ") e anterior ao inicio (" + validFrom + ")");
        }
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A aliquota deve estar em [0, 1]: " + rate);
        }
        boolean overlap = repository.taxRates(tenantId).stream()
                .filter(r -> r.taxCode().equals(taxCode) && r.scope().equals(scope))
                .anyMatch(r -> TaxRateValidity.overlaps(
                        r.validFrom(), r.validTo(), validFrom, validTo));
        if (overlap) {
            throw new IllegalStateException(
                    "Vigencia sobreposta para " + taxCode + " (" + scope + ") em ["
                            + validFrom + ", " + validTo + "]: corrija tax_rates");
        }
        return repository.insertTaxRate(tenantId,
                new TaxRateRow(taxCode, scope, validFrom, validTo, rate), legalBasis);
    }

    /** Alíquota vigente de {@code taxCode} no {@code scope} para a competência (erro se ausente). */
    public BigDecimal resolve(UUID tenantId, String taxCode, String scope, LocalDate competence) {
        Objects.requireNonNull(tenantId, "O tenant e obrigatorio");
        Objects.requireNonNull(scope, "O escopo e obrigatorio");
        List<TaxRateRow> rows = repository.taxRates(tenantId).stream()
                .filter(r -> r.scope().equals(scope))
                .toList();
        return TaxRateValidity.rateFor(rows, taxCode, competence);
    }

    /** Alíquota vigente no escopo nacional (o caminho usado pela emissão fiscal). */
    public BigDecimal resolveNational(UUID tenantId, String taxCode, LocalDate competence) {
        return resolve(tenantId, taxCode, NATIONAL, competence);
    }
}

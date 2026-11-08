package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.effluent.EffluentCatalog;
import br.com.ziipsan.core.effluent.EffluentStandard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Catalogo de padrao de lancamento DO PRESTADOR para o efluente do
 * usuario nao domestico, construido SOBRE o catalogo de padrao de
 * efluente da ED-105 (aceite da ED-83:
 * {@code effluent.user_standard_catalog} sobre
 * {@code EffluentCatalog}).
 *
 * <p>Desenho (sem duplicar a ED-105): os dois niveis sao
 * {@link EffluentCatalog} REAIS da ED-105 — o catalogo-base (padroes
 * de efluente da estacao/norma) e o catalogo do prestador para o
 * usuario. A resolucao e declarada e deterministica:
 * <ol>
 *   <li>vale a linha do PRESTADOR para o parametro/esfera/data, se
 *       existir e estiver revisada pelo responsavel tecnico;</li>
 *   <li>na falta dela, vale a linha revisada da BASE (ED-105);</li>
 *   <li>sem linha revisada em nenhum nivel, o veredito e
 *       {@code INDETERMINATE} — nunca conformidade presumida, como na
 *       ED-50/ED-105. Linha existente mas nao revisada NAO fundamenta
 *       veredito, nem no nivel do prestador nem na base.</li>
 * </ol>
 * O veredito nomeia a origem da referencia usada
 * ({@link Source#PROVIDER} x {@link Source#BASE_ED105}) e a
 * referencia normativa da linha — a esfera (federal/estadual) segue
 * declarada pelo chamador, como na ED-105 (regra de prevalencia sem
 * fonte, P-ED105-06, herdada e declarada).
 *
 * <p>NENHUM limite nasce aqui: as linhas dos dois catalogos entram
 * pela carga revisada das respectivas tarefas.
 */
public record UserStandardCatalog(String version, EffluentCatalog providerCatalog,
                                  EffluentCatalog baseCatalog) {

    /** Origem da linha que fundamentou o veredito. */
    public enum Source { PROVIDER, BASE_ED105, NONE }

    public record Assessment(String parameterKey, Verdict verdict, String reason,
                             String normReference, Source source, String catalogVersionUsed) {
        public enum Verdict { CONFORMING, NON_CONFORMING, INDETERMINATE }
    }

    public UserStandardCatalog {
        if (version == null || version.isBlank())
            throw new IllegalArgumentException("versao do catalogo do prestador"
                    + " (effluent.user_standard_catalog) e obrigatoria");
        if (providerCatalog == null)
            throw new IllegalArgumentException("catalogo do prestador e obrigatorio (pode ser vazio)");
        if (baseCatalog == null)
            throw new IllegalArgumentException("catalogo-base da ED-105 e obrigatorio:"
                    + " o catalogo do usuario se constroi SOBRE ele, nao o substitui");
        if (!version.equals(providerCatalog.version()))
            throw new IllegalArgumentException("versao declarada (" + version + ") diverge da versao"
                    + " do catalogo do prestador (" + providerCatalog.version() + "):"
                    + " effluent.user_standard_catalog nomeia a versao vigente do catalogo carregado");
    }

    public Assessment evaluate(String parameterKey, BigDecimal numericValue, String qualitativeValue,
                               EffluentStandard.Jurisdiction jurisdiction, String stateCode,
                               LocalDate referenceDate) {
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        boolean numeric = numericValue != null;
        boolean qualitative = qualitativeValue != null && !qualitativeValue.isBlank();
        if (numeric == qualitative)
            throw new IllegalArgumentException("resultado de " + parameterKey
                    + " exige exatamente uma forma: numerica OU qualitativa, como reportada");
        if (jurisdiction == null) throw new IllegalArgumentException("esfera (federal/estadual) e obrigatoria");
        if (referenceDate == null) throw new IllegalArgumentException("data de referencia e obrigatoria");

        var providerReviewed = providerCatalog.reviewedStandardOn(
                parameterKey, jurisdiction, stateCode, referenceDate);
        if (providerReviewed.isPresent())
            return judge(parameterKey, providerReviewed.get(), numericValue, qualitativeValue,
                    Source.PROVIDER, providerCatalog.version());
        var baseReviewed = baseCatalog.reviewedStandardOn(
                parameterKey, jurisdiction, stateCode, referenceDate);
        if (baseReviewed.isPresent())
            return judge(parameterKey, baseReviewed.get(), numericValue, qualitativeValue,
                    Source.BASE_ED105, baseCatalog.version());

        boolean anyLine = providerCatalog.standardOn(parameterKey, jurisdiction, stateCode, referenceDate).isPresent()
                || baseCatalog.standardOn(parameterKey, jurisdiction, stateCode, referenceDate).isPresent();
        return new Assessment(parameterKey, Assessment.Verdict.INDETERMINATE,
                anyLine ? "padrao existe mas ainda nao foi revisado pelo responsavel tecnico"
                        + " em nenhum nivel (prestador/ED-105): veredito nao se presume"
                        : "sem padrao para o parametro na esfera e na data, nem no catalogo"
                        + " do prestador nem no catalogo da ED-105",
                null, Source.NONE, null);
    }

    private static Assessment judge(String parameterKey, EffluentStandard standard,
                                    BigDecimal numericValue, String qualitativeValue,
                                    Source source, String catalogVersion) {
        boolean numeric = numericValue != null;
        boolean conforming = switch (standard.comparator()) {
            case MAX -> {
                if (!numeric) yield mismatch(parameterKey, standard);
                yield numericValue.compareTo(standard.limitMax()) <= 0;
            }
            case MIN -> {
                if (!numeric) yield mismatch(parameterKey, standard);
                yield numericValue.compareTo(standard.limitMin()) >= 0;
            }
            case RANGE -> {
                if (!numeric) yield mismatch(parameterKey, standard);
                yield numericValue.compareTo(standard.limitMin()) >= 0
                        && numericValue.compareTo(standard.limitMax()) <= 0;
            }
            case QUALITATIVE_EQUALS -> {
                if (numeric) yield mismatch(parameterKey, standard);
                yield standard.qualitativeExpected().equals(qualitativeValue);
            }
        };
        return new Assessment(parameterKey,
                conforming ? Assessment.Verdict.CONFORMING : Assessment.Verdict.NON_CONFORMING,
                conforming ? "dentro do padrao revisado" : "fora do padrao revisado",
                standard.normReference(), source, catalogVersion);
    }

    private static boolean mismatch(String parameterKey, EffluentStandard standard) {
        throw new IllegalArgumentException("forma do resultado de " + parameterKey
                + " (numerica/qualitativa) nao corresponde ao comparador do padrao revisado "
                + standard.comparator() + ": resultado nao avaliavel, nao presumido");
    }
}

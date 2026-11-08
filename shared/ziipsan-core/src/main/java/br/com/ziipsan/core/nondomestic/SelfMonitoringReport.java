package br.com.ziipsan.core.nondomestic;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Automonitoramento INFORMADO PELO USUARIO (aceite da ED-83): o
 * gerador reporta os resultados do seu proprio monitoramento no
 * periodo. O relatorio e DECLARACAO do usuario — ele nao substitui a
 * amostragem do prestador nem a vistoria por OS: os resultados sao
 * avaliados contra o mesmo catalogo de padrao de lancamento
 * ({@link UserStandardCatalog}) pelo servico, e divergencia entre o
 * automonitoramento e a amostragem do prestador e fato que a
 * vistoria apura (ver {@link NonDomesticService}).
 *
 * <p>Nenhum resultado nasce aqui: cada linha e numerica OU
 * qualitativa, como o laboratorio do usuario a reportou.
 */
public record SelfMonitoringReport(
        UUID id,
        UUID dischargerId,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<ReportedResult> results,
        String reportedByRef,
        Instant reportedAt) {

    /** Um resultado reportado pelo usuario: numerico OU qualitativo. */
    public record ReportedResult(String parameterKey, BigDecimal numericValue, String qualitativeValue) {
        public ReportedResult {
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parameterKey do automonitoramento e obrigatorio");
            boolean numeric = numericValue != null;
            boolean qualitative = qualitativeValue != null && !qualitativeValue.isBlank();
            if (numeric == qualitative)
                throw new IllegalArgumentException("resultado de " + parameterKey
                        + " do automonitoramento exige exatamente uma forma:"
                        + " numerica OU qualitativa, como reportada");
        }
    }

    public SelfMonitoringReport {
        if (id == null) throw new IllegalArgumentException("id do relatorio e obrigatorio");
        if (dischargerId == null) throw new IllegalArgumentException("gerador do relatorio e obrigatorio");
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart))
            throw new IllegalArgumentException("periodo do automonitoramento invalido");
        if (results == null || results.isEmpty())
            throw new IllegalArgumentException("automonitoramento sem resultado nao e relatorio");
        results = List.copyOf(results);
        long distinct = results.stream().map(ReportedResult::parameterKey).distinct().count();
        if (distinct != results.size())
            throw new IllegalArgumentException("automonitoramento com parametro duplicado no periodo");
        if (reportedByRef == null || reportedByRef.isBlank())
            throw new IllegalArgumentException("automonitoramento exige quem reportou (reportedByRef)");
        if (reportedAt == null) throw new IllegalArgumentException("automonitoramento exige o instante do reporte");
    }
}

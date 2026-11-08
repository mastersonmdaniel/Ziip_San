package br.com.ziipsan.core.quality;

import java.util.List;
import java.util.UUID;

/**
 * Publicacao de qualidade no portal do consumidor (P3.3) — SG-821/
 * TR-846 do levantamento 18g: resultados por ponto de coleta
 * monitorado e por sistema, SOMENTE de amostras validadas/publicadas
 * de rotina e de processo. Resultado de reclamacao (COMPLAINT) e dado
 * do protocolo do reclamante: NUNCA entra na publicacao aberta — sai
 * so pela resposta no protocolo (ED-35). Nenhum dado pessoal e
 * carregado nesta visao (ponto e sistema sao cadastro operacional).
 */
public final class PortalQualityPublication {

    public record PublishedResult(
            UUID pointId,
            String parameterKey,
            String resultText,
            String unit,
            ConformityEvaluator.Verdict verdict) {}

    private PortalQualityPublication() {}

    public static List<PublishedResult> forPoint(UUID pointId, List<MonthlyClosing.Entry> entries) {
        if (pointId == null) throw new IllegalArgumentException("pointId e obrigatorio");
        return filter(entries).stream()
                .filter(e -> e.sample().pointId().equals(pointId))
                .map(PortalQualityPublication::toPublished)
                .toList();
    }

    public static List<PublishedResult> forAllPoints(List<MonthlyClosing.Entry> entries) {
        return filter(entries).stream().map(PortalQualityPublication::toPublished).toList();
    }

    private static List<MonthlyClosing.Entry> filter(List<MonthlyClosing.Entry> entries) {
        if (entries == null) throw new IllegalArgumentException("entradas sao obrigatorias (podem ser vazias)");
        return entries.stream()
                .filter(e -> e.sample().type() == Sample.SampleType.ROUTINE
                        || e.sample().type() == Sample.SampleType.PROCESS_CONTROL)
                .filter(e -> e.sample().status() == Sample.Status.VALIDATED
                        || e.sample().status() == Sample.Status.PUBLISHED)
                .toList();
    }

    private static PublishedResult toPublished(MonthlyClosing.Entry e) {
        return new PublishedResult(e.sample().pointId(), e.result().parameterKey(),
                e.result().resultText(), e.result().unit(), e.assessment().verdict());
    }
}

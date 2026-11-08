package br.com.ziipsan.reporting.exportprofile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monta os dois documentos de exportacao da ED-82 a partir das fichas da
 * ED-77 e das series historicas (fechamentos congelados da P3.41).
 *
 * <p><b>ISO 37120:</b> exige mapeamento declarado para CADA indicador
 * ({@link Iso37120Mapping}); indicador sem mapeamento recusa o documento
 * inteiro - exportar parcialmente em nome da norma esconderia a lacuna.
 *
 * <p><b>PNQS:</b> NAO exige nem aceita referencia externa: o regulamento
 * do PNQS 2026 (v1.2) nao define leiaute nem formato de troca, logo o
 * documento sai na estrutura neutra do produto, com os avisos de
 * natureza (premio/selo, nao certificacao) e de estrutura neutra.
 */
public final class ExportProfileService {

    public ExportDocument exportIso37120(UUID tenantId, List<IndicatorSeries> series,
                                         List<Iso37120Mapping> mappings, Instant generatedAt) {
        requireSeries(series);
        // Correcao D3 da revisao da ED-82: duplicado e pre-validado e recusa com o
        // codigo nomeado do pacote; Collectors.toMap lancaria IllegalStateException
        // crua ("Duplicate key ..."), fora do padrao de recusas nomeadas (sonda J2).
        Map<String, Iso37120Mapping> byCode = new HashMap<>();
        if (mappings != null) {
            for (Iso37120Mapping mapping : mappings) {
                Iso37120Mapping previous = byCode.putIfAbsent(mapping.indicatorCode(), mapping);
                if (previous != null) {
                    throw new ExportValidationException("EXPORT_ISO_MAPPING_DUPLICATED",
                            "mapeamento ISO 37120 duplicado para o indicador " + mapping.indicatorCode()
                                    + ": um unico mapeamento declarado por indicador (referencia nao presumida)");
                }
            }
        }
        List<ExportLine> lines = new ArrayList<>();
        for (IndicatorSeries s : series) {
            Iso37120Mapping mapping = byCode.get(s.code());
            if (mapping == null) {
                throw new ExportValidationException("EXPORT_ISO_MAPPING_REQUIRED",
                        "indicador " + s.code() + " sem mapeamento declarado para a "
                                + ExportReferences.ISO_37120_REFERENCE
                                + ": a referencia da norma nao e presumida (nenhum mapeamento de fabrica)");
            }
            lines.add(line(s, mapping.iso37120Reference()));
        }
        return new ExportDocument(tenantId, ExportProfileKind.ISO_37120,
                ExportReferences.ISO_37120_REFERENCE, List.of(), generatedAt, lines);
    }

    public ExportDocument exportPnqs(UUID tenantId, List<IndicatorSeries> series, Instant generatedAt) {
        requireSeries(series);
        List<ExportLine> lines = new ArrayList<>();
        for (IndicatorSeries s : series) {
            lines.add(line(s, null));
        }
        return new ExportDocument(tenantId, ExportProfileKind.PNQS,
                ExportReferences.PNQS_REGULATION_REFERENCE,
                List.of(ExportReferences.PNQS_NATURE_NOTICE, ExportReferences.PNQS_NEUTRAL_STRUCTURE_NOTICE),
                generatedAt, lines);
    }

    private ExportLine line(IndicatorSeries s, String externalReference) {
        return new ExportLine(s.code(), s.sheet().definition().name(), s.sheet().definition().unit(),
                s.sheet().definition().periodicity(), s.sheet().confidenceGrade(),
                externalReference, s.points());
    }

    private void requireSeries(List<IndicatorSeries> series) {
        if (series == null || series.isEmpty()) {
            throw new ExportValidationException("EXPORT_SERIES_REQUIRED",
                    "exportacao sem nenhuma serie: os perfis nascem das fichas da ED-77 com serie historica");
        }
    }
}

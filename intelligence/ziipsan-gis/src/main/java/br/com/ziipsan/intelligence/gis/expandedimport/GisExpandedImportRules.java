package br.com.ziipsan.intelligence.gis.expandedimport;

import br.com.ziipsan.intelligence.gis.importer.GisImportRules;

import java.time.LocalDate;
import java.util.Set;

/**
 * Regras puras da GIS-18 (importacao ampliada), sobre o nucleo da
 * GIS-07 (P3.26): os formatos DWG/DXF/SHP/GPKG continuam na P3.26 e
 * NAO sao reimplementados aqui. Esta classe cobre o que a GIS-18
 * acrescenta (§9.1 etapa 1 e §13.2 linha GIS-18):
 *
 * <ul>
 *   <li>DGN v7: lido pelo worker GDAL, entra direto no staging;</li>
 *   <li>DGN v8: SOMENTE pela conversao assistida da implantacao para
 *       DWG ou DXF, com os dois hashes (regra herdada da GIS-07);</li>
 *   <li>INP do EPANET e do SWMM: o LEITOR e da GIS-12 (ED-63) — aqui o
 *       lote so e aceito com o SRS declarado (SG-1339) e marcado para
 *       roteamento ao leitor da GIS-12; nenhum parser de INP e
 *       escrito nesta entrega;</li>
 *   <li>CSV de pontos GNSS: exige os metadados de precisao por ponto
 *       (metodo e precisao estimada) — ponto sem metadado e recusado,
 *       nao presumido.</li>
 * </ul>
 *
 * <p>Atribuicao de formatos (correcao D8 da revisao cruzada): DGN_V7,
 * DGN_V8 e CSV_GNSS JA sao formatos da GIS-07 — estao em
 * {@code GisImportRules.SOURCE_FORMATS} (P3.26) e no CHECK de
 * {@code source_format} da V13_11. O genuinamente NOVO da GIS-18 e
 * {@code INP_EPANET}/{@code INP_SWMM} (mais a regra de metadado GNSS
 * por ponto e a janela de 60 dias); sobre os formatos da GIS-07 esta
 * classe so acrescenta a regra de abertura de lote ampliado.
 */
public final class GisExpandedImportRules {

    /**
     * Formatos NOVOS da GIS-18: somente o par INP. DGN_V7/DGN_V8/
     * CSV_GNSS ja existiam na GIS-07 (ver javadoc da classe).
     */
    public static final Set<String> EXPANDED_FORMATS = Set.of(
            "INP_EPANET", "INP_SWMM");

    /**
     * Formatos que a abertura de lote ampliado da GIS-18 cobre: os
     * novos (INP) mais os da GIS-07 sobre os quais a GIS-18
     * acrescenta regra (DGN v7 direto, DGN v8 assistido, CSV GNSS
     * com metadado por ponto).
     */
    public static final Set<String> COVERED_FORMATS = Set.of(
            "DGN_V7", "DGN_V8", "CSV_GNSS", "INP_EPANET", "INP_SWMM");

    /** Metodos de captura GNSS aceitos (metadado de precisao, §9.1/1). */
    public static final Set<String> GNSS_FIX_METHODS = Set.of(
            "RTK_FIXED", "RTK_FLOAT", "DGPS", "AUTONOMOUS");

    /** Prazo maximo do ensaio da amostra de rede: 60 dias (GIS-18). */
    public static final int SAMPLE_MAX_DAYS = 60;

    private GisExpandedImportRules() {
    }

    /** Verdadeiro somente para os formatos novos da GIS-18 (INP). */
    public static boolean isExpandedFormat(String format) {
        return format != null && EXPANDED_FORMATS.contains(format);
    }

    /** Verdadeiro para todo formato aceito na abertura ampliada. */
    public static boolean isCoveredFormat(String format) {
        return format != null && COVERED_FORMATS.contains(format);
    }

    /**
     * Leitor de destino do lote ampliado (roteamento declarado na
     * abertura, correcao D3): INP vai ao leitor da GIS-12 (ED-63);
     * DGN e CSV GNSS vao ao worker GDAL (o mesmo da GIS-07). Nenhum
     * leitor e implementado aqui — a rota so registra o destino.
     */
    public static String readerRoute(String sourceFormat) {
        return switch (sourceFormat) {
            case "INP_EPANET", "INP_SWMM" -> "GIS-12_INP_READER";
            case "DGN_V7", "DGN_V8", "CSV_GNSS" -> "GDAL_WORKER";
            default -> throw new IllegalArgumentException(
                    "formato fora da GIS-18: " + sourceFormat);
        };
    }

    /**
     * Validacao de abertura de lote ampliado. Delega a conversao
     * assistida (DGN v8/DWG) e os hashes a GIS-07
     * ({@link GisImportRules#ensureConversionRecorded}); acrescenta:
     * DGN v7 nao tem conversao; INP exige SRS declarado; CSV GNSS nao
     * tem conversao nem SRS de desenho (pontos ja vem em 4674 com
     * metadado por ponto, validado em {@link #ensureGnssPoint}).
     */
    public static void ensureExpandedBatch(String sourceFormat,
                                           String convertedFormat,
                                           String originalSha256,
                                           String convertedSha256,
                                           String conversionTool,
                                           Integer sourceSrsEpsg,
                                           String srsSource) {
        switch (sourceFormat) {
            case "DGN_V7" -> {
                if (convertedSha256 != null) {
                    throw new IllegalArgumentException(
                            "DGN v7 e lido direto (GDAL): nao tem conversao registrada");
                }
                if (!GisImportRules.isSha256(originalSha256)) {
                    throw new IllegalArgumentException(
                            "hash SHA-256 do arquivo original ausente ou invalido");
                }
            }
            case "DGN_V8" -> GisImportRules.ensureConversionRecorded(
                    "DGN_V8", convertedFormat, originalSha256,
                    convertedSha256, conversionTool);
            case "INP_EPANET", "INP_SWMM" -> {
                if (!GisImportRules.isSha256(originalSha256)) {
                    throw new IllegalArgumentException(
                            "hash SHA-256 do arquivo original ausente ou invalido");
                }
                // SG-1339 (parte INP da GIS-12): sem SRS declarado o INP
                // nao entra — INP nao carrega SRS.
                if (sourceSrsEpsg == null || srsSource == null || srsSource.isBlank()) {
                    throw new IllegalArgumentException(
                            "INP exige o SRS declarado na abertura do lote (SG-1339)");
                }
            }
            case "CSV_GNSS" -> {
                if (!GisImportRules.isSha256(originalSha256)) {
                    throw new IllegalArgumentException(
                            "hash SHA-256 do arquivo original ausente ou invalido");
                }
                if (convertedSha256 != null) {
                    throw new IllegalArgumentException(
                            "CSV GNSS nao tem conversao registrada");
                }
            }
            default -> throw new IllegalArgumentException(
                    "formato fora da GIS-18 (DWG/DXF/SHP/GPKG sao da GIS-07/P3.26): "
                            + sourceFormat);
        }
    }

    /**
     * Ponto GNSS (§9.1 etapa 1: "CSV de pontos GNSS com metadados de
     * precisao"): metodo de fixacao e precisao estimada sao
     * obrigatorios e a precisao tem de ser positiva. Ponto sem
     * metadado e recusado — precisao nunca e presumida.
     */
    public static void ensureGnssPoint(String fixMethod, Double accuracyM) {
        if (fixMethod == null || !GNSS_FIX_METHODS.contains(fixMethod)) {
            throw new IllegalArgumentException(
                    "ponto GNSS sem metodo de fixacao conhecido: " + fixMethod);
        }
        if (accuracyM == null || accuracyM <= 0) {
            throw new IllegalArgumentException(
                    "ponto GNSS sem precisao estimada positiva registrada");
        }
    }

    /** Amostra de rede do ensaio: concluida em ate 60 dias (GIS-18). */
    public static boolean withinSampleWindow(LocalDate startedOn,
                                             LocalDate finishedOn) {
        if (startedOn == null || finishedOn == null
                || finishedOn.isBefore(startedOn)) {
            return false;
        }
        return !finishedOn.isAfter(startedOn.plusDays(SAMPLE_MAX_DAYS));
    }
}

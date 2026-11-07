package br.com.ziipsan.intelligence.gis.importer;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras puras da GIS-07 (§9.1/§9.2), espelho legível das guardas do
 * banco na V13_11 — o banco é a autoridade (recusa inclusive por SQL
 * direto); esta classe existe para a api falhar cedo, com o erro certo,
 * e para as regras serem testáveis sem banco.
 *
 * <p>Conversão de DWG (ODA File Converter) e de DGN v8 são ETAPAS
 * ASSISTIDAS EXTERNAS: aqui só se registra e valida o resultado (hashes
 * e formato do convertido) — nenhum conversor é executado.
 */
public final class GisImportRules {

    /** Formatos aceitos pelo importador no núcleo da GIS-07. */
    public static final Set<String> SOURCE_FORMATS = Set.of(
            "DWG", "DXF", "SHP", "GPKG", "DGN_V7", "DGN_V8", "CSV_GNSS");

    /** Estados do lote (os mesmos do CHECK da V13_11). */
    public static final Set<String> BATCH_STATUSES = Set.of(
            "RECEIVED", "STAGED", "MAPPED", "CLEANED", "QUALITY_REPORTED",
            "PUBLISHED", "REJECTED", "FAILED");

    private static final Map<String, Set<String>> BATCH_TRANSITIONS = Map.of(
            "RECEIVED", Set.of("STAGED", "REJECTED", "FAILED"),
            "STAGED", Set.of("MAPPED", "REJECTED", "FAILED"),
            "MAPPED", Set.of("CLEANED", "REJECTED", "FAILED"),
            "CLEANED", Set.of("QUALITY_REPORTED", "REJECTED", "FAILED"),
            "QUALITY_REPORTED", Set.of("PUBLISHED", "REJECTED", "FAILED"),
            "PUBLISHED", Set.of(),
            "REJECTED", Set.of(),
            "FAILED", Set.of());

    /** EPSG do SAD69 e do Córrego Alegre: só pela grade do IBGE (§9.1/4). */
    public static final Set<Integer> LEGACY_DATUM_EPSG = Set.of(4618, 4225);

    /** Métodos de anonimização do ADR-0033/§12 item 8. */
    public static final Set<String> ANONYMIZATION_METHODS = Set.of(
            "SHIFTED_WITHIN_BLOCK", "AGGREGATED_BY_BLOCK", "SYNTHETIC");

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private GisImportRules() {
    }

    public static boolean canTransitionBatch(String from, String to) {
        return BATCH_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isSha256(String hash) {
        return hash != null && SHA256.matcher(hash).matches();
    }

    /**
     * Conversão assistida (§9.1 etapa 1): DWG só entra com o DXF do ODA
     * File Converter e os dois hashes; DGN v8 só entra convertido para
     * DWG ou DXF, também com os dois hashes. Os demais formatos do
     * núcleo não têm conversão registrada.
     */
    public static void ensureConversionRecorded(String sourceFormat,
                                                String convertedFormat,
                                                String originalSha256,
                                                String convertedSha256,
                                                String conversionTool) {
        if (!isSha256(originalSha256)) {
            throw new IllegalArgumentException(
                    "hash SHA-256 do arquivo original ausente ou inválido");
        }
        switch (sourceFormat) {
            case "DWG" -> {
                if (!"DXF".equals(convertedFormat) || !isSha256(convertedSha256)
                        || !"ODA_FILE_CONVERTER".equals(conversionTool)) {
                    throw new IllegalArgumentException(
                            "DWG exige conversão assistida pelo ODA File Converter "
                                    + "para DXF, com hash do original e do DXF");
                }
            }
            case "DGN_V8" -> {
                if (!("DWG".equals(convertedFormat) || "DXF".equals(convertedFormat))
                        || !isSha256(convertedSha256)
                        || !"ASSISTED_EXTERNAL".equals(conversionTool)) {
                    throw new IllegalArgumentException(
                            "DGN v8 exige conversão assistida para DWG ou DXF, "
                                    + "com hash do original e do convertido");
                }
            }
            default -> {
                if (convertedSha256 != null) {
                    throw new IllegalArgumentException(
                            "formato " + sourceFormat + " não tem conversão registrada");
                }
            }
        }
    }

    /**
     * SRS (§9.1 etapa 4): sem SRS o lote não avança; a escolha do
     * operador exige o responsável; SAD69/Córrego Alegre exigem a grade
     * do IBGE com a referência registrada (a grade em si é aplicada pelo
     * worker/GDAL — aqui não se inventa grade nem parâmetro).
     */
    public static void ensureSrsReady(Integer sourceSrsEpsg, String srsSource,
                                      boolean declaredByPresent,
                                      String datumTransform, String ibgeGridRef,
                                      boolean controlResidualPresent) {
        if (sourceSrsEpsg == null) {
            throw new IllegalArgumentException(
                    "arquivo sem SRS recusado: declare o SRS ou registre a escolha");
        }
        if (srsSource == null || srsSource.isBlank()) {
            throw new IllegalArgumentException(
                    "origem do SRS (detectado ou escolha do operador) não registrada");
        }
        if ("OPERATOR_DECLARED".equals(srsSource) && !declaredByPresent) {
            throw new IllegalArgumentException(
                    "escolha de SRS pelo operador exige o responsável registrado");
        }
        if (LEGACY_DATUM_EPSG.contains(sourceSrsEpsg)
                && (!"IBGE_GRID".equals(datumTransform)
                || ibgeGridRef == null || ibgeGridRef.isBlank())) {
            throw new IllegalArgumentException(
                    "SAD69/Córrego Alegre exigem conversão pela grade do IBGE registrada");
        }
        if ("CONTROL_POINTS".equals(datumTransform) && !controlResidualPresent) {
            throw new IllegalArgumentException(
                    "ajuste por pontos de controle exige o resíduo registrado");
        }
    }

    /** Camada pessoal sem método de anonimização registrado é recusada. */
    public static void ensureAnonymization(boolean personalData, String method) {
        if (personalData && (method == null
                || !ANONYMIZATION_METHODS.contains(method))) {
            throw new IllegalArgumentException(
                    "camada pessoal sem método de anonimização registrado (ADR-0033)");
        }
    }
}

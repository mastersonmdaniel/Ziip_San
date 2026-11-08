package br.com.ziipsan.integration.esfinge;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.integration.esfinge.EsfingeLayout.LayoutTable;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.DatasetRecord;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.RemittanceSourceData;
import br.com.ziipsan.integration.esfinge.EsfingeValidator.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Arquivo da remessa e-Sfinge assistida (ED-33; modo assistido do
 * ADR-0004, como o SINISA da P4.2: o sistema gera e valida, o CLIENTE
 * baixa e envia no canal oficial — o aceite pede a remessa "validada e
 * baixada pelo cliente", nao o envio automatico).
 *
 * <p>O documento e JSON canonico (so pela classe {@code Json} do core),
 * com os registros na ordem das tabelas do leiaute e os campos em ordem
 * alfabetica dentro de cada registro, acompanhado do resumo SHA-256 do
 * conteudo — hash, versao do leiaute e situacao do leiaute (oficial ou
 * pendente) vao no proprio cabecalho e sao os valores que o registro da
 * remessa (EsfingeRemittance) guarda.
 *
 * <p>Geracao recusada quando: (a) a validacao tem erros; (b) a versao do
 * leiaute difere do parametro {@code esfinge.layout_version} do tenant —
 * remessa nunca sai declarando versao diferente da aceita pelo tenant.
 * Com o leiaute oficial pendente (ver EsfingeLayout), o arquivo sai com
 * {@code layoutOfficial = false} e o marcador da pendencia no cabecalho:
 * ele e um pacote assistido de conferencia, nao um arquivo oficial do
 * TCE/SC, e nada aqui o disfarca de oficial.
 */
public final class EsfingeRemittanceFile {

    /** Canal declarado no documento: geracao assistida, download e envio pelo cliente. */
    public static final String CHANNEL = "ASSISTED_DOWNLOAD_BY_CLIENT";

    private EsfingeRemittanceFile() {}

    /** Arquivo gerado: documento JSON, seu SHA-256 (hex, minusculas) e a versao do leiaute. */
    public record GeneratedFile(String json, String sha256, String layoutVersion, boolean layoutOfficial) {}

    public static GeneratedFile generate(UUID tenantId, YearMonth competence, EsfingeLayout layout,
                                         EsfingeParameters params, RemittanceSourceData data,
                                         ValidationResult validation, OffsetDateTime generatedAt) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(competence, "competencia e obrigatoria");
        Objects.requireNonNull(layout, "leiaute e obrigatorio");
        Objects.requireNonNull(params, "parametros sao obrigatorios");
        Objects.requireNonNull(data, "dados de origem sao obrigatorios");
        Objects.requireNonNull(validation, "resultado da validacao e obrigatorio");
        Objects.requireNonNull(generatedAt, "instante de geracao e obrigatorio");

        String acceptedVersion = params.layoutVersion();
        if (!layout.version().equals(acceptedVersion))
            throw new ApiException.Conflict("esfinge-layout-version-mismatch",
                    "Leiaute carregado (" + layout.version() + ") difere da versao aceita do tenant ("
                            + acceptedVersion + "): a remessa nao e gerada.");
        if (!validation.valid())
            throw new ApiException.Conflict("esfinge-remittance-invalid",
                    "Remessa com " + validation.errors().size() + " erro(s) de consistencia nao gera arquivo: "
                            + validation.errors().get(0).message());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("system", "e-Sfinge");
        document.put("channel", CHANNEL);
        document.put("tenantId", tenantId.toString());
        document.put("competence", competence.toString());
        document.put("layoutVersion", layout.version());
        document.put("layoutOfficial", layout.official());
        if (!layout.official())
            document.put("layoutStatus", EsfingeLayout.OFFICIAL_LAYOUT_PENDING_MARKER);
        if (layout.normReference() != null) document.put("layoutNormReference", layout.normReference());
        document.put("generatedAt", generatedAt.toString());

        Map<String, Object> activeDebt = new LinkedHashMap<>();
        activeDebt.put("source", "ED-31");
        activeDebt.put("enrolledInPeriod", data.activeDebt().enrolledInPeriod().toPlainString());
        activeDebt.put("settledInPeriod", data.activeDebt().settledInPeriod().toPlainString());
        activeDebt.put("activeBalance", data.activeDebt().activeBalance().toPlainString());
        activeDebt.put("enrollmentCount", data.activeDebt().enrollmentCount());
        activeDebt.put("monthlyBalanceFrozen", data.activeDebt().frozen());
        document.put("activeDebt", activeDebt);

        Map<String, Object> accounting = new LinkedHashMap<>();
        accounting.put("source", "ED-32");
        accounting.put("totalDebit", data.accounting().totalDebit().toPlainString());
        accounting.put("totalCredit", data.accounting().totalCredit().toPlainString());
        accounting.put("entryCount", data.accounting().entryCount());
        accounting.put("balanced", data.accounting().balanced());
        document.put("accounting", accounting);

        List<Map<String, Object>> tables = new ArrayList<>();
        for (LayoutTable table : layout.tables()) {
            Map<String, Object> tableDoc = new LinkedHashMap<>();
            tableDoc.put("table", table.code());
            List<Map<String, Object>> records = new ArrayList<>();
            for (DatasetRecord rec : data.recordsOf(table.code())) {
                Map<String, Object> recDoc = new LinkedHashMap<>();
                recDoc.put("fields", new TreeMap<>(rec.values()));
                recDoc.put("sourceReference", rec.sourceReference());
                records.add(recDoc);
            }
            tableDoc.put("records", records);
            tables.add(tableDoc);
        }
        document.put("tables", tables);
        document.put("recordCount", data.records().size());

        String json = Json.write(document);
        return new GeneratedFile(json, sha256(json), layout.version(), layout.official());
    }

    /** Resumo SHA-256 do conteudo, para evidencia e conferencia de integridade. */
    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}

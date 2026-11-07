package br.com.ziipsan.core.exit;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.support.TicketRetentionService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exportacao de fim de contrato e certificado de descarte (P3.16):
 * renderizadores puros em CSV, JSON e XML, manifesto com hashes e
 * dicionario de dados gerado dos metadados do schema. Sem banco:
 * quem chama entrega as linhas e os metadados (na integracao, lidos
 * de {@code information_schema} do schema do tenant); ausencia de
 * metadado e erro explicito, nunca dicionario vazio silencioso.
 *
 * <p>Regras:
 * <ul>
 *   <li>JSON sai SOMENTE pela classe {@code Json} do core (padrao do
 *       projeto); CSV segue a RFC 4180 (aspas dobradas, campo entre
 *       aspas quando contem separador, aspas ou quebra de linha);
 *       XML escapa os cinco caracteres reservados;</li>
 *   <li>o manifesto lista, por tabela: formato, linhas e SHA-256 do
 *       conteudo renderizado; o hash geral e o SHA-256 do JSON
 *       canonico do manifesto (chaves na ordem de insercao);</li>
 *   <li>o certificado carrega: hash da exportacao e hash de cada
 *       arquivo do manifesto, expiracao de backups e de copias
 *       imutaveis (instantes gravados no processo; sem eles, o
 *       certificado recusa — nao se certifica expiracao desconhecida)
 *       e a excecao dos chamados: eles NAO sao eliminados com o
 *       tenant; ficam retidos ate o {@code purge_at} da P3.19
 *       (fim da vigencia + 5 anos).</li>
 * </ul>
 */
public final class TenantExitExport {

    /** Formatos da exportacao (CHECK de {@code platform.tenant_export} apos a V10_5). */
    public enum Format {
        CSV, JSON, XML
    }

    /** Metadado de uma coluna, como lido de {@code information_schema.columns}. */
    public record ColumnMeta(String table, String column, String dataType,
                             boolean nullable, String comment) {
        public ColumnMeta {
            if (table == null || table.isBlank() || column == null || column.isBlank()
                    || dataType == null || dataType.isBlank()) {
                throw new IllegalArgumentException("table, column e dataType sao obrigatorios no metadado");
            }
        }
    }

    /** Um arquivo logico da exportacao: a tabela e suas linhas (mapas ordenados). */
    public record ExportTable(String table, List<Map<String, Object>> rows) {
        public ExportTable {
            if (table == null || table.isBlank()) {
                throw new IllegalArgumentException("table e obrigatoria");
            }
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /** Entrada do manifesto para uma tabela exportada. */
    public record ManifestEntry(String table, String format, int rows, String sha256) {
    }

    private TenantExitExport() {
    }

    /** SHA-256 em hexadecimal minusculo do conteudo UTF-8. */
    public static String sha256Hex(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content e obrigatorio");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    // ------------------------------------------------------------------
    // Renderizadores
    // ------------------------------------------------------------------

    /** Renderiza a tabela no formato pedido. JSON via {@code Json} do core. */
    public static String render(ExportTable table, Format format) {
        if (table == null || format == null) {
            throw new IllegalArgumentException("table e format sao obrigatorios");
        }
        return switch (format) {
            case JSON -> Json.write(table.rows());
            case CSV -> renderCsv(table);
            case XML -> renderXml(table);
        };
    }

    private static String renderCsv(ExportTable table) {
        if (table.rows().isEmpty()) {
            return "";
        }
        List<String> columns = new ArrayList<>(table.rows().get(0).keySet());
        StringBuilder out = new StringBuilder();
        out.append(String.join(",", columns.stream().map(TenantExitExport::csvField).toList())).append("\r\n");
        for (Map<String, Object> row : table.rows()) {
            List<String> fields = new ArrayList<>();
            for (String column : columns) {
                Object value = row.get(column);
                fields.add(csvField(value == null ? "" : value.toString()));
            }
            out.append(String.join(",", fields)).append("\r\n");
        }
        return out.toString();
    }

    private static String csvField(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String renderXml(ExportTable table) {
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<table name=\"").append(xmlEscape(table.table())).append("\">\n");
        for (Map<String, Object> row : table.rows()) {
            out.append("  <row>\n");
            for (Map.Entry<String, Object> cell : row.entrySet()) {
                out.append("    <").append(cell.getKey()).append(">");
                if (cell.getValue() != null) {
                    out.append(xmlEscape(cell.getValue().toString()));
                }
                out.append("</").append(cell.getKey()).append(">\n");
            }
            out.append("  </row>\n");
        }
        out.append("</table>\n");
        return out.toString();
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    // ------------------------------------------------------------------
    // Manifesto e dicionario
    // ------------------------------------------------------------------

    /** Manifesto: uma entrada por tabela, com o hash do conteudo renderizado. */
    public static List<ManifestEntry> manifest(List<ExportTable> tables, Format format) {
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("Exportacao sem tabela nao gera manifesto");
        }
        List<ManifestEntry> entries = new ArrayList<>();
        for (ExportTable table : tables) {
            entries.add(new ManifestEntry(table.table(), format.name(),
                    table.rows().size(), sha256Hex(render(table, format))));
        }
        return List.copyOf(entries);
    }

    /** Hash geral da exportacao: SHA-256 do JSON canonico do manifesto. */
    public static String manifestHash(List<ManifestEntry> manifest) {
        if (manifest == null || manifest.isEmpty()) {
            throw new IllegalArgumentException("manifest e obrigatorio");
        }
        List<Map<String, Object>> canonical = new ArrayList<>();
        for (ManifestEntry entry : manifest) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("table", entry.table());
            item.put("format", entry.format());
            item.put("rows", entry.rows());
            item.put("sha256", entry.sha256());
            canonical.add(item);
        }
        return sha256Hex(Json.write(canonical));
    }

    /**
     * Dicionario de dados gerado dos metadados do schema: uma entrada
     * por coluna, na ordem recebida. Metadado vazio e erro explicito:
     * dicionario gerado do schema nao pode nascer de schema nenhum.
     */
    public static List<Map<String, Object>> dataDictionary(List<ColumnMeta> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Sem metadados do schema: o dicionario de dados nao pode ser gerado");
        }
        List<Map<String, Object>> dictionary = new ArrayList<>();
        for (ColumnMeta column : columns) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("table", column.table());
            entry.put("column", column.column());
            entry.put("data_type", column.dataType());
            entry.put("nullable", column.nullable());
            entry.put("comment", column.comment());
            dictionary.add(entry);
        }
        return List.copyOf(dictionary);
    }

    // ------------------------------------------------------------------
    // Certificado de descarte
    // ------------------------------------------------------------------

    /**
     * Conteudo do certificado de descarte (serializar com {@code Json}
     * do core; o hash do certificado e {@link #sha256Hex} desse JSON).
     * Exige os instantes de expiracao de backups e de copias imutaveis
     * e o hash da exportacao: certificar sem eles seria afirmar o que
     * nao se sabe. A excecao dos chamados usa a regra da P3.19
     * ({@link TicketRetentionService#purgeDate}: vigencia + 5 anos).
     */
    public static Map<String, Object> certificate(String tenantId, LocalDate contractEnd,
                                                  String exportHash, List<ManifestEntry> manifest,
                                                  OffsetDateTime keyDestroyedAt,
                                                  OffsetDateTime backupExpiresAt,
                                                  OffsetDateTime immutableCopyExpiresAt,
                                                  OffsetDateTime issuedAt) {
        if (tenantId == null || tenantId.isBlank() || contractEnd == null
                || exportHash == null || exportHash.isBlank()
                || manifest == null || manifest.isEmpty()
                || keyDestroyedAt == null || backupExpiresAt == null
                || immutableCopyExpiresAt == null || issuedAt == null) {
            throw new IllegalArgumentException("Certificado exige tenant, fim do contrato, hash da "
                    + "exportacao, manifesto com o hash de cada arquivo, destruicao da chave, "
                    + "expiracoes de backup e de copias imutaveis e o instante de emissao");
        }
        Map<String, Object> cert = new LinkedHashMap<>();
        cert.put("tenant_id", tenantId);
        cert.put("contract_end", contractEnd.toString());
        cert.put("export_hash", exportHash);
        List<Map<String, Object>> files = new ArrayList<>();
        if (manifest != null) {
            for (ManifestEntry entry : manifest) {
                Map<String, Object> file = new LinkedHashMap<>();
                file.put("table", entry.table());
                file.put("format", entry.format());
                file.put("rows", entry.rows());
                file.put("sha256", entry.sha256());
                files.add(file);
            }
        }
        cert.put("files", files);
        cert.put("crypto_shredding_key_destroyed_at", keyDestroyedAt.toString());
        cert.put("backup_expires_at", backupExpiresAt.toString());
        cert.put("immutable_copy_expires_at", immutableCopyExpiresAt.toString());
        Map<String, Object> ticketsException = new LinkedHashMap<>();
        ticketsException.put("scope", "support_tickets");
        ticketsException.put("rule", "retained_until_purge_at");
        ticketsException.put("purge_at", TicketRetentionService.purgeDate(contractEnd).toString());
        cert.put("exceptions", List.of(ticketsException));
        cert.put("issued_at", issuedAt.toString());
        return cert;
    }
}

package br.com.ziipsan.core.exit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros da exportacao e do certificado (P3.16): formatos,
 * manifesto com hash, dicionario gerado dos metadados e a excecao
 * dos chamados da P3.19 no certificado.
 */
class TenantExitExportTest {

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private static final TenantExitExport.ExportTable CUSTOMERS = new TenantExitExport.ExportTable(
            "customers", List.of(
                    row("id", "1", "name", "Padaria Sintetica, Ltda", "note", "disse \"sim\""),
                    row("id", "2", "name", "Tenant <Sintetico> & Cia", "note", null)));

    @Test
    @DisplayName("SHA-256: vetor conhecido de 'abc'")
    void sha256KnownVector() {
        assertThat(TenantExitExport.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("JSON: sai pela classe Json do core, com os valores da tabela")
    void renderJson() {
        String json = TenantExitExport.render(CUSTOMERS, TenantExitExport.Format.JSON);
        assertThat(json).contains("\"name\":\"Padaria Sintetica, Ltda\"");
        assertThat(json).contains("\"note\":null");
    }

    @Test
    @DisplayName("CSV RFC 4180: virgula e aspas no valor forcam aspas dobradas")
    void renderCsvEscapes() {
        String csv = TenantExitExport.render(CUSTOMERS, TenantExitExport.Format.CSV);
        assertThat(csv).startsWith("id,name,note\r\n");
        assertThat(csv).contains("\"Padaria Sintetica, Ltda\",\"disse \"\"sim\"\"\"");
        assertThat(csv).contains("Tenant <Sintetico> & Cia,");
    }

    @Test
    @DisplayName("XML: caracteres reservados escapados e celula nula vazia")
    void renderXmlEscapes() {
        String xml = TenantExitExport.render(CUSTOMERS, TenantExitExport.Format.XML);
        assertThat(xml).contains("<table name=\"customers\">");
        assertThat(xml).contains("Tenant &lt;Sintetico&gt; &amp; Cia");
        assertThat(xml).contains("<note></note>");
    }

    @Test
    @DisplayName("manifesto: linhas e hash por tabela; hash geral deterministico")
    void manifestAndHash() {
        var manifest = TenantExitExport.manifest(List.of(CUSTOMERS), TenantExitExport.Format.JSON);
        assertThat(manifest).hasSize(1);
        assertThat(manifest.get(0).rows()).isEqualTo(2);
        assertThat(manifest.get(0).sha256())
                .isEqualTo(TenantExitExport.sha256Hex(
                        TenantExitExport.render(CUSTOMERS, TenantExitExport.Format.JSON)));
        assertThat(TenantExitExport.manifestHash(manifest))
                .isEqualTo(TenantExitExport.manifestHash(
                        TenantExitExport.manifest(List.of(CUSTOMERS), TenantExitExport.Format.JSON)));
        assertThatThrownBy(() -> TenantExitExport.manifest(List.of(), TenantExitExport.Format.CSV))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dicionario: gerado dos metadados; sem metadado, erro explicito")
    void dataDictionaryFromSchema() {
        var dictionary = TenantExitExport.dataDictionary(List.of(
                new TenantExitExport.ColumnMeta("customers", "id", "uuid", false, "PK"),
                new TenantExitExport.ColumnMeta("customers", "name", "varchar(200)", false, null)));
        assertThat(dictionary).hasSize(2);
        assertThat(dictionary.get(0)).containsEntry("table", "customers")
                .containsEntry("data_type", "uuid").containsEntry("nullable", false);
        assertThatThrownBy(() -> TenantExitExport.dataDictionary(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dicionario");
    }

    @Test
    @DisplayName("certificado: hashes, expiracoes e a excecao dos chamados (vigencia + 5 anos = 2031-12-31)")
    void certificateContent() {
        var manifest = TenantExitExport.manifest(List.of(CUSTOMERS), TenantExitExport.Format.JSON);
        OffsetDateTime issued = OffsetDateTime.of(2027, 2, 20, 12, 0, 0, 0, ZoneOffset.UTC);
        Map<String, Object> cert = TenantExitExport.certificate(
                "00000000-0000-0000-0000-000000000001", LocalDate.of(2026, 12, 31),
                TenantExitExport.manifestHash(manifest), manifest,
                OffsetDateTime.of(2027, 2, 20, 11, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2027, 3, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2027, 3, 22, 0, 0, 0, 0, ZoneOffset.UTC), issued);
        assertThat(cert).containsEntry("export_hash", TenantExitExport.manifestHash(manifest));
        assertThat(cert).containsEntry("backup_expires_at", "2027-03-22T00:00Z");
        assertThat(cert).containsEntry("immutable_copy_expires_at", "2027-03-22T00:00Z");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exceptions = (List<Map<String, Object>>) cert.get("exceptions");
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0)).containsEntry("scope", "support_tickets")
                .containsEntry("purge_at", "2031-12-31");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) cert.get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).containsEntry("table", "customers");
    }

    @Test
    @DisplayName("certificado sem manifesto (hash por arquivo): recusado, nunca emitido sem hashes (revisao cruzada)")
    void certificateRequiresManifest() {
        OffsetDateTime now = OffsetDateTime.of(2027, 2, 20, 12, 0, 0, 0, ZoneOffset.UTC);
        assertThatThrownBy(() -> TenantExitExport.certificate(
                "t", LocalDate.of(2026, 12, 31), "h", null, now, now, now, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantExitExport.certificate(
                "t", LocalDate.of(2026, 12, 31), "h", List.of(), now, now, now, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("certificado sem expiracao de backup conhecida: recusado, nunca afirmado")
    void certificateRequiresExpiries() {
        var manifest = TenantExitExport.manifest(List.of(CUSTOMERS), TenantExitExport.Format.JSON);
        OffsetDateTime now = OffsetDateTime.of(2027, 2, 20, 12, 0, 0, 0, ZoneOffset.UTC);
        assertThatThrownBy(() -> TenantExitExport.certificate(
                "t", LocalDate.of(2026, 12, 31), "h", manifest, now, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

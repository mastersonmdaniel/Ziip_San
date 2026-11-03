package br.com.ziipsan.platform.audit;

import br.com.ziipsan.platform.audit.AuditTrailExportService.ExportRow;
import br.com.ziipsan.platform.audit.AuditTrailExportService.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditTrailExportTest {

    private static final UUID TENANT = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID USER = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ENTITY = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private static ExportRow row() {
        return new ExportRow(
                OffsetDateTime.of(2024, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC),
                TENANT, USER, "customers", ENTITY, "READ", null, true, "audit_log");
    }

    @Test
    @DisplayName("JSONL: uma linha por evento, so metadados — nenhum campo de payload, IP ou agente")
    void jsonlHasMetadataOnly() {
        String out = new AuditTrailExportService(null).format(List.of(row()), Format.JSONL);
        assertThat(out).contains("\"entity_type\":\"customers\"")
                .contains("\"action\":\"READ\"")
                .contains("\"success\":true")
                .doesNotContain("ip_address")
                .doesNotContain("user_agent")
                .doesNotContain("old_values")
                .doesNotContain("new_values")
                .doesNotContain("203.0.113");
        assertThat(out.trim().split("\n")).hasSize(1);
    }

    @Test
    @DisplayName("CEF: cabecalho padrao, extensoes com metadados, sem IP")
    void cefShape() {
        String out = new AuditTrailExportService(null).format(List.of(row()), Format.CEF);
        assertThat(out).startsWith("CEF:0|Ziip|ZiipSAN|1.0|AUDIT|READ|5|")
                .contains("entityType=customers")
                .contains("success=true")
                .doesNotContain("ip_address");
    }

    @Test
    @DisplayName("CEF: pipe e igual em campo sao escapados")
    void cefEscapes() {
        ExportRow tricky = new ExportRow(row().occurredAt(), TENANT, USER,
                "a|b=c", ENTITY, "READ", null, true, "audit_log");
        String out = new AuditTrailExportService(null).format(List.of(tricky), Format.CEF);
        assertThat(out).contains("entityType=a\\|b\\=c");
    }

    @Test
    @DisplayName("entrega desligada: recusa antes de tocar no banco ou no storage")
    void disabledDeliveryRefuses() {
        var service = new AuditTrailExportService(null);
        assertThatThrownBy(() -> service.deliver(TENANT, null,
                OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                Format.JSONL, false, null, "bucket"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("log.export_enabled");
    }
}

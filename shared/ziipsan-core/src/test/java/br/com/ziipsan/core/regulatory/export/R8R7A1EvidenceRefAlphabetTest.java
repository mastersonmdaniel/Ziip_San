package br.com.ziipsan.core.regulatory.export;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Correcao R8-R7A-1 (Rodada 8, escolha (a)): o construtor de
 * {@link SubmissionRecord} valida o alfabeto de {@code agencyCode} e
 * {@code periodRef} ([A-Za-z0-9_-]+) — o mesmo alfabeto do CHECK
 * {@code ck_reg_export_evidence_ref_format} do SQL pendente
 * ED-97__regulatory_export_social_tariff.sql:56-57. Falha rapida na
 * fronteira Java; nenhum valor legitimo existente quebra (ARIS/AGIR/
 * ARESC, 2026-10/2026-Q3). Produtor do lote R8-B, sessao
 * 8b71ddfb-41b3-40ac-9ecb-88a13c7d22a3.
 */
class R8R7A1EvidenceRefAlphabetTest {

    private static final String SQL_EVIDENCE_REF_PATTERN =
            "^ED97-EVIDENCE:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+:[0-9a-f]{16}$";

    private SubmissionRecord record(String agencyCode, String periodRef) {
        return new SubmissionRecord(UUID.randomUUID(), UUID.randomUUID(), agencyCode, "ARIS_SISAGUA",
                "[A DEFINIR]", periodRef, ExportPackage.Kind.AGGREGATE_PUBLIC, "a".repeat(64),
                SubmissionRecord.Status.SUBMITTED_BY_TENANT, Instant.now(), "SYN-PROTO-1");
    }

    @Test void periodWithDotIsRefusedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> record("ARIS", "2026.10"),
                "periodo com ponto quebraria o CHECK de evidence_ref");
    }

    @Test void agencyWithSpaceIsRefusedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> record("ARIS SP", "2026-10"),
                "agencia com espaco quebraria o CHECK de evidence_ref");
    }

    @Test void existingAgencyAndPeriodValuesPassAndMatchSqlCheck() {
        SubmissionRecord aris = record("ARIS", "2026-10");
        assertTrue(aris.evidenceRef().matches(SQL_EVIDENCE_REF_PATTERN),
                "ARIS/2026-10 casa com o padrao do CHECK: " + aris.evidenceRef());
        SubmissionRecord agir = record("AGIR", "2026-Q3");
        assertTrue(agir.evidenceRef().matches(SQL_EVIDENCE_REF_PATTERN),
                "AGIR/2026-Q3 casa com o padrao do CHECK: " + agir.evidenceRef());
        SubmissionRecord aresc = record("ARESC", "2026-10");
        assertTrue(aresc.evidenceRef().matches(SQL_EVIDENCE_REF_PATTERN),
                "ARESC/2026-10 casa com o padrao do CHECK: " + aresc.evidenceRef());
    }
}

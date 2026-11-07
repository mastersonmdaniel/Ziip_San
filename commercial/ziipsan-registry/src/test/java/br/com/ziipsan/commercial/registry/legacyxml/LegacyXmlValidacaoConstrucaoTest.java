package br.com.ziipsan.commercial.registry.legacyxml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correção A6 da revisão da P2.19: validações de CONSTRUÇÃO dos records do
 * importador, no padrão do {@code RegistroRetencaoXml} da P2.18 —
 * {@code RetentionResult} incompleto e documento aceito sem retenção /
 * recusado com retenção / origem diversa são inconstruíveis, em vez de só
 * "o serviço não faz". Massa 100% sintética, como em toda a tarefa.
 */
class LegacyXmlValidacaoConstrucaoTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String HASH = "a".repeat(64);
    private static final String KEY_A = "42250311222333000181550010000001231876543218";
    private static final String ISSUER = "11222333000181";

    private static LegacyXmlImportedDocument documento(LegacyXmlImportedDocument.Status status,
                                                       LegacyXmlRejection rejection, String origin,
                                                       String retentionKey, String retentionUri,
                                                       String retentionHashChain) {
        boolean aceito = status == LegacyXmlImportedDocument.Status.ACCEPTED;
        return new LegacyXmlImportedDocument(UUID.randomUUID(), TENANT, UUID.randomUUID(), "a.xml",
                aceito ? KEY_A : null,
                aceito ? YearMonth.of(2025, 3) : null,
                aceito ? ISSUER : null,
                aceito ? "142250000000001" : null,
                HASH, status, rejection, origin,
                retentionKey, retentionUri, retentionHashChain, "{\"tarefa\":\"P2.19\"}");
    }

    @Test
    @DisplayName("RetentionResult: chave/URI obrigatórias e hashes em 64 hex minúsculos")
    void retentionResultValidado() {
        assertThat(new LegacyXmlRetentionPort.RetentionResult("k", "u", HASH, HASH))
                .isNotNull();
        assertThatThrownBy(() -> new LegacyXmlRetentionPort.RetentionResult(null, "u", HASH, HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chave do objeto");
        assertThatThrownBy(() -> new LegacyXmlRetentionPort.RetentionResult("k", " ", HASH, HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URI");
        assertThatThrownBy(() -> new LegacyXmlRetentionPort.RetentionResult("k", "u", null, HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xmlSha256");
        assertThatThrownBy(() -> new LegacyXmlRetentionPort.RetentionResult("k", "u", "ABC123", HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xmlSha256");
        assertThatThrownBy(() -> new LegacyXmlRetentionPort.RetentionResult("k", "u", HASH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HashChain");
    }

    @Test
    @DisplayName("documento ACEITO exige retenção completa (chave, URI e elo) e nenhum motivo de recusa")
    void documentoAceitoExigeRetencao() {
        assertThat(documento(LegacyXmlImportedDocument.Status.ACCEPTED, null,
                "LEGACY_IMPORT", "retencao/k.xml", "emulador-s3://k.xml", HASH).accepted()).isTrue();
        assertThatThrownBy(() -> documento(LegacyXmlImportedDocument.Status.ACCEPTED, null,
                "LEGACY_IMPORT", null, "emulador-s3://k.xml", HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retenção");
        assertThatThrownBy(() -> documento(LegacyXmlImportedDocument.Status.ACCEPTED, null,
                "LEGACY_IMPORT", "retencao/k.xml", "emulador-s3://k.xml", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HashChain");
        assertThatThrownBy(() -> documento(LegacyXmlImportedDocument.Status.ACCEPTED, null,
                "LEGACY_IMPORT", "retencao/k.xml", "emulador-s3://k.xml", "123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HashChain");
        assertThatThrownBy(() -> documento(LegacyXmlImportedDocument.Status.ACCEPTED,
                LegacyXmlRejection.DUPLICATE, "LEGACY_IMPORT",
                "retencao/k.xml", "emulador-s3://k.xml", HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo de recusa");
    }

    @Test
    @DisplayName("documento RECUSADO exige motivo, nunca tem retenção e a origem é fixa LEGACY_IMPORT")
    void documentoRecusadoSemRetencaoEOrigemFixa() {
        assertThat(documento(LegacyXmlImportedDocument.Status.REJECTED,
                LegacyXmlRejection.DUPLICATE, "LEGACY_IMPORT", null, null, null).accepted()).isFalse();
        assertThatThrownBy(() -> documento(LegacyXmlImportedDocument.Status.REJECTED,
                LegacyXmlRejection.DUPLICATE, "LEGACY_IMPORT", "retencao/k.xml", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nunca tem referência de retenção");
        assertThatThrownBy(() -> documento(LegacyXmlImportedDocument.Status.REJECTED,
                LegacyXmlRejection.DUPLICATE, "LEGACY_IMPORT", null, null, HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nunca tem referência de retenção");
        assertThatThrownBy(() -> documento(LegacyXmlImportedDocument.Status.REJECTED,
                null, "LEGACY_IMPORT", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo da recusa");
        assertThatThrownBy(() -> documento(LegacyXmlImportedDocument.Status.REJECTED,
                LegacyXmlRejection.DUPLICATE, "SIMULADOR", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LEGACY_IMPORT");
    }
}

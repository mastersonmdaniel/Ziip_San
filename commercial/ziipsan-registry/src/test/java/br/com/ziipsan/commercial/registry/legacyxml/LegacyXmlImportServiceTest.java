package br.com.ziipsan.commercial.registry.legacyxml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Importador de XML anteriores à primeira produção (P2.19): TUDO sintético,
 * como manda o aceite e o ADR-0033 — os XML são fixtures montadas neste
 * arquivo, assinadas de verdade (XMLDSig do JDK) com o par de chaves de
 * TESTE gerado aqui, os CNPJs são os de teste já usados nas fixtures da
 * P2.18 (válidos pelo DV, de empresas fictícias) e nenhum dado é de piloto
 * real. Os XML reais — com dado pessoal do destinatário — só entram na
 * LC.10, em produção.
 *
 * <p>Chaves de acesso das fixtures (44 dígitos, DV módulo 11 calculado à
 * mão/fora do código sob teste): A e B do emitente esperado nas
 * competências 2025-03 e 2025-04; as demais variam um fator por vez, para
 * cada recusa ter exatamente um motivo.
 */
class LegacyXmlImportServiceTest {

    private static final String ISSUER = "11222333000181";
    private static final String OTHER_ISSUER = "34028316000103";
    private static final String KEY_A = "42250311222333000181550010000001231876543218";
    private static final String KEY_B = "42250411222333000181550010000001241876543227";
    private static final String KEY_OTHER = "42250334028316000103550010000002001876543230";
    private static final String KEY_NO_PROTOCOL = "42250311222333000181550010000001251876543247";
    private static final String KEY_TAMPERED = "42250511222333000181550010000001261876543250";
    // KEY_A com o último dígito trocado (8 → 9): o DV módulo 11 NÃO confere.
    private static final String KEY_BAD_DV = "42250311222333000181550010000001231876543219";
    private static final UUID TENANT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);

    private static KeyPair keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /** XML autorizado sintético no invólucro usual (nfeProc + protNFe), ainda SEM assinatura. */
    private static String xmlAutorizado(String accessKey, String issuerCnpj, boolean comProtocolo, String cStat) {
        return "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<NFe><infNFe Id=\"NFe" + accessKey + "\" versao=\"4.00\">"
                + "<ide><cUF>42</cUF><mod>55</mod><nNF>123</nNF>"
                + "<dhEmi>2025-03-10T08:00:00-03:00</dhEmi></ide>"
                + "<emit><CNPJ>" + issuerCnpj + "</CNPJ><xNome>Prestador Sintetico de Teste</xNome></emit>"
                + "<dest><CPF>11144477735</CPF><xNome>Destinatario Sintetico de Teste</xNome></dest>"
                + "<total><ICMSTot><vNF>150.00</vNF></ICMSTot></total>"
                + "</infNFe></NFe>"
                + (comProtocolo
                    ? "<protNFe><infProt><tpAmb>1</tpAmb><nProt>142250000000001</nProt>"
                      + "<cStat>" + cStat + "</cStat><xMotivo>Autorizado o uso</xMotivo></infProt></protNFe>"
                    : "")
                + "</nfeProc>";
    }

    private static String sha256Hex(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    /** XML com protocolo presente (nProt) mas SEM o elemento cStat — a alegação sem prova da correção A4. */
    private static String xmlComProtocoloSemCStat(String accessKey, String issuerCnpj) {
        return "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<NFe><infNFe Id=\"NFe" + accessKey + "\" versao=\"4.00\">"
                + "<ide><cUF>42</cUF><mod>55</mod><nNF>123</nNF>"
                + "<dhEmi>2025-03-10T08:00:00-03:00</dhEmi></ide>"
                + "<emit><CNPJ>" + issuerCnpj + "</CNPJ><xNome>Prestador Sintetico de Teste</xNome></emit>"
                + "<total><ICMSTot><vNF>150.00</vNF></ICMSTot></total>"
                + "</infNFe></NFe>"
                + "<protNFe><infProt><tpAmb>1</tpAmb><nProt>142250000000001</nProt>"
                + "<xMotivo>Autorizado o uso</xMotivo></infProt></protNFe>"
                + "</nfeProc>";
    }

    /** Retenção em memória com a semântica da porta: guarda os bytes e devolve o hash deles. */
    private static final class FakeRetention implements LegacyXmlRetentionPort {
        final List<byte[]> receivedBytes = new ArrayList<>();
        final List<String> receivedKeys = new ArrayList<>();
        /** Chave para a qual a retenção devolve um hash DIVERGENTE do arquivo (abort do lote — correções A2/A7-iii). */
        String chaveComHashDivergente;

        @Override
        public RetentionResult arquivarLegado(UUID tenantId, String accessKey, byte[] xmlBytes, String xmlSha256) {
            receivedKeys.add(accessKey);
            receivedBytes.add(xmlBytes.clone());
            try {
                String chain = sha256Hex(("elo-legado:" + accessKey).getBytes(StandardCharsets.UTF_8));
                String hashRegistrado = accessKey.equals(chaveComHashDivergente)
                        ? "0".repeat(64) : xmlSha256;
                return new RetentionResult("retencao/legado/" + tenantId + "/" + accessKey + ".xml",
                        "emulador-s3://retencao/legado/" + accessKey + ".xml", hashRegistrado, chain);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private record Fixture(InMemoryLegacyXmlImportStore store, FakeRetention retention,
                           LegacyXmlImportService service) {
    }

    private static Fixture fixture() {
        InMemoryLegacyXmlImportStore store = new InMemoryLegacyXmlImportStore();
        FakeRetention retention = new FakeRetention();
        return new Fixture(store, retention, new LegacyXmlImportService(
                CLOCK, store, new JdkXmlSignatureValidator(), retention));
    }

    private static LegacyXmlImportService.ImportCommand command(String batchKey,
                                                                List<LegacyXmlImportService.XmlFileEntry> files) {
        return new LegacyXmlImportService.ImportCommand(TENANT, LegacyXmlMapping.v1(), batchKey,
                ISSUER, UUID.randomUUID(), files);
    }

    private static LegacyXmlImportService.XmlFileEntry entry(String name, byte[] content) {
        return new LegacyXmlImportService.XmlFileEntry(name, content);
    }

    @Test
    @DisplayName("lote misto: 2 aceitos vão à retenção byte a byte; assinatura inválida, sem protocolo, outro emitente, duplicado e malformado são recusados com motivo")
    void loteMistoAceitaERecusaComMotivo() throws Exception {
        KeyPair keys = keys();
        byte[] a = SinteticoXmlSigner.signWithKeyValue(xmlAutorizado(KEY_A, ISSUER, true, "100"), keys);
        byte[] b = SinteticoXmlSigner.signWithKeyValue(xmlAutorizado(KEY_B, ISSUER, true, "100"), keys);
        byte[] tamperedSigned = SinteticoXmlSigner.signWithKeyValue(
                xmlAutorizado(KEY_TAMPERED, ISSUER, true, "100"), keys);
        byte[] tampered = new String(tamperedSigned, StandardCharsets.UTF_8)
                .replace("150.00", "999.00").getBytes(StandardCharsets.UTF_8);
        byte[] noProtocol = SinteticoXmlSigner.signWithKeyValue(
                xmlAutorizado(KEY_NO_PROTOCOL, ISSUER, false, null), keys);
        byte[] otherIssuer = SinteticoXmlSigner.signWithKeyValue(
                xmlAutorizado(KEY_OTHER, OTHER_ISSUER, true, "100"), keys);
        byte[] malformed = "<nfeProc><NFe>isto nao fecha".getBytes(StandardCharsets.UTF_8);

        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(command("lote-1", List.of(
                entry("a.xml", a), entry("b.xml", b), entry("adulterado.xml", tampered),
                entry("sem-protocolo.xml", noProtocol), entry("outro-emitente.xml", otherIssuer),
                entry("a-copia.xml", a), entry("malformado.xml", malformed))));

        assertThat(report.alreadyExisted()).isFalse();
        assertThat(report.totalReceived()).isEqualTo(7);
        assertThat(report.totalAccepted()).isEqualTo(2);
        assertThat(report.totalRejected()).isEqualTo(5);
        assertThat(report.rejectedByReason()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "SIGNATURE_INVALID", 1L,
                "MISSING_AUTHORIZATION_PROTOCOL", 1L,
                "ISSUER_MISMATCH", 1L,
                "DUPLICATE", 1L,
                "XML_INVALID", 1L));
        assertThat(report.acceptedByCompetence()).containsExactlyInAnyOrderEntriesOf(Map.of(
                YearMonth.of(2025, 3), 1L, YearMonth.of(2025, 4), 1L));

        // Aceitos: bytes EXATOS na retenção (sem alteração do arquivo), na ordem do lote.
        assertThat(f.retention().receivedKeys).containsExactly(KEY_A, KEY_B);
        assertThat(f.retention().receivedBytes.get(0)).isEqualTo(a);
        assertThat(f.retention().receivedBytes.get(1)).isEqualTo(b);

        // Registros persistidos: aceitos com origem LEGACY_IMPORT, hash e retenção; recusados sem retenção.
        List<LegacyXmlImportedDocument> docs = f.store().listDocuments(TENANT, report.batchId());
        assertThat(docs).hasSize(7);
        for (LegacyXmlImportedDocument doc : docs) {
            if (doc.accepted()) {
                assertThat(doc.origin()).isEqualTo("LEGACY_IMPORT");
                assertThat(doc.xmlSha256()).isEqualTo(sha256Hex(
                        doc.accessKey().equals(KEY_A) ? a : b));
                assertThat(doc.retentionKey()).contains(doc.accessKey());
                assertThat(doc.retentionUri()).isNotBlank();
                assertThat(doc.retentionHashChain()).matches("[0-9a-f]{64}");
                assertThat(doc.protocol()).isEqualTo("142250000000001");
                assertThat(doc.detailJson()).contains("LEGACY_IMPORT");
            } else {
                assertThat(doc.rejection()).isNotNull();
                assertThat(doc.retentionKey()).isNull();
                assertThat(doc.retentionUri()).isNull();
            }
        }
        // O relatório gravado no lote é JSON do core e traz os totais.
        LegacyXmlBatch stored = f.store().findBatch(TENANT, "SISTEMA_ANTIGO", "lote-1").orElseThrow();
        assertThat(stored.reportJson()).contains("\"totalAccepted\":2").contains("\"totalRejected\":5");

        // Correção A7(vi): o CPF do destinatário da fixture NUNCA aparece em
        // nenhuma saída persistida (relatório, detalhe por documento) — a
        // garantia "destinatário nunca persistido" travada por teste.
        assertThat(report.toJson()).doesNotContain("11144477735");
        assertThat(stored.reportJson()).doesNotContain("11144477735");
        for (LegacyXmlImportedDocument doc : docs) {
            assertThat(doc.detailJson()).doesNotContain("11144477735").doesNotContain("Destinatario");
        }
    }

    @Test
    @DisplayName("idempotência: mesma chave de lote devolve o relatório existente; lote novo com a mesma chave de acesso recusa por duplicidade")
    void idempotenciaEDuplicidadeEntreLotes() throws Exception {
        KeyPair keys = keys();
        byte[] a = SinteticoXmlSigner.signWithKeyValue(xmlAutorizado(KEY_A, ISSUER, true, "100"), keys);
        Fixture f = fixture();

        LegacyXmlImportService.BatchReport first = f.service().importar(command("lote-1", List.of(entry("a.xml", a))));
        assertThat(first.totalAccepted()).isEqualTo(1);

        LegacyXmlImportService.BatchReport again = f.service().importar(command("lote-1", List.of(entry("a.xml", a))));
        assertThat(again.alreadyExisted()).isTrue();
        assertThat(again.batchId()).isEqualTo(first.batchId());
        assertThat(f.retention().receivedKeys).hasSize(1);

        LegacyXmlImportService.BatchReport otherBatch = f.service().importar(command("lote-2", List.of(entry("a.xml", a))));
        assertThat(otherBatch.totalAccepted()).isZero();
        assertThat(otherBatch.rejectedByReason()).containsExactlyEntriesOf(Map.of("DUPLICATE", 1L));
        assertThat(f.retention().receivedKeys).hasSize(1);
    }

    @Test
    @DisplayName("conferência por competência: contagem do legado igual confere; divergente é apontada linha a linha; sem contagem do legado é erro")
    void conferenciaPorCompetencia() throws Exception {
        KeyPair keys = keys();
        byte[] a = SinteticoXmlSigner.signWithKeyValue(xmlAutorizado(KEY_A, ISSUER, true, "100"), keys);
        byte[] b = SinteticoXmlSigner.signWithKeyValue(xmlAutorizado(KEY_B, ISSUER, true, "100"), keys);
        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(
                command("lote-1", List.of(entry("a.xml", a), entry("b.xml", b))));

        assertThat(f.service().conferirContagem(TENANT, report.batchId(),
                Map.of(YearMonth.of(2025, 3), 1L, YearMonth.of(2025, 4), 1L)).allMatch()).isTrue();

        LegacyXmlImportService.CompetenceConference divergent = f.service().conferirContagem(
                TENANT, report.batchId(), Map.of(YearMonth.of(2025, 3), 2L, YearMonth.of(2025, 5), 1L));
        assertThat(divergent.allMatch()).isFalse();
        assertThat(divergent.lines()).hasSize(3);
        assertThat(divergent.lines()).anySatisfy(line -> {
            assertThat(line.competence()).isEqualTo(YearMonth.of(2025, 3));
            assertThat(line.expectedFromLegacy()).isEqualTo(2L);
            assertThat(line.importedAccepted()).isEqualTo(1L);
            assertThat(line.matches()).isFalse();
        });

        assertThatThrownBy(() -> f.service().conferirContagem(TENANT, report.batchId(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obrigatória");
    }

    @Test
    @DisplayName("emitente do corpo divergente da chave e modelo fora do mapeamento são recusados")
    void emitenteInconsistenteEModeloForaDoMapeamento() throws Exception {
        KeyPair keys = keys();
        // Chave embute o emitente esperado, mas o emit/CNPJ do corpo é o outro.
        byte[] inconsistent = SinteticoXmlSigner.signWithKeyValue(
                xmlAutorizado(KEY_A, OTHER_ISSUER, true, "100"), keys);
        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(
                command("lote-1", List.of(entry("inconsistente.xml", inconsistent))));
        assertThat(report.rejectedByReason()).containsExactlyEntriesOf(Map.of("ISSUER_MISMATCH", 1L));

        // Mapeamento que só aceita o modelo 75: o documento modelo 55 é recusado por modelo.
        byte[] a = SinteticoXmlSigner.signWithKeyValue(xmlAutorizado(KEY_A, ISSUER, true, "100"), keys);
        LegacyXmlMapping only75 = new LegacyXmlMapping("legacy-xml", "1", "SISTEMA_ANTIGO", java.util.Set.of("75"));
        LegacyXmlImportService.BatchReport modelReport = f.service().importar(
                new LegacyXmlImportService.ImportCommand(TENANT, only75, "lote-2",
                        ISSUER, UUID.randomUUID(), List.of(entry("a.xml", a))));
        assertThat(modelReport.rejectedByReason()).containsExactlyEntriesOf(Map.of("MODEL_NOT_ACCEPTED", 1L));
    }

    @Test
    @DisplayName("certificado de teste: XML assinado com X.509 autossinado de teste é aceito e o assinante é conferido; sem assinatura é recusado")
    void certificadoDeTeste() throws Exception {
        KeyPair keys = keys();
        java.security.cert.X509Certificate certificate = SinteticoXmlSigner.selfSignedTestCertificate(
                keys, "Prestador Sintetico de Teste:" + ISSUER);
        byte[] signed = SinteticoXmlSigner.signWithCertificate(
                xmlAutorizado(KEY_A, ISSUER, true, "100"), keys, certificate);

        JdkXmlSignatureValidator validator = new JdkXmlSignatureValidator();
        XmlSignatureValidatorPort.SignatureResult result = validator.validate(signed);
        assertThat(result.valida()).isTrue();
        assertThat(result.signerDocument()).contains(ISSUER);
        assertThat(validator.validate(xmlAutorizado(KEY_A, ISSUER, true, "100")
                .getBytes(StandardCharsets.UTF_8)).valida()).isFalse();

        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(
                command("lote-cert", List.of(entry("a.xml", signed))));
        assertThat(report.totalAccepted()).isEqualTo(1);
    }

    @Test
    @DisplayName("protocolo com cStat de não autorizado é recusado como sem autorização")
    void protocoloNaoAutorizado() throws Exception {
        KeyPair keys = keys();
        byte[] denied = SinteticoXmlSigner.signWithKeyValue(
                xmlAutorizado(KEY_A, ISSUER, true, "101"), keys);
        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(
                command("lote-1", List.of(entry("negado.xml", denied))));
        assertThat(report.rejectedByReason())
                .containsExactlyEntriesOf(Map.of("MISSING_AUTHORIZATION_PROTOCOL", 1L));
    }

    @Test
    @DisplayName("correção A4: nProt presente SEM cStat é recusado — número de protocolo sozinho não prova autorização")
    void protocoloSemCStatRecusado() throws Exception {
        KeyPair keys = keys();
        byte[] semCStat = SinteticoXmlSigner.signWithKeyValue(
                xmlComProtocoloSemCStat(KEY_A, ISSUER), keys);
        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(
                command("lote-1", List.of(entry("sem-cstat.xml", semCStat))));
        assertThat(report.totalAccepted()).isZero();
        assertThat(report.rejectedByReason())
                .containsExactlyEntriesOf(Map.of("MISSING_AUTHORIZATION_PROTOCOL", 1L));
        assertThat(f.retention().receivedKeys).isEmpty();
        List<LegacyXmlImportedDocument> docs = f.store().listDocuments(TENANT, report.batchId());
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).detailJson()).contains("sem cStat");
    }

    @Test
    @DisplayName("correção A1: assinatura VÁLIDA sobre fragmento irrelevante é recusada — ela não cobre o documento fiscal")
    void assinaturaSobreFragmentoIrrelevanteRecusada() throws Exception {
        KeyPair keys = keys();
        byte[] fragmento = SinteticoXmlSigner.signIrrelevantFragmentWithKeyValue(
                xmlAutorizado(KEY_A, ISSUER, true, "100"), keys);

        XmlSignatureValidatorPort.SignatureResult direta = new JdkXmlSignatureValidator().validate(fragmento);
        assertThat(direta.valida()).isFalse();
        assertThat(direta.detail()).contains("não cobre o documento fiscal");

        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(
                command("lote-1", List.of(entry("fragmento.xml", fragmento))));
        assertThat(report.totalAccepted()).isZero();
        assertThat(report.rejectedByReason())
                .containsExactlyEntriesOf(Map.of("SIGNATURE_INVALID", 1L));
        assertThat(f.retention().receivedKeys).isEmpty();
    }

    @Test
    @DisplayName("correções A2/A7-iii: hash divergente da retenção aborta o lote no meio; a retentativa NÃO devolve o lote parcial como relatório final")
    void loteInterrompidoNoMeioNaoViraRelatorioFinal() throws Exception {
        KeyPair keys = keys();
        byte[] a = SinteticoXmlSigner.signWithKeyValue(xmlAutorizado(KEY_A, ISSUER, true, "100"), keys);
        byte[] b = SinteticoXmlSigner.signWithKeyValue(xmlAutorizado(KEY_B, ISSUER, true, "100"), keys);
        Fixture f = fixture();
        f.retention().chaveComHashDivergente = KEY_B;
        LegacyXmlImportService.ImportCommand comando = command("lote-abortado",
                List.of(entry("a.xml", a), entry("b.xml", b)));

        // O abort desenhado: a retenção registrou hash que não é o do arquivo.
        assertThatThrownBy(() -> f.service().importar(comando))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hash divergente");

        // O lote ficou persistido PARCIAL (1 documento) e SEM marcador de conclusão.
        LegacyXmlBatch parcial = f.store().findBatch(TENANT, "SISTEMA_ANTIGO", "lote-abortado").orElseThrow();
        assertThat(parcial.reportJson()).isNull();
        assertThat(f.store().listDocuments(TENANT, parcial.id())).hasSize(1);

        // Retentativa com a mesma chave: erro explícito, nunca relatório parcial como final.
        assertThatThrownBy(() -> f.service().importar(comando))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NÃO foi concluído");
        assertThat(f.retention().receivedKeys).hasSize(2);

        // E a conferência de contagem também recusa o lote não concluído (A2/A10).
        assertThatThrownBy(() -> f.service().conferirContagem(TENANT, parcial.id(),
                Map.of(YearMonth.of(2025, 3), 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("não foi concluído");
    }

    @Test
    @DisplayName("correção A10: conferência de lote inexistente é erro nomeado, não divergência contra zero")
    void conferenciaDeLoteInexistenteErroNomeado() {
        Fixture f = fixture();
        assertThatThrownBy(() -> f.service().conferirContagem(TENANT, UUID.randomUUID(),
                Map.of(YearMonth.of(2025, 3), 1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não encontrado");
    }

    @Test
    @DisplayName("correção A7-i: chave com DV errado e XML sem chave localizável são recusados por ACCESS_KEY_INVALID")
    void chaveInvalidaOuAusente() throws Exception {
        KeyPair keys = keys();
        byte[] dvErrado = SinteticoXmlSigner.signWithKeyValue(
                xmlAutorizado(KEY_BAD_DV, ISSUER, true, "100"), keys);
        byte[] semChave = ("<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<NFe><infNFe versao=\"4.00\"><ide><mod>55</mod></ide></infNFe></NFe></nfeProc>")
                .getBytes(StandardCharsets.UTF_8);
        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(command("lote-1",
                List.of(entry("dv-errado.xml", dvErrado), entry("sem-chave.xml", semChave))));
        assertThat(report.totalAccepted()).isZero();
        assertThat(report.rejectedByReason())
                .containsExactlyEntriesOf(Map.of("ACCESS_KEY_INVALID", 2L));
        assertThat(f.retention().receivedKeys).isEmpty();
    }

    @Test
    @DisplayName("correção A7-ii: DOCTYPE/XXE é recusado pelo parser seguro como XML_INVALID, antes de qualquer outra etapa")
    void doctypeXxeRecusado() throws Exception {
        byte[] xxe = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE nfeProc [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + xmlAutorizado(KEY_A, ISSUER, true, "100")).getBytes(StandardCharsets.UTF_8);
        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(
                command("lote-1", List.of(entry("xxe.xml", xxe))));
        assertThat(report.totalAccepted()).isZero();
        assertThat(report.rejectedByReason())
                .containsExactlyEntriesOf(Map.of("XML_INVALID", 1L));
        assertThat(f.retention().receivedKeys).isEmpty();
    }

    @Test
    @DisplayName("correção A7-iv: certificado do assinante de OUTRO emitente é recusado por ISSUER_MISMATCH")
    void assinanteDoCertificadoNaoEhOEmitente() throws Exception {
        KeyPair keys = keys();
        java.security.cert.X509Certificate certOutro = SinteticoXmlSigner.selfSignedTestCertificate(
                keys, "Outro Prestador Sintetico de Teste:" + OTHER_ISSUER);
        byte[] signed = SinteticoXmlSigner.signWithCertificate(
                xmlAutorizado(KEY_A, ISSUER, true, "100"), keys, certOutro);
        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(
                command("lote-1", List.of(entry("assinante-outro.xml", signed))));
        assertThat(report.totalAccepted()).isZero();
        assertThat(report.rejectedByReason())
                .containsExactlyEntriesOf(Map.of("ISSUER_MISMATCH", 1L));
        assertThat(f.retention().receivedKeys).isEmpty();
    }

    @Test
    @DisplayName("correção A12-a: KeyInfo com certificado e KeyValue de chaves DIFERENTES é recusado como ambíguo")
    void keyInfoAmbiguoRecusado() throws Exception {
        KeyPair keys = keys();
        KeyPair outras = keys();
        java.security.cert.X509Certificate certificate = SinteticoXmlSigner.selfSignedTestCertificate(
                keys, "Prestador Sintetico de Teste:" + ISSUER);
        byte[] signed = SinteticoXmlSigner.signWithCertificateAndForeignKeyValue(
                xmlAutorizado(KEY_A, ISSUER, true, "100"), keys, certificate, outras.getPublic());
        assertThat(new JdkXmlSignatureValidator().validate(signed).valida()).isFalse();
    }

    @Test
    @DisplayName("correção A8: chNFe do protocolo divergente da chave do Id do documento é recusada por ACCESS_KEY_INVALID")
    void chaveDoProtocoloDivergeDaChaveDoId() throws Exception {
        KeyPair keys = keys();
        String comChaveDivergente = xmlAutorizado(KEY_A, ISSUER, true, "100")
                .replace("<nProt>", "<chNFe>" + KEY_B + "</chNFe><nProt>");
        byte[] signed = SinteticoXmlSigner.signWithKeyValue(comChaveDivergente, keys);
        Fixture f = fixture();
        LegacyXmlImportService.BatchReport report = f.service().importar(
                command("lote-1", List.of(entry("chave-divergente.xml", signed))));
        assertThat(report.totalAccepted()).isZero();
        assertThat(report.rejectedByReason())
                .containsExactlyEntriesOf(Map.of("ACCESS_KEY_INVALID", 1L));
        assertThat(f.retention().receivedKeys).isEmpty();
    }
}

package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verificacao publica por codigo/QR: valida, inconsistente, inexistente, limites e mascara. */
class SignatureVerificationTest {

    private DocumentSigner.SignatureRecord signedRecord(SigningTestSupport.MapStore store) {
        var params = new SigningTestSupport.MapParameters()
                .withMinLevel(SignableDocumentType.ADHESION_CONTRACT, "SIMPLE");
        var simple = new SimpleDocumentSigner(
                (t, u, c, v, h) -> Optional.of(SigningTestSupport.acceptance(h)),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
        var service = SigningTestSupport.service(params, List.of(simple), store);
        return service.sign(new SignatureService.SignCommand(
                SignatureLevel.SIMPLE, SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT)));
    }

    @Test
    @DisplayName("verificacao: registro integro valida e a tentativa fica registrada")
    void validRecordVerifies() {
        var store = new SigningTestSupport.MapStore();
        DocumentSigner.SignatureRecord record = signedRecord(store);
        var service = SigningTestSupport.service(new SigningTestSupport.MapParameters(), List.of(), store);
        assertThat(service.verify(record.verificationCode(), "203.0.113.99"))
                .isEqualTo(SignatureVerification.Outcome.VALID);
        assertThat(store.attempts).containsEntry(record.verificationCode(), SignatureVerification.Outcome.VALID);
    }

    @Test
    @DisplayName("verificacao: hash adulterado no registro vira RECORD_TAMPERED; codigo desconhecido, NOT_FOUND")
    void tamperedAndUnknown() {
        var store = new SigningTestSupport.MapStore();
        DocumentSigner.SignatureRecord record = signedRecord(store);
        var tampered = new DocumentSigner.SignatureRecord(record.id(), record.tenantId(), record.documentType(),
                record.documentReference(), record.level(), SignatureHashes.sha256Hex("texto trocado"),
                record.signerSubject(), record.signedAt(), record.verificationCode(), record.verificationDigest(),
                null, null, null, null, null, record.evidenceJson());
        store.byCode.put(record.verificationCode(), tampered);
        assertThat(SignatureVerification.check(tampered)).isEqualTo(SignatureVerification.Outcome.RECORD_TAMPERED);
        assertThat(SignatureVerification.check(null)).isEqualTo(SignatureVerification.Outcome.NOT_FOUND);
    }

    @Test
    @DisplayName("QR: carga aponta para a pagina de validacao do Ziip; JSON publico traz o link do VALIDAR do ITI")
    void qrPayloadAndPublicJson() {
        var store = new SigningTestSupport.MapStore();
        DocumentSigner.SignatureRecord record = signedRecord(store);
        assertThat(SignatureVerification.qrPayload("https://portal.exemplo.invalid/", record.verificationCode()))
                .isEqualTo("https://portal.exemplo.invalid/validar-assinatura/" + record.verificationCode());
        var json = Json.readTree(SignatureVerification.publicJson(record, SignatureVerification.Outcome.VALID));
        assertThat(json.get("itiValidarUrl").asText()).isEqualTo("https://validar.iti.gov.br/");
        assertThat(json.get("level").asText()).isEqualTo("SIMPLE");
        assertThat(json.has("evidenceJson")).isFalse();
    }

    @Test
    @DisplayName("checksum (D1, limitacao provada): quem recomputa o digest com dados publicos obtem VALID — e checksum, nao protecao")
    void checksumIsRecomputableByDesign() {
        // Este teste DOCUMENTA o limite do mecanismo (correcao D1): o digest e
        // SHA-256 sem segredo sobre dados publicos. A sonda do revisor fez
        // exatamente esta troca e obteve VALID; a alegacao "anti-adulteracao"
        // foi retirada do codigo e do relatorio, e a protecao real (segredo
        // fora da base) e pendencia nomeada. Se um dia houver segredo, ESTE
        // teste tem de passar a esperar RECORD_TAMPERED.
        var store = new SigningTestSupport.MapStore();
        DocumentSigner.SignatureRecord record = signedRecord(store);
        String forgedHash = SignatureHashes.sha256Hex("texto trocado pelo atacante");
        var forged = new DocumentSigner.SignatureRecord(record.id(), record.tenantId(), record.documentType(),
                record.documentReference(), record.level(), forgedHash,
                record.signerSubject(), record.signedAt(), record.verificationCode(),
                SignatureVerification.digestOf(forgedHash, record.verificationCode(), record.signedAt()),
                null, null, null, null, null, record.evidenceJson());
        assertThat(SignatureVerification.check(forged)).isEqualTo(SignatureVerification.Outcome.VALID);
    }

    @Test
    @DisplayName("instante (D2): nanos nao redondos sao truncados para micros na fonte e o registro sobrevive ao round-trip do timestamptz")
    void nanosTruncatedAndRoundTripStaysValid() {
        Instant nanoInstant = Instant.parse("2026-11-07T12:00:00.123456789Z");
        // O digest e o mesmo para o instante com nanos e para a sua forma em micros (a que o banco guarda).
        assertThat(SignatureVerification.digestOf("h", "c", nanoInstant))
                .isEqualTo(SignatureVerification.digestOf("h", "c", Instant.parse("2026-11-07T12:00:00.123456Z")));
        var nanoClock = Clock.fixed(nanoInstant, ZoneOffset.UTC);
        var signer = new SimpleDocumentSigner(
                (t, u, c, v, h) -> Optional.of(SigningTestSupport.acceptance(h)),
                nanoClock, SigningTestSupport.ids(), SigningTestSupport.codes());
        DocumentSigner.SignatureRecord record = signer.sign(
                SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT));
        assertThat(record.signedAt()).isEqualTo(Instant.parse("2026-11-07T12:00:00.123456Z"));
        // Round-trip simulado: o timestamptz devolve o instante em micros (texto -> Instant).
        Instant reread = Instant.parse(record.signedAt().toString());
        var afterRoundTrip = new DocumentSigner.SignatureRecord(record.id(), record.tenantId(),
                record.documentType(), record.documentReference(), record.level(), record.contentHash(),
                record.signerSubject(), reread, record.verificationCode(), record.verificationDigest(),
                null, null, null, null, null, record.evidenceJson());
        assertThat(SignatureVerification.check(afterRoundTrip)).isEqualTo(SignatureVerification.Outcome.VALID);
    }

    @Test
    @DisplayName("limite (D7): tentativas por codigo e por origem estouram em 429 e a tentativa fica registrada como RATE_LIMITED")
    void verificationRateLimited() {
        var store = new SigningTestSupport.MapStore();
        DocumentSigner.SignatureRecord record = signedRecord(store);
        var byCode = SigningTestSupport.service(new SigningTestSupport.MapParameters(), List.of(), store,
                new SignatureService.VerificationLimits(2, 30, 15));
        assertThat(byCode.verify(record.verificationCode(), "203.0.113.1"))
                .isEqualTo(SignatureVerification.Outcome.VALID);
        assertThat(byCode.verify(record.verificationCode(), "203.0.113.2"))
                .isEqualTo(SignatureVerification.Outcome.VALID);
        assertThatThrownBy(() -> byCode.verify(record.verificationCode(), "203.0.113.3"))
                .isInstanceOf(ApiException.TooManyRequests.class);
        assertThat(store.attemptLog)
                .anyMatch(a -> a.outcome() == SignatureVerification.Outcome.RATE_LIMITED);

        var store2 = new SigningTestSupport.MapStore();
        DocumentSigner.SignatureRecord record2 = signedRecord(store2);
        var byOrigin = SigningTestSupport.service(new SigningTestSupport.MapParameters(), List.of(), store2,
                new SignatureService.VerificationLimits(10, 1, 15));
        assertThat(byOrigin.verify(record2.verificationCode(), "198.51.100.7"))
                .isEqualTo(SignatureVerification.Outcome.VALID);
        assertThatThrownBy(() -> byOrigin.verify("0000-0000-0000-9999", "198.51.100.7"))
                .isInstanceOf(ApiException.TooManyRequests.class);
    }

    @Test
    @DisplayName("JSON publico (D10): signatario sai mascarado; em RECORD_TAMPERED nenhum campo do registro e exposto")
    void publicJsonMasksSignerAndHidesTamperedRecord() {
        var store = new SigningTestSupport.MapStore();
        DocumentSigner.SignatureRecord record = signedRecord(store);
        var valid = Json.readTree(SignatureVerification.publicJson(record, SignatureVerification.Outcome.VALID));
        assertThat(valid.has("signerSubject")).isFalse();
        assertThat(valid.get("signerSubjectMasked").asText())
                .isEqualTo(SignatureVerification.maskSignerSubject(record.signerSubject()));
        assertThat(valid.get("signerSubjectMasked").asText()).doesNotContain(record.signerSubject());
        var tampered = Json.readTree(
                SignatureVerification.publicJson(record, SignatureVerification.Outcome.RECORD_TAMPERED));
        assertThat(tampered.get("outcome").asText()).isEqualTo("RECORD_TAMPERED");
        assertThat(tampered.has("signerSubjectMasked")).isFalse();
        assertThat(tampered.has("contentHash")).isFalse();
        assertThat(tampered.has("signedAt")).isFalse();
        assertThat(tampered.has("level")).isFalse();
    }
}

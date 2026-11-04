package br.com.ziipsan.commercial.fiscal.application;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate.Environment;
import br.com.ziipsan.commercial.fiscal.port.AuthorizedXmlStore;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer.AuthorizationResult;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer.FiscalDocumentType;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer.SignedDocument;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.DocumentRecord;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.EventRecord;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.ReservedSlot;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.TaxLine;
import br.com.ziipsan.commercial.fiscal.port.RestrictedProductionUnavailableException;
import br.com.ziipsan.commercial.fiscal.rules.DpsId;
import br.com.ziipsan.commercial.fiscal.rules.TaxEngine;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity.TaxRateRow;
import br.com.ziipsan.commercial.fiscal.xml.DpsXmlBuilder;
import br.com.ziipsan.commercial.fiscal.xml.DpsXmlBuilder.DpsInput;
import br.com.ziipsan.commercial.fiscal.xml.NfseEventXmlBuilder;
import br.com.ziipsan.commercial.fiscal.xml.NfseEventXmlBuilder.EventInput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Emissão, cancelamento e substituição de NFS-e Nacional (P1.8) sobre o porte Java do
 * motor fiscal (ADR-0003; FISCAL-PORTE-JAVA §3.3, §3.4, §3.5).
 *
 * <p>Ordem de cada emissão, sem atalho: trava de produção → recusa fechada do stub da
 * produção restrita (antes de qualquer escrita) → alíquotas por vigência (ausente = erro,
 * documento não emitido) → reserva atômica do número com o documento em RESERVED → DPS
 * persistida antes do envio → autorização → XML autorizado guardado. Timeout ou 5xx nunca
 * retransmite sem consultar antes ({@link #retransmit(UUID, UUID)} consulta primeiro).
 *
 * <p>Limites desta frente, sem maquiagem: (1) só o simulador autoriza de verdade; HOMOLOGATION
 * e PRODUCTION caem no stub e falham fechados — a prova na produção restrita, com o e-CNPJ
 * da Ziip/TGP, é pendência registrada; (2) NFAg-e é recusada aqui (leiaute na P2.6, V4_7):
 * as tabelas e a numeração já a suportam, o builder ainda não; (3) a assinatura XMLDSig e o
 * mTLS chegam com o cliente real (P2) — o XML aqui é o não assinado do corpus.
 *
 * <p>IBS/CBS entram como informativos só na competência do ano de teste de 2026
 * ({@link TaxRateValidity#cbsIbsInformative}); a partir de 01/01/2027 a CBS plena deixa
 * de ser informativa e o IBS segue a sua transição por vigência (P2.4).
 */
public final class FiscalIssuanceService {

    /**
     * Código do evento de cancelamento da NFS-e no Id do evento (porte §1: tipo de 6
     * posições). O nome do evento no porte é "e101101"; o Id leva só o código numérico
     * (PRE + chave de 50 + 101101 = 59 posições), como o {@link DpsId} já validava.
     */
    public static final String CANCEL_EVENT_TYPE = "101101";

    /** Sufixo do simulador para compor a chave de acesso sintética (42 do Id da DPS + 8 = 50). */
    static final String SIMULATED_KEY_SUFFIX = "SIMULADO";

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final FiscalDocumentRepository repository;
    private final FiscalIssuerRouter router;
    private final AuthorizedXmlStore xmlStore;
    private final Clock clock;

    public FiscalIssuanceService(FiscalDocumentRepository repository, FiscalIssuerRouter router,
            AuthorizedXmlStore xmlStore, Clock clock) {
        this.repository = repository;
        this.router = router;
        this.xmlStore = xmlStore;
        this.clock = clock;
    }

    /** Entrada de uma emissão. A inscrição é transitória: valida o Id da DPS e o XML, e não é gravada em campo próprio. */
    public record IssueRequest(
            UUID tenantId, UUID issuerId, String inscription, char inscriptionType,
            FiscalDocumentType documentType, String series, String sourceType, UUID sourceId,
            BigDecimal gross, LocalDate competenceDate, Environment environment,
            boolean tenantApproved, String takerName, boolean withIbsCbs) {
    }

    public record IssueResult(UUID documentId, long number, String dpsId, String status, String protocol) {
    }

    public record SubstituteResult(UUID originalId, UUID replacementId, String replacementDpsId) {
    }

    /** Emite uma NFS-e Nacional no simulador. Fora dele, falha fechada antes de escrever. */
    public IssueResult issue(IssueRequest request) {
        if (request.documentType() != FiscalDocumentType.NFSE_NACIONAL) {
            throw new IllegalStateException("NFAg-e ainda sem builder nesta frente (leiaute na P2.6, V4_7)");
        }
        FiscalDocumentIssuer issuer = router.issuerFor(request.environment(), request.tenantApproved());
        if (router.isRestricted(request.environment())) {
            // Fail-closed: o stub recusaria de qualquer forma, mas recusar aqui não consome
            // número nem grava documento — e o contador do stub prova que nada foi tentado.
            throw new RestrictedProductionUnavailableException();
        }
        var issuerRecord = repository.findIssuer(request.tenantId(), request.issuerId())
                .orElseThrow(() -> new IllegalStateException("Emitente inexistente: " + request.issuerId()));
        List<TaxRateRow> rates = repository.taxRates(request.tenantId());
        TaxEngine.TaxRates taxRates = new TaxEngine.TaxRates(
                TaxRateValidity.rateFor(rates, "ISS", request.competenceDate()),
                TaxRateValidity.rateFor(rates, "IRRF", request.competenceDate()),
                TaxRateValidity.rateFor(rates, "CSRF", request.competenceDate()),
                TaxRateValidity.rateFor(rates, "CBS", request.competenceDate()),
                TaxRateValidity.rateFor(rates, "IBS", request.competenceDate()));
        TaxEngine.TaxResult result = TaxEngine.calculate(request.gross(), taxRates);

        ReservedSlot slot = repository.reserveSlot(request.tenantId(), request.issuerId(),
                request.documentType().name(), request.series(), request.sourceType(), request.sourceId(),
                request.environment().name(), request.environment().tpAmb(), request.gross());
        String numberField = String.format("%015d", slot.number());
        String dpsId = request.inscriptionType() == DpsId.TYPE_CNPJ
                ? DpsId.ofCnpj(issuerRecord.ibge(), request.inscription(), request.series(), numberField)
                : DpsId.ofCpf(issuerRecord.ibge(), request.inscription(), request.series(), numberField);
        String unsignedXml = DpsXmlBuilder.build(new DpsInput(dpsId, nowIso(), request.series(),
                slot.number(), issuerRecord.ibge(), request.inscription(), request.takerName(),
                request.gross(), result, request.withIbsCbs()));
        List<TaxLine> taxes = List.of(
                new TaxLine("ISS", taxRates.issRate(), result.iss(), false),
                new TaxLine("IRRF", taxRates.irrfRate(), result.irrf(), false),
                new TaxLine("CSRF", taxRates.csrfRate(), result.csrf(), false),
                new TaxLine("CBS", taxRates.cbsRate(), result.cbsInformative(),
                        TaxRateValidity.cbsIbsInformative(request.competenceDate())),
                new TaxLine("IBS", taxRates.ibsRate(), result.ibsInformative(),
                        TaxRateValidity.cbsIbsInformative(request.competenceDate())));
        repository.signDocument(request.tenantId(), slot.documentId(), dpsId, unsignedXml, taxes);
        try {
            AuthorizationResult authorization = issuer.authorize(
                    new SignedDocument(dpsId, unsignedXml, request.environment()));
            return authorize(request.tenantId(), slot.documentId(), dpsId, unsignedXml, authorization);
        } catch (RuntimeException failure) {
            repository.markPendingReconciliation(request.tenantId(), slot.documentId());
            throw failure;
        }
    }

    /**
     * Reconciliação pós-timeout (porte §3.3): consulta antes de qualquer retransmissão. Se o
     * autorizador já tem o documento, ele passa a AUTHORIZED sem segunda autorização.
     */
    public Optional<String> reconcile(UUID tenantId, UUID documentId) {
        DocumentRecord document = requiredDocument(tenantId, documentId);
        if (!document.status().equals("PENDING_RECONCILIATION") && !document.status().equals("TRANSMITTED")) {
            throw new IllegalStateException("So documento PENDING_RECONCILIATION ou TRANSMITTED reconcilia: " + document.status());
        }
        FiscalDocumentIssuer issuer = router.issuerFor(
                Environment.valueOf(document.environment()), true);
        Optional<AuthorizationResult> found = issuer.query(document.dpsId());
        if (found.isPresent()) {
            authorize(tenantId, documentId, document.dpsId(), document.unsignedXml(), found.get());
            return Optional.of(found.get().protocol());
        }
        return Optional.empty();
    }

    /**
     * Retransmissão: consulta primeiro; só autoriza de novo se a consulta voltar vazia.
     * Nunca autoriza duas vezes o mesmo Id da DPS sem consultar.
     */
    public IssueResult retransmit(UUID tenantId, UUID documentId) {
        DocumentRecord document = requiredDocument(tenantId, documentId);
        if (!document.status().equals("PENDING_RECONCILIATION")) {
            throw new IllegalStateException("So documento PENDING_RECONCILIATION retransmite: " + document.status());
        }
        Optional<String> reconciled = reconcile(tenantId, documentId);
        if (reconciled.isPresent()) {
            DocumentRecord updated = requiredDocument(tenantId, documentId);
            return new IssueResult(updated.id(), updated.number(), updated.dpsId(), updated.status(), updated.protocol());
        }
        FiscalDocumentIssuer issuer = router.issuerFor(
                Environment.valueOf(document.environment()), true);
        AuthorizationResult authorization = issuer.authorize(
                new SignedDocument(document.dpsId(), document.unsignedXml(), Environment.valueOf(document.environment())));
        return authorize(tenantId, documentId, document.dpsId(), document.unsignedXml(), authorization);
    }

    /**
     * Cancela um documento autorizado (evento e101101). Cancelar de novo o já cancelado
     * devolve o evento existente, sem registrar segundo evento (cancelamento idempotente;
     * o autorizador trata E0840 como "já cancelada", porte §1).
     */
    /** Cancelamento simples (um documento, um aprovador). */
    public EventRecord cancel(UUID tenantId, UUID documentId, String reason, UUID requestedBy) {
        return cancel(tenantId, documentId, reason, requestedBy, null);
    }

    /** Cancelamento com segundo aprovador (obrigatório no cancelamento em lote). */
    public EventRecord cancel(UUID tenantId, UUID documentId, String reason, UUID requestedBy, UUID approvedBySecond) {
        DocumentRecord document = requiredDocument(tenantId, documentId);
        if (document.status().equals("CANCELLED")) {
            return repository.findEventByDocumentAndKind(tenantId, documentId, "CANCELLATION")
                    .orElseThrow(() -> new IllegalStateException("Documento CANCELLED sem evento registrado"));
        }
        if (!document.status().equals("AUTHORIZED")) {
            throw new IllegalStateException("So documento AUTHORIZED cancela: " + document.status());
        }
        if (document.accessKey() == null) {
            throw new IllegalStateException("Documento sem chave de acesso nao cancela");
        }
        Environment environment = Environment.valueOf(document.environment());
        if (router.isRestricted(environment)) {
            throw new RestrictedProductionUnavailableException();
        }
        FiscalDocumentIssuer issuer = router.issuerFor(environment, true);
        String eventId = DpsId.eventId(document.accessKey(), CANCEL_EVENT_TYPE);
        String eventXml = NfseEventXmlBuilder.build(
                new EventInput(document.accessKey(), CANCEL_EVENT_TYPE, nowIso(), reason));
        // O XML do evento e construido e validado (Id + escape) antes do registro; o registro
        // no autorizador e o que o torna efetivo. O XML assinado do evento segue o mesmo
        // caminho do assinador da P2; aqui o simulador aceita o evento pelo Id.
        FiscalDocumentIssuer.EventResult registered = issuer.registerEvent(eventId);
        EventRecord event = new EventRecord(UUID.randomUUID(), documentId, eventId,
                CANCEL_EVENT_TYPE, "CANCELLATION",
                registered.accepted() ? "ACCEPTED" : "REJECTED", reason, requestedBy, requestedBy, approvedBySecond);
        repository.registerEvent(tenantId, event);
        if (!registered.accepted()) {
            throw new IllegalStateException("Autorizador recusou o cancelamento do evento " + eventId);
        }
        repository.markCancelled(tenantId, documentId);
        return event;
    }

    /** Cancelamento em lote: exige o segundo aprovador (dois papéis distintos, porte §3.5). */
    public List<EventRecord> cancelBatch(UUID tenantId, List<UUID> documentIds, String reason,
            UUID requestedBy, UUID approvedBySecond) {
        if (approvedBySecond == null) {
            throw new IllegalArgumentException("Cancelamento em lote exige o segundo aprovador");
        }
        return documentIds.stream()
                .map(id -> cancel(tenantId, id, reason, requestedBy, approvedBySecond))
                .toList();
    }

    /**
     * Substituição: cancela o original e emite o substituto; o original passa a REPLACED
     * apontando para o substituto. Se a emissão do substituto falhar, o original fica
     * CANCELLED (um cancelamento registrado nunca é desfeito em silêncio) e o erro propaga.
     */
    public SubstituteResult substitute(UUID tenantId, UUID originalId, IssueRequest replacement, String reason, UUID requestedBy) {
        DocumentRecord original = requiredDocument(tenantId, originalId);
        if (!original.issuerId().equals(replacement.issuerId())) {
            throw new IllegalArgumentException("O substituto deve ser do mesmo emitente do original");
        }
        cancel(tenantId, originalId, reason, requestedBy);
        IssueResult issued = issue(replacement);
        repository.markReplaced(tenantId, originalId, issued.documentId());
        return new SubstituteResult(originalId, issued.documentId(), issued.dpsId());
    }

    /**
     * Fecha a autorização no caminho do simulador: a chave de acesso sintética (Id da DPS +
     * "SIMULADO" = 50 posições) só existe aqui porque o simulador não devolve chave real.
     * O cliente real (P2) substitui este método: a chave vem do autorizador, nunca sintetizada.
     */
    private IssueResult authorize(UUID tenantId, UUID documentId, String dpsId, String unsignedXml,
            AuthorizationResult authorization) {
        if (!authorization.authorized()) {
            repository.markRejected(tenantId, documentId, "NOT_AUTHORIZED", "Autorizador nao autorizou");
            throw new IllegalStateException("Autorizador nao autorizou o documento " + dpsId);
        }
        String accessKey = dpsId + SIMULATED_KEY_SUFFIX;
        if (accessKey.length() != 50) {
            throw new IllegalStateException("Chave de acesso sintetica do simulador fora do tamanho de 50");
        }
        repository.markAuthorized(tenantId, documentId, authorization.protocol(), accessKey);
        byte[] xmlBytes = unsignedXml.getBytes(StandardCharsets.UTF_8);
        xmlStore.store(tenantId, documentId, gzip(xmlBytes), sha256Hex(xmlBytes), "SIMULATOR");
        DocumentRecord updated = requiredDocument(tenantId, documentId);
        return new IssueResult(updated.id(), updated.number(), updated.dpsId(), updated.status(), updated.protocol());
    }

    private DocumentRecord requiredDocument(UUID tenantId, UUID documentId) {
        return repository.findDocument(tenantId, documentId)
                .orElseThrow(() -> new IllegalStateException("Documento inexistente: " + documentId));
    }

    private String nowIso() {
        return clock.instant().atZone(SAO_PAULO).toOffsetDateTime().toString();
    }

    /** Gzip do XML, como o transporte do porte (gzip+Base64 em UTF-8, porte §1). */
    public static byte[] gzip(byte[] bytes) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

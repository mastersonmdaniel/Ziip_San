package br.com.ziipsan.commercial.collection.legacydebit;

import br.com.ziipsan.commercial.collection.parameters.LegacyDebitParameters;
import br.com.ziipsan.commercial.collection.pixauto.AccountDebitAuthorizationGate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * FEBRABAN 150 legado (P3.24) sobre autorizacoes IMPORTADAS, e a campanha
 * de conversao dos optantes ao Pix Automatico (P4.5).
 *
 * <p>Condicoes de contorno (aceite + decisao do dono de 08/11/2026, item 9):
 * <ul>
 *   <li>Optantes EXISTEM (palavra do dono) e o caminho e DUPLO: conversao
 *       ao Pix Automatico E legado mantido para a base atual. O parecer
 *       formal RAS-06/PD-10 nao existe como documento - ver relatorio.</li>
 *   <li>So entra optante de mesmo banco (pagador = recebedor), condicao
 *       por optante da P0.30, aplicada na conferencia por matricula.</li>
 *   <li>Autorizacao NOVA e recusada aqui: so Pix Automatico (P4.5).</li>
 *   <li>Tudo e portao de {@code pay.legacy_debit_enabled} (classe d) e da
 *       data de desligamento DEDUZIDA em {@link BcbDebitRegimeRules} -
 *       nunca digitada. ED-27 e P4.5 seguem a mesma data parametrizada.</li>
 *   <li>Conferencia no paralelo local GL.2 com arquivos de teste, sem
 *       envio ao banco: a saida fisica e a porta {@link Febraban150Port}.</li>
 * </ul>
 *
 * <p>Baixa do retorno e IDEMPOTENTE pela chave do debito: retorno repetido
 * nao baixa de novo, valor divergente nao baixa, debito desconhecido nao
 * e identificado - mesmo desenho da conciliacao da P4.5.
 */
public final class LegacyDebitService {

    public enum ReturnOutcome { BAIXADO, REPETIDO_SEM_EFEITO, DIVERGENTE, NAO_IDENTIFICADO }

    public record ReturnResult(ReturnOutcome outcome, UUID debitId) {
    }

    public enum DebitStatus { REMETIDO, BAIXADO, CANCELADO }

    /** Debito legado de um ciclo, chave da baixa idempotente. */
    public static final class LegacyDebit {
        private final UUID id;
        private final UUID tenantId;
        private final UUID authorizationId;
        private final String enrollmentId;
        private final BigDecimal amount;
        private final LocalDate debitDate;
        /** Chave de deduplicacao da remessa (B2-4): identifica o debito do ciclo. */
        private final String remittanceKey;
        private DebitStatus status = DebitStatus.REMETIDO;
        private int remittanceCount = 1;
        private String outcomeCode;
        private Instant settledAt;

        LegacyDebit(UUID id, UUID tenantId, UUID authorizationId, String enrollmentId,
                    BigDecimal amount, LocalDate debitDate) {
            this.id = id;
            this.tenantId = tenantId;
            this.authorizationId = authorizationId;
            this.enrollmentId = enrollmentId;
            this.amount = amount;
            this.debitDate = debitDate;
            this.remittanceKey = LegacyDebitService.remittanceKey(tenantId, enrollmentId, amount, debitDate);
        }

        public UUID id() { return id; }
        public UUID tenantId() { return tenantId; }
        public UUID authorizationId() { return authorizationId; }
        public String enrollmentId() { return enrollmentId; }
        public BigDecimal amount() { return amount; }
        public LocalDate debitDate() { return debitDate; }
        /** Chave de deduplicacao da remessa (B2-4). */
        public String remittanceKey() { return remittanceKey; }
        public DebitStatus status() { return status; }
        public int remittanceCount() { return remittanceCount; }
        /** Codigo de ocorrencia do retorno guardado VERBATIM (correcao D1 da revisao). */
        public String outcomeCode() { return outcomeCode; }
        public Instant settledAt() { return settledAt; }
    }

    /** Porta de persistencia (adaptador JPA na fiacao, sobre as entidades desta entrega). */
    public interface Store {
        void saveAuthorization(LegacyDebitAuthorization authorization);
        Optional<LegacyDebitAuthorization> findAuthorization(UUID tenantId, UUID authorizationId);
        Optional<LegacyDebitAuthorization> findAuthorizationByEnrollment(UUID tenantId, String enrollmentId);
        List<LegacyDebitAuthorization> findAuthorizations(UUID tenantId);
        void saveDebit(LegacyDebit debit);
        Optional<LegacyDebit> findDebit(UUID tenantId, UUID debitId);
        /** Busca o debito do ciclo pela chave de deduplicacao da remessa (B2-4). */
        Optional<LegacyDebit> findDebitByRemittanceKey(UUID tenantId, String remittanceKey);
        void saveConsent(LegacyConversionConsent consent);
        List<LegacyConversionConsent> findConsents(UUID tenantId, UUID authorizationId);
    }

    /**
     * Chave deterministica de deduplicacao da remessa (B2-4): um debito e o
     * mesmo quando repete tenant + matricula + valor + data do ciclo. O
     * valor e normalizado ({@code stripTrailingZeros}) para "100.00" e
     * "100.0" nao virarem debitos distintos.
     */
    public static String remittanceKey(UUID tenantId, String enrollmentId, BigDecimal amount,
                                       LocalDate debitDate) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(enrollmentId, "matricula e obrigatoria");
        Objects.requireNonNull(amount, "valor e obrigatorio");
        Objects.requireNonNull(debitDate, "data do debito e obrigatoria");
        return tenantId + "|" + enrollmentId + "|"
                + amount.stripTrailingZeros().toPlainString() + "|" + debitDate;
    }

    private final Store store;
    private final Febraban150Port filePort;
    private final LegacyDebitParameters parameters;
    private final BcbDebitRegimeRules.Framing framing;

    public LegacyDebitService(Store store, Febraban150Port filePort,
                              LegacyDebitParameters parameters,
                              BcbDebitRegimeRules.Framing framing) {
        this.store = Objects.requireNonNull(store, "Store e obrigatoria");
        this.filePort = Objects.requireNonNull(filePort, "Febraban150Port e obrigatoria");
        this.parameters = Objects.requireNonNull(parameters,
                "parametros (pay.legacy_debit_enabled) sao obrigatorios");
        this.framing = Objects.requireNonNull(framing,
                "enquadramento do tenant e obrigatorio (deduz a data de desligamento)");
    }

    /** Data de desligamento deste tenant, deduzida da regra do BCB. */
    public LocalDate shutdownDate() {
        return BcbDebitRegimeRules.deducedShutdownDate(framing);
    }

    private void requireLegacyUsable(LocalDate today) {
        if (!parameters.legacyDebitEnabled()) {
            throw new IllegalStateException(
                    "pay.legacy_debit_enabled desligado: operacao do FEBRABAN 150 legado recusada");
        }
        if (BcbDebitRegimeRules.isShutdownOn(framing, today)) {
            throw new IllegalStateException("FEBRABAN 150 legado desligado desde "
                    + shutdownDate() + " (" + BcbDebitRegimeRules.ruleReference(framing)
                    + "): so Pix Automatico (P4.5)");
        }
    }

    // ------------------------------------------------------------------
    // Autorizacao nova: recusada (so Pix Automatico, na P4.5)
    // ------------------------------------------------------------------

    public void requestNewAuthorization() {
        AccountDebitAuthorizationGate.requirePixAutomaticoForNewAuthorization(
                AccountDebitAuthorizationGate.DebitChannel.FEBRABAN_150_LEGADO);
    }

    // ------------------------------------------------------------------
    // Importacao e conferencia por matricula
    // ------------------------------------------------------------------

    /** Importa UMA autorizacao legada (nasce nao conferida). Matricula duplicada e recusada. */
    public LegacyDebitAuthorization importLegacyAuthorization(
            UUID tenantId, String enrollmentId, UUID unitId, String payerDocument,
            String payerBankCode, String receiverBankCode, String legacyReference,
            Instant importedAt, LocalDate today) {
        requireLegacyUsable(today);
        if (store.findAuthorizationByEnrollment(tenantId, enrollmentId).isPresent()) {
            throw new IllegalStateException(
                    "matricula ja importada para o tenant (conferencia e por matricula): " + enrollmentId);
        }
        LegacyDebitAuthorization auth = new LegacyDebitAuthorization(UUID.randomUUID(), tenantId,
                enrollmentId, unitId, payerDocument, payerBankCode, receiverBankCode,
                legacyReference, importedAt);
        store.saveAuthorization(auth);
        return auth;
    }

    /** Confere a importacao pela matricula e aplica a condicao de mesmo banco. */
    public LegacyDebitAuthorization conferImported(UUID tenantId, String enrollmentId, Instant at) {
        LegacyDebitAuthorization auth = store.findAuthorizationByEnrollment(tenantId, enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "matricula nao importada para o tenant: " + enrollmentId));
        auth.confer(enrollmentId, at);
        store.saveAuthorization(auth);
        return auth;
    }

    // ------------------------------------------------------------------
    // Remessa (B, C, E, F), reenvio e cancelamento de optante
    // ------------------------------------------------------------------

    /**
     * Gera a remessa de um ciclo: B de cada optante ativo informado em
     * {@code inclusionsPending}, E de cada debito novo, C de cada optante
     * efetivamente CANCELADA informado em {@code cancellationsPending} e F
     * de fechamento. Retorna os debitos criados (um por cobranca).
     *
     * <p>NAO ha estado de inclusao neste servico (correcao D4 da revisao):
     * o B e emitido a cada chamada para cada matricula presente em
     * {@code inclusionsPending} - se a mesma matricula for informada em
     * duas chamadas, o B sai nas duas. A deduplicacao da lista de
     * pendentes e de quem chama, que monta as listas por ciclo (mesmo
     * desenho de {@code drafts} e {@code cancellationsPending}).
     *
     * <p>IDEMPOTENCIA POR DEBITO (B2-4): a reexecucao com os MESMOS drafts
     * nao cria debitos duplicados - o debito ja existente para a chave
     * {@link #remittanceKey} e reutilizado e nenhum registro E novo e
     * escrito para ele; o trailer F soma so os debitos EFETIVAMENTE
     * criados nesta chamada (nunca os reutilizados). Draft com valor ou
     * data distintos e outro debito (outra chave) e cria normalmente. A
     * emissao de B/C a cada chamada (D4) nao muda: a dedup cobre os
     * debitos E, nao a lista de pendentes, que continua sendo do chamador.
     * A UNIQUE de banco sobre a chave exige migration nova e segue
     * ADIADA de proposito: a renumeracao R2-G1 (decisao do dono de
     * 08/11/2026) levou a migration do legado de V6_4 para V102 SEM a
     * UNIQUE - a chave e derivada em aplicacao e nao tem coluna
     * propria na tabela, logo a forma exata da UNIQUE no banco nao
     * esta provada no codigo; fica pendente de desenho proprio
     * (V103 seguinte, se aprovada).
     */
    public List<LegacyDebit> generateRemittance(UUID tenantId, String receiverBankCode,
                                                  List<DebitDraft> drafts,
                                                  List<String> inclusionsPending,
                                                  List<String> cancellationsPending,
                                                  LocalDate today) {
        requireLegacyUsable(today);
        LegacyDebitAuthorization.requireBankCode(receiverBankCode, "banco do recebedor");
        List<Febraban150Port.RemittanceRecord> records = new ArrayList<>();
        int seq = 0;
        for (String enrollmentId : inclusionsPending) {
            LegacyDebitAuthorization auth = requireActiveLegacy(tenantId, enrollmentId);
            if (!auth.receiverBankCode().equals(receiverBankCode)) {
                throw new IllegalStateException("banco do recebedor da remessa diverge do optante "
                        + enrollmentId);
            }
            records.add(new Febraban150Port.RemittanceRecord(
                    Febraban150Port.RecordType.B, enrollmentId, null, null, null, ++seq));
        }
        List<LegacyDebit> created = new ArrayList<>();
        List<LegacyDebit> newlyCreated = new ArrayList<>();
        for (DebitDraft draft : drafts) {
            LegacyDebitAuthorization auth = requireActiveLegacy(tenantId, draft.enrollmentId());
            if (!auth.receiverBankCode().equals(receiverBankCode)) {
                throw new IllegalStateException("banco do recebedor da remessa diverge do optante "
                        + draft.enrollmentId());
            }
            // B2-4: debito do ciclo ja gerado - reutiliza, sem duplicar e sem novo registro E.
            Optional<LegacyDebit> existing = store.findDebitByRemittanceKey(tenantId,
                    remittanceKey(tenantId, draft.enrollmentId(), draft.amount(), draft.debitDate()));
            if (existing.isPresent()) {
                created.add(existing.get());
                continue;
            }
            LegacyDebit debit = new LegacyDebit(UUID.randomUUID(), tenantId, auth.id(),
                    draft.enrollmentId(), draft.amount(), draft.debitDate());
            store.saveDebit(debit);
            created.add(debit);
            newlyCreated.add(debit);
            records.add(new Febraban150Port.RemittanceRecord(
                    Febraban150Port.RecordType.E, draft.enrollmentId(), debit.id(),
                    draft.amount(), draft.debitDate(), ++seq));
        }
        for (String enrollmentId : cancellationsPending) {
            // Correcao D3 da revisao: C so sai para autorizacao que existe e
            // esta efetivamente CANCELADA - nunca para uma string qualquer.
            LegacyDebitAuthorization auth = store.findAuthorizationByEnrollment(tenantId, enrollmentId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "matricula nao encontrada para o tenant: " + enrollmentId));
            if (auth.status() != LegacyDebitAuthorization.Status.CANCELADA) {
                throw new IllegalStateException("registro C exige optante cancelado (status "
                        + auth.status() + "): " + enrollmentId);
            }
            records.add(new Febraban150Port.RemittanceRecord(
                    Febraban150Port.RecordType.C, enrollmentId, null, null, null, ++seq));
        }
        records.add(new Febraban150Port.RemittanceRecord(
                Febraban150Port.RecordType.F, null, null,
                newlyCreated.stream().map(LegacyDebit::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                null, ++seq));
        filePort.writeRemittance(new Febraban150Port.RemittanceBatch(tenantId, receiverBankCode,
                List.copyOf(records)));
        return List.copyOf(created);
    }

    public record DebitDraft(String enrollmentId, BigDecimal amount, LocalDate debitDate) {
        public DebitDraft {
            if (enrollmentId == null || enrollmentId.isBlank()) {
                throw new IllegalArgumentException("matricula do debito e obrigatoria");
            }
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("valor do debito deve ser > 0");
            }
            Objects.requireNonNull(debitDate, "data do debito e obrigatoria");
        }
    }

    /** Reenvio: regera o registro E do debito ainda nao baixado e conta o reenvio. */
    public void resendDebit(UUID tenantId, UUID debitId, LocalDate today) {
        requireLegacyUsable(today);
        LegacyDebit debit = store.findDebit(tenantId, debitId)
                .orElseThrow(() -> new IllegalArgumentException("debito desconhecido: " + debitId));
        if (debit.status != DebitStatus.REMETIDO) {
            throw new IllegalStateException("so debito remetido e nao baixado pode ser reenviado: "
                    + debit.status);
        }
        debit.remittanceCount++;
        store.saveDebit(debit);
        filePort.writeRemittance(new Febraban150Port.RemittanceBatch(tenantId, null,
                List.of(new Febraban150Port.RemittanceRecord(Febraban150Port.RecordType.E,
                        debit.enrollmentId, debit.id, debit.amount, debit.debitDate, 1),
                        new Febraban150Port.RemittanceRecord(Febraban150Port.RecordType.F,
                                null, null, debit.amount, null, 2))));
    }

    /** Cancelamento de optante: encerra a autorizacao; o registro C sai na proxima remessa. */
    public LegacyDebitAuthorization cancelOptant(UUID tenantId, String enrollmentId, Instant at) {
        LegacyDebitAuthorization auth = requireActiveLegacy(tenantId, enrollmentId);
        auth.cancel(at);
        store.saveAuthorization(auth);
        return auth;
    }

    // ------------------------------------------------------------------
    // Retorno com baixa idempotente
    // ------------------------------------------------------------------

    /** Processa o retorno ja lido pela porta; baixa UMA unica vez por debito. */
    public List<ReturnResult> processReturn(UUID tenantId, String fileReference, Instant at) {
        Objects.requireNonNull(at, "instante do processamento do retorno e obrigatorio");
        List<ReturnResult> results = new ArrayList<>();
        for (Febraban150Port.ReturnEntry entry : filePort.readReturn(tenantId, fileReference)) {
            Optional<LegacyDebit> found = entry.debitId() == null
                    ? Optional.empty() : store.findDebit(tenantId, entry.debitId());
            if (found.isEmpty()) {
                results.add(new ReturnResult(ReturnOutcome.NAO_IDENTIFICADO, entry.debitId()));
                continue;
            }
            LegacyDebit debit = found.get();
            if (debit.status == DebitStatus.BAIXADO) {
                // Repetido: sem efeito - nem settledAt nem outcomeCode sao tocados.
                results.add(new ReturnResult(ReturnOutcome.REPETIDO_SEM_EFEITO, debit.id()));
                continue;
            }
            // Correcao D1 da revisao: o codigo de ocorrencia do retorno e
            // guardado VERBATIM no debito (coluna outcome_code da V102 /
            // LegacyDebitEntryEntity.outcomeCode), na baixa e na divergencia.
            debit.outcomeCode = entry.outcomeCode();
            if (!entry.settled() || entry.amount() == null
                    || entry.amount().compareTo(debit.amount) != 0) {
                store.saveDebit(debit);
                results.add(new ReturnResult(ReturnOutcome.DIVERGENTE, debit.id()));
                continue;
            }
            debit.status = DebitStatus.BAIXADO;
            debit.settledAt = at;
            store.saveDebit(debit);
            results.add(new ReturnResult(ReturnOutcome.BAIXADO, debit.id()));
        }
        return List.copyOf(results);
    }

    // ------------------------------------------------------------------
    // Campanha de conversao ao Pix Automatico (consentimento guardado)
    // ------------------------------------------------------------------

    /** Optantes ativos no legado = publico da campanha de conversao. */
    public List<LegacyDebitAuthorization> conversionCampaignTargets(UUID tenantId) {
        return store.findAuthorizations(tenantId).stream()
                .filter(a -> a.status() == LegacyDebitAuthorization.Status.ATIVA_LEGADO)
                .toList();
    }

    /**
     * Registra o consentimento GUARDADO do optante e converte: a
     * autorizacao sai do legado (CONVERTIDA_PIX) e nao gera mais debito
     * FEBRABAN 150. Sem consentimento registrado, nada muda.
     *
     * <p>Idempotente por prova (B2-2): o mesmo consentimento registrado
     * duas vezes (mesma matricula + mesmo hash de prova) devolve o
     * registro ja guardado, sem duplicar e sem re-marcar a autorizacao
     * (que ja esta CONVERTIDA_PIX e falharia no portao de ativa).
     */
    public LegacyConversionConsent registerConversionConsent(
            UUID tenantId, String enrollmentId, String proofHash, String proofReference,
            String channel, Instant consentedAt) {
        LegacyDebitAuthorization existing = store.findAuthorizationByEnrollment(tenantId, enrollmentId)
                .orElse(null);
        if (existing != null && proofHash != null) {
            String normalized = proofHash.toLowerCase();
            for (LegacyConversionConsent prior : store.findConsents(tenantId, existing.id())) {
                if (prior.proofHash().equals(normalized)) {
                    // Consentimento ja guardado (ex.: pelo adaptador da ED-27):
                    // devolve o registro sem duplicar, MAS ainda converte a
                    // autorizacao quando ela segue ativa - a conversao nao
                    // pode ser pulada junto com a deduplicacao.
                    if (existing.status() == LegacyDebitAuthorization.Status.ATIVA_LEGADO) {
                        existing.markConvertedToPix(consentedAt);
                        store.saveAuthorization(existing);
                    }
                    return prior;
                }
            }
        }
        LegacyDebitAuthorization auth = requireActiveLegacy(tenantId, enrollmentId);
        LegacyConversionConsent consent = new LegacyConversionConsent(UUID.randomUUID(), tenantId,
                auth.id(), enrollmentId, proofHash, proofReference, channel, consentedAt);
        store.saveConsent(consent);
        auth.markConvertedToPix(consentedAt);
        store.saveAuthorization(auth);
        return consent;
    }

    /** Todas as autorizacoes legadas importadas do tenant (apoio ao adaptador da ED-27, B2-5). */
    public List<LegacyDebitAuthorization> listLegacyAuthorizations(UUID tenantId) {
        return store.findAuthorizations(tenantId);
    }

    /** Autorizacao legada pela unidade vinculada, se houver (apoio ao adaptador da ED-27, B2-5). */
    public Optional<LegacyDebitAuthorization> findLegacyAuthorizationByUnit(UUID tenantId, UUID unitId) {
        if (unitId == null) {
            return Optional.empty();
        }
        return store.findAuthorizations(tenantId).stream()
                .filter(a -> unitId.equals(a.unitId())).findFirst();
    }

    private LegacyDebitAuthorization requireActiveLegacy(UUID tenantId, String enrollmentId) {
        LegacyDebitAuthorization auth = store.findAuthorizationByEnrollment(tenantId, enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "matricula nao encontrada para o tenant: " + enrollmentId));
        if (auth.status() != LegacyDebitAuthorization.Status.ATIVA_LEGADO) {
            throw new IllegalStateException("optante nao esta ativo no legado (status "
                    + auth.status() + "): " + enrollmentId);
        }
        return auth;
    }
}

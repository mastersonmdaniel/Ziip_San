package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.parameters.LegacyDebitParameters;
import br.com.ziipsan.commercial.collection.pixauto.AccountDebitAuthorizationGate;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Integracao do debito em conta (ED-27): Pix Automatico (P4.5) como unico
 * canal de autorizacao NOVA + legado FEBRABAN 150 (P3.24, pela porta
 * {@link LegacyDebitPort}) para a base atual de optantes + campanha de
 * conversao ao Pix Automatico, sob o calendario regulatorio cuja FONTE
 * de datas e a P3.24 ({@link DebitRegulatoryCalendar} apenas delega a
 * {@code BcbDebitRegimeRules}).
 *
 * <p>Regras provadas aqui (o resto e da P4.5/P3.24, reusado, nao duplicado):
 * <ol>
 *   <li>autorizacao nova so por Pix Automatico - a trava e a MESMA da P4.5
 *       ({@link AccountDebitAuthorizationGate}); canal legado e recusado;</li>
 *   <li>operacao legada exige {@code pay.legacy_debit_enabled} ligado -
 *       lido pela classe de parametros DA P3.24
 *       ({@code collection.parameters.LegacyDebitParameters}, sem classe
 *       paralela nesta entrega, correcao D3) - E data estritamente
 *       anterior ao desligamento deduzido em {@code BcbDebitRegimeRules}
 *       (P3.24) via {@link DebitRegulatoryCalendar};</li>
 *   <li>remessa legada so carrega ITENS cuja autorizacao vinculada
 *       ({@code LegacyDebitItem.unitId}) esta ATIVA e e de MESMO BANCO
 *       (condicao da P3.24); os demais itens sao EXCLUIDOS antes da
 *       chamada a porta e CONTADOS em
 *       {@code RemittanceResult.excludedCount} (correcao D1);</li>
 *   <li>retorno processado com baixa IDEMPOTENTE POR DEBITO, alinhada a
 *       P3.24 (correcoes D2/D3 - ver {@link #processLegacyReturn});</li>
 *   <li>conversao ao Pix Automatico so se consuma com o CONSENTIMENTO do
 *       optante guardado com prova (hash SHA-256 + referencia, mesmo desenho
 *       de prova da autorizacao da P4.5); sem prova, o optante segue como
 *       candidato da campanha e continua no legado ate o desligamento.</li>
 * </ol>
 */
public final class DebitAccountIntegration {

    public enum ReturnProcessingOutcome { PROCESSADO, REPETIDO_SEM_EFEITO }

    public record ReturnProcessingResult(ReturnProcessingOutcome outcome, int settledCount,
                                         int rejectedCount, java.math.BigDecimal settledTotal) {
    }

    public enum ConversionStatus { CANDIDATO, CONVERTIDO }

    /** Decisao de conversao de um optante legado. */
    public record ConversionDecision(UUID unitId, ConversionStatus status, String consentProofHash,
                                     String consentProofReference) {
    }

    /**
     * Porta de idempotencia dos retornos legados (adaptador sobre a tabela
     * da integracao). Guarda DOIS tipos de chave, no mesmo desenho da
     * P3.24 (baixa por debito) combinado com o marcador do arquivo:
     * <ul>
     *   <li>chave POR DEBITO - {@code "DEBIT:" + chargeIdentifier}: marcada
     *       IMEDIATAMENTE apos a baixa bem-sucedida daquela linha; e a
     *       chave canonica da idempotencia, identica em semantica a do
     *       {@code LegacyDebitService} da P3.24 (la, o estado BAIXADO do
     *       proprio debito; o arquivo de origem nao entra na chave);</li>
     *   <li>chave DO ARQUIVO - {@code ReturnBatch.returnKey} (NSA + hash):
     *       marcada so ao fim de um processamento completo, como atalho
     *       para devolver {@code REPETIDO_SEM_EFEITO} sem varrer linhas.</li>
     * </ul>
     */
    public interface ProcessedReturns {
        boolean alreadyProcessed(UUID tenantId, String returnKey);

        void markProcessed(UUID tenantId, String returnKey);
    }

    /** Porta de baixa das contas pagas pelo retorno legado (baixa da P3.1 na fiacao). */
    public interface SettlementSink {
        void settle(String chargeIdentifier, java.math.BigDecimal amount);
    }

    /** Recusa de operacao legada (desligada, desligada pela data ou fora da janela). */
    public static final class LegacyDebitRefusedException extends RuntimeException {
        public LegacyDebitRefusedException(String message) {
            super(message);
        }
    }

    /** Prefixo da chave canonica de idempotencia POR DEBITO (alinhada a P3.24). */
    static final String DEBIT_KEY_PREFIX = "DEBIT:";

    /** Chave canonica de idempotencia de um debito: por identificador do debito, SEM o arquivo de origem. */
    public static String debitSettlementKey(String chargeIdentifier) {
        Objects.requireNonNull(chargeIdentifier, "identificador do debito e obrigatorio");
        return DEBIT_KEY_PREFIX + chargeIdentifier;
    }

    private final LegacyDebitPort legacyPort;
    private final LegacyDebitParameters parameters;
    private final ProcessedReturns processedReturns;
    private final SettlementSink settlementSink;

    public DebitAccountIntegration(LegacyDebitPort legacyPort, LegacyDebitParameters parameters,
                                   ProcessedReturns processedReturns, SettlementSink settlementSink) {
        this.legacyPort = Objects.requireNonNull(legacyPort, "LegacyDebitPort e obrigatoria");
        this.parameters = Objects.requireNonNull(parameters, "LegacyDebitParameters e obrigatorio");
        this.processedReturns = Objects.requireNonNull(processedReturns, "ProcessedReturns e obrigatoria");
        this.settlementSink = Objects.requireNonNull(settlementSink, "SettlementSink e obrigatoria");
    }

    /** Trava unica de autorizacao nova: delega a trava da P4.5, sem regra paralela. */
    public static void requireNewAuthorizationChannel(AccountDebitAuthorizationGate.DebitChannel channel) {
        AccountDebitAuthorizationGate.requirePixAutomaticoForNewAuthorization(channel);
    }

    /** Portao de qualquer operacao legada: parametro (P3.24) ligado + antes do desligamento deduzido (P3.24). */
    public void requireLegacyOperable(DebitRegulatoryCalendar.AgreementKind kind, LocalDate onDate) {
        Objects.requireNonNull(onDate, "data da operacao e obrigatoria");
        if (!parameters.legacyDebitEnabled()) {
            throw new LegacyDebitRefusedException(
                    "pay.legacy_debit_enabled desligado: legado FEBRABAN 150 indisponivel para o tenant");
        }
        if (!DebitRegulatoryCalendar.legacyOperatesOn(kind, onDate)) {
            throw new LegacyDebitRefusedException("legado FEBRABAN 150 desligado em "
                    + DebitRegulatoryCalendar.legacyShutdownDate(kind) + " para convenio " + kind
                    + " (calendario regulatorio BCB, fonte BcbDebitRegimeRules da P3.24); "
                    + "debito novo e migrado so por Pix Automatico");
        }
    }

    /**
     * Gera a remessa do ciclo (POC-29): so entram os itens VINCULADOS a
     * autorizacoes legadas ATIVAS e de MESMO BANCO; item sem vinculo, de
     * autorizacao inexistente na lista, inativa ou de outro banco e
     * EXCLUIDO do arquivo e CONTADO ({@code excludedCount} do resultado).
     * Se nenhum item sobreviver a filtragem havendo itens, a remessa e
     * recusada em vez de gerada vazia, e a porta nao e chamada.
     */
    public LegacyDebitPort.RemittanceResult generateLegacyRemittance(
            UUID tenantId, DebitRegulatoryCalendar.AgreementKind kind, LocalDate onDate,
            List<LegacyDebitPort.LegacyAuthorization> authorizations,
            List<LegacyDebitPort.LegacyDebitItem> items) {
        requireLegacyOperable(kind, onDate);
        Objects.requireNonNull(authorizations, "autorizacoes legadas sao obrigatorias");
        Objects.requireNonNull(items, "itens de debito sao obrigatorios");
        List<LegacyDebitPort.LegacyDebitItem> eligibleItems = eligibleLegacyItems(authorizations, items);
        int excluded = items.size() - eligibleItems.size();
        if (!items.isEmpty() && eligibleItems.isEmpty()) {
            throw new LegacyDebitRefusedException(
                    "nenhum item vinculado a autorizacao legada ativa de mesmo banco: remessa vazia e recusada ("
                            + excluded + " itens excluidos por inelegibilidade)");
        }
        LegacyDebitPort.RemittanceResult result = legacyPort.generateRemittance(tenantId, eligibleItems);
        return new LegacyDebitPort.RemittanceResult(result.remittanceId(), result.itemCount(),
                result.total(), excluded);
    }

    /** Autorizacoes legadas que podem ir a remessa: ativas e de mesmo banco. */
    public static List<LegacyDebitPort.LegacyAuthorization> eligibleLegacyAuthorizations(
            List<LegacyDebitPort.LegacyAuthorization> authorizations) {
        Objects.requireNonNull(authorizations, "autorizacoes legadas sao obrigatorias");
        return authorizations.stream().filter(a -> a.active() && a.sameBank()).toList();
    }

    /**
     * Itens que podem ir a remessa (correcao D1): apenas os vinculados
     * ({@code unitId}) a uma autorizacao ELEGIVEL (ativa + mesmo banco).
     * Todo o resto - sem vinculo, autorizacao fora da lista, inativa ou
     * de outro banco - fica fora, sem excecao silenciosa: o chamador
     * conta a diferenca como excluidos.
     */
    public static List<LegacyDebitPort.LegacyDebitItem> eligibleLegacyItems(
            List<LegacyDebitPort.LegacyAuthorization> authorizations,
            List<LegacyDebitPort.LegacyDebitItem> items) {
        Objects.requireNonNull(items, "itens de debito sao obrigatorios");
        Set<UUID> eligibleUnitIds = eligibleLegacyAuthorizations(authorizations).stream()
                .map(LegacyDebitPort.LegacyAuthorization::unitId)
                .collect(Collectors.toSet());
        return items.stream()
                .filter(i -> i.unitId() != null && eligibleUnitIds.contains(i.unitId()))
                .toList();
    }

    /**
     * Processa o retorno legado com baixa IDEMPOTENTE tambem no caminho
     * de falha (correcao D2), com a chave canonica POR DEBITO alinhada a
     * P3.24 (correcao D3):
     * <ol>
     *   <li><b>Pre-validacao completa do lote</b>: TODAS as linhas
     *       liquidadas precisam ter valor conhecido ANTES de qualquer
     *       baixa; valor faltante lanca {@code IllegalStateException}
     *       com ZERO baixas feitas - a falha conhecida do desenho
     *       anterior (baixar as primeiras linhas e depois falhar) nao
     *       deixa residuo;</li>
     *   <li><b>Marcacao por debito, imediatamente apos cada baixa</b>:
     *       cada linha liquidada e marcada na hora sob a chave
     *       {@code DEBIT:<identificador>} - a mesma semantica da P3.24,
     *       onde quem impede a segunda baixa e o estado do DEBITO, nao o
     *       arquivo. Se uma baixa posterior falhar (ex.: o sink lanca),
     *       o reprocessamento PULA as linhas ja marcadas e retoma das
     *       restantes: cada debito baixa exatamente UMA vez, venha o
     *       retorno neste arquivo ou em outro;</li>
     *   <li><b>Chave do arquivo como atalho</b>: marcada apenas ao fim
     *       do processamento completo; reprocessar um arquivo ja completo
     *       devolve {@code REPETIDO_SEM_EFEITO} sem varrer as linhas.</li>
     * </ol>
     * Justificativa da escolha (D2): pre-validacao + marcacao por linha
     * dispensam depender da transacao da fiacao (V6_4/P3.1) para a
     * alegacao de idempotencia desta classe; a marcacao vem DEPOIS da
     * baixa da linha (nunca antes) para uma linha cuja baixa falhou nao
     * ficar marcada como baixada sem ter baixado.
     * Valores das linhas liquidadas sao resolvidos por quem chama a
     * partir dos itens do ciclo (a porta devolve desfecho, nao valor).
     */
    public ReturnProcessingResult processLegacyReturn(UUID tenantId,
                                                      DebitRegulatoryCalendar.AgreementKind kind,
                                                      LocalDate onDate,
                                                      LegacyDebitPort.ReturnBatch batch,
                                                      java.util.Map<String, java.math.BigDecimal> amountByCharge) {
        requireLegacyOperable(kind, onDate);
        Objects.requireNonNull(batch, "lote de retorno e obrigatorio");
        Objects.requireNonNull(amountByCharge, "valores por identificador sao obrigatorios");
        if (processedReturns.alreadyProcessed(tenantId, batch.returnKey())) {
            return new ReturnProcessingResult(ReturnProcessingOutcome.REPETIDO_SEM_EFEITO, 0, 0,
                    java.math.BigDecimal.ZERO);
        }
        // (1) Pre-validacao completa: nenhuma baixa antes de o lote inteiro ser baixavel.
        for (LegacyDebitPort.ReturnLine line : batch.lines()) {
            if (line.settled() && amountByCharge.get(line.chargeIdentifier()) == null) {
                throw new IllegalStateException(
                        "retorno liquidou identificador sem valor conhecido: " + line.chargeIdentifier());
            }
        }
        int settled = 0;
        int rejected = 0;
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (LegacyDebitPort.ReturnLine line : batch.lines()) {
            if (!line.settled()) {
                rejected++;
                continue;
            }
            // (2) Ja baixado por debito (nesta ou em outra passagem/arquivo): nao baixa de novo.
            String debitKey = debitSettlementKey(line.chargeIdentifier());
            if (processedReturns.alreadyProcessed(tenantId, debitKey)) {
                continue;
            }
            java.math.BigDecimal amount = amountByCharge.get(line.chargeIdentifier());
            settlementSink.settle(line.chargeIdentifier(), amount);
            // Marcacao imediata pos-baixa: uma falha adiante nao faz esta linha baixar de novo.
            processedReturns.markProcessed(tenantId, debitKey);
            settled++;
            total = total.add(amount);
        }
        // (3) Arquivo completo: marca a chave do arquivo como atalho de repeticao.
        processedReturns.markProcessed(tenantId, batch.returnKey());
        return new ReturnProcessingResult(ReturnProcessingOutcome.PROCESSADO, settled, rejected, total);
    }

    /** Reenvio legado, sob o mesmo portao de operabilidade. */
    public void resendLegacy(UUID tenantId, DebitRegulatoryCalendar.AgreementKind kind,
                             LocalDate onDate, String chargeIdentifier) {
        requireLegacyOperable(kind, onDate);
        legacyPort.resend(tenantId, chargeIdentifier);
    }

    /** Cancelamento de optante no legado, sob o mesmo portao. */
    public void cancelLegacyOptant(UUID tenantId, DebitRegulatoryCalendar.AgreementKind kind,
                                   LocalDate onDate, UUID unitId) {
        requireLegacyOperable(kind, onDate);
        legacyPort.cancelOptant(tenantId, unitId);
    }

    /**
     * Conversao ao Pix Automatico (campanha da P4.5, ramo da P3.24): so
     * converte com consentimento GUARDADO com prova (hash SHA-256 de 64 hex
     * + referencia). Sem prova valida, o optante permanece CANDIDATO - a
     * campanha nao converte ninguem por omissao.
     */
    public ConversionDecision convertOptant(UUID unitId, String consentProofHash,
                                            String consentProofReference) {
        Objects.requireNonNull(unitId, "unidade do optante e obrigatoria");
        boolean proofOk = consentProofHash != null && consentProofHash.matches("[0-9a-fA-F]{64}")
                && consentProofReference != null && !consentProofReference.isBlank();
        if (!proofOk) {
            return new ConversionDecision(unitId, ConversionStatus.CANDIDATO, null, null);
        }
        return new ConversionDecision(unitId, ConversionStatus.CONVERTIDO,
                consentProofHash, consentProofReference);
    }

    /** Optantes ainda legados (candidatos ou nao convertidos) para a campanha. */
    public static List<UUID> pendingConversion(List<ConversionDecision> decisions) {
        Objects.requireNonNull(decisions, "decisoes de conversao sao obrigatorias");
        return decisions.stream()
                .filter(d -> d.status() == ConversionStatus.CANDIDATO)
                .map(ConversionDecision::unitId)
                .toList();
    }

    /** Busca uma autorizacao legada importada pela unidade (conveniencia sobre a porta). */
    public Optional<LegacyDebitPort.LegacyAuthorization> legacyAuthorizationOf(UUID tenantId, UUID unitId) {
        return legacyPort.importLegacyAuthorizations(tenantId).stream()
                .filter(a -> a.unitId().equals(unitId))
                .findFirst();
    }
}

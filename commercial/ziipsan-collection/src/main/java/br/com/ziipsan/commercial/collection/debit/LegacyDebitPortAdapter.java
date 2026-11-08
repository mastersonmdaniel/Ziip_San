package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.legacydebit.Febraban150Port;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitAuthorization;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador da porta legada da ED-27 sobre a implementacao da P3.24
 * (B2-5 da rodada 2 da auditoria continua). Antes desta correcao a
 * {@link LegacyDebitPort} nao tinha implementacao nenhuma e os tipos
 * nao casavam com a P3.24.
 *
 * <p>Mapeamento definido (antes indefinido):
 * <ul>
 *   <li>{@code LegacyAuthorization} &lt;-&gt; {@code LegacyDebitAuthorization}:
 *       {@code enrollmentRef} e a matricula de conferencia,
 *       {@code active} e o status ATIVA_LEGADO, bancos como codigos de
 *       3 digitos; a condicao de mesmo banco e a mesma nos dois lados;</li>
 *   <li>{@code LegacyDebitItem} -&gt; {@code DebitDraft}: o item e
 *       vinculado a autorizacao pelo {@code unitId}; sem autorizacao
 *       ativa de mesmo banco o item e EXCLUIDO e contado (o mesmo
 *       desenho da filtragem da ED-27, aqui como defesa em profundidade);
 *       itens de bancos recebedores distintos vao em chamadas separadas
 *       ao servico, uma por banco;</li>
 *   <li>{@code chargeIdentifier} &lt;-&gt; {@code debitId}: o
 *       identificador de cobranca da ED-27 E o UUID do debito da P3.24,
 *       em texto. Na geracao, o debito criado pelo servico define o
 *       identificador (o {@code chargeIdentifier} de entrada e apenas o
 *       rotulo do chamador e nao e preservado); no retorno, no reenvio
 *       e na leitura, o identificador e lido como UUID do debito;</li>
 *   <li>{@code ReturnBatch.returnKey}: a referencia do arquivo de
 *       retorno informada pelo chamador (NSA + hash, no desenho da
 *       P3.1) - este adaptador apenas LE o retorno; a baixa idempotente
 *       e da {@code DebitAccountIntegration} / {@code LegacyDebitService};</li>
 *   <li>{@code RemittanceResult.remittanceId}: o id do primeiro debito
 *       criado nesta chamada (o servico nao tem id proprio de lote);
 *       {@code excludedCount} e o numero de itens excluidos aqui.</li>
 * </ul>
 *
 * <p>A data de operacao do legado vem do {@link Clock} do adaptador
 * (o servico exige a data para o portao de desligamento).
 */
public final class LegacyDebitPortAdapter implements LegacyDebitPort {

    private final LegacyDebitService service;
    private final Febraban150Port filePort;
    private final Clock clock;

    public LegacyDebitPortAdapter(LegacyDebitService service, Febraban150Port filePort, Clock clock) {
        this.service = Objects.requireNonNull(service, "LegacyDebitService da P3.24 e obrigatorio");
        this.filePort = Objects.requireNonNull(filePort, "Febraban150Port e obrigatoria");
        this.clock = Objects.requireNonNull(clock, "relogio e obrigatorio");
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    @Override
    public List<LegacyAuthorization> importLegacyAuthorizations(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        return service.listLegacyAuthorizations(tenantId).stream()
                .map(a -> new LegacyAuthorization(a.tenantId(), a.unitId(), a.enrollmentId(),
                        a.payerBankCode(), a.receiverBankCode(),
                        a.status() == LegacyDebitAuthorization.Status.ATIVA_LEGADO))
                .toList();
    }

    @Override
    public RemittanceResult generateRemittance(UUID tenantId, List<LegacyDebitItem> items) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(items, "itens de debito sao obrigatorios");
        // Agrupa por banco recebedor: o servico gera uma remessa por banco.
        java.util.Map<String, List<LegacyDebitService.DebitDraft>> draftsByBank =
                new java.util.LinkedHashMap<>();
        int excluded = 0;
        for (LegacyDebitItem item : items) {
            LegacyDebitAuthorization auth = item.unitId() == null ? null
                    : service.findLegacyAuthorizationByUnit(tenantId, item.unitId()).orElse(null);
            if (auth == null || auth.status() != LegacyDebitAuthorization.Status.ATIVA_LEGADO
                    || !auth.sameBank()) {
                excluded++;
                continue;
            }
            draftsByBank.computeIfAbsent(auth.receiverBankCode(), k -> new ArrayList<>())
                    .add(new LegacyDebitService.DebitDraft(auth.enrollmentId(), item.amount(),
                            item.debitDate()));
        }
        List<LegacyDebitService.LegacyDebit> created = new ArrayList<>();
        for (var entry : draftsByBank.entrySet()) {
            created.addAll(service.generateRemittance(tenantId, entry.getKey(), entry.getValue(),
                    List.of(), List.of(), today()));
        }
        BigDecimal total = created.stream().map(LegacyDebitService.LegacyDebit::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String remittanceId = created.isEmpty() ? null : created.get(0).id().toString();
        return new RemittanceResult(remittanceId, created.size(), total, excluded);
    }

    @Override
    public ReturnBatch readReturn(UUID tenantId, String returnReference) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(returnReference, "referencia do retorno e obrigatoria");
        List<ReturnLine> lines = filePort.readReturn(tenantId, returnReference).stream()
                .map(e -> new ReturnLine(e.debitId() == null ? null : e.debitId().toString(),
                        e.settled(), e.outcomeCode()))
                .toList();
        return new ReturnBatch(returnReference, lines);
    }

    @Override
    public void resend(UUID tenantId, String chargeIdentifier) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        UUID debitId = parseDebitId(chargeIdentifier);
        service.resendDebit(tenantId, debitId, today());
    }

    @Override
    public void cancelOptant(UUID tenantId, UUID unitId) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        LegacyDebitAuthorization auth = service.findLegacyAuthorizationByUnit(tenantId, unitId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unidade sem autorizacao legada para o tenant: " + unitId));
        service.cancelOptant(tenantId, auth.enrollmentId(), Instant.now(clock));
    }

    /** O identificador de cobranca da ED-27 e o UUID do debito da P3.24, em texto. */
    static UUID parseDebitId(String chargeIdentifier) {
        if (chargeIdentifier == null || chargeIdentifier.isBlank()) {
            throw new IllegalArgumentException("identificador do debito e obrigatorio");
        }
        try {
            return UUID.fromString(chargeIdentifier);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "identificador de cobranca nao e o UUID do debito da P3.24: " + chargeIdentifier, e);
        }
    }
}

package br.com.ziipsan.platform.engagement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mensagem impressa na conta (ED-101): gerada SOMENTE a partir do modelo
 * aprovado pela agencia registrado em {@code bill.invoice_template}
 * (aceite) — nenhum texto livre e composto por este codigo. Sem modelo
 * vigente, ou com o relatorio suprimido, nao ha mensagem.
 *
 * <p>Variaveis declaradas (conjunto fechado e proposto — a confirmar na
 * integracao com o conteudo minimo da fatura da P2.32): {@code {period}},
 * {@code {own_m3}}, {@code {goal_m3}}, {@code {peer_average_m3}},
 * {@code {peer_median_m3}} e {@code {position}} — todas agregadas ou do
 * proprio usuario; modelo com variavel fora do conjunto e recusado
 * ({@code TEMPLATE_VARIABLE_UNKNOWN}), para nenhum dado de terceiro poder
 * entrar na conta impressa por esta via. Valores em m3 com 2 casas
 * (exibicao), HALF_UP.
 */
public final class InvoiceMessageService {

    public enum Outcome {RENDERED, REPORT_NOT_PUBLISHED, TEMPLATE_NOT_CONFIGURED}

    public record InvoiceMessage(Outcome outcome, String text) {
    }

    private static final Pattern VARIABLE = Pattern.compile("\\{([a-z0-9_]+)\\}");
    private static final Set<String> KNOWN = Set.of(
            "period", "own_m3", "goal_m3", "peer_average_m3", "peer_median_m3", "position");

    private final EngagementParameters parameters;

    public InvoiceMessageService(EngagementParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "parametros sao obrigatorios");
    }

    private static String m3(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public InvoiceMessage build(UUID tenantId, PeerReport report, Optional<ConsumptionGoal> goal) {
        Objects.requireNonNull(report, "relatorio e obrigatorio");
        Objects.requireNonNull(goal, "meta (ou ausencia dela) e obrigatoria");
        if (!report.published()) {
            return new InvoiceMessage(Outcome.REPORT_NOT_PUBLISHED, null);
        }
        Optional<String> template = parameters.invoiceTemplate(tenantId);
        if (template.isEmpty() || template.get().isBlank()) {
            return new InvoiceMessage(Outcome.TEMPLATE_NOT_CONFIGURED, null);
        }
        Map<String, String> values = Map.of(
                "period", report.period().toString(),
                "own_m3", m3(report.ownConsumptionM3()),
                "goal_m3", goal.map(g -> m3(g.targetM3())).orElse(""),
                "peer_average_m3", m3(report.cohortAverageM3()),
                "peer_median_m3", m3(report.cohortMedianM3()),
                "position", report.position());
        Matcher matcher = VARIABLE.matcher(template.get());
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!KNOWN.contains(name)) {
                throw new EngagementValidationException("TEMPLATE_VARIABLE_UNKNOWN",
                        "bill.invoice_template usa variavel fora do conjunto declarado: " + name);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(values.get(name)));
        }
        matcher.appendTail(out);
        return new InvoiceMessage(Outcome.RENDERED, out.toString());
    }
}

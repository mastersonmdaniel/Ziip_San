package br.com.ziipsan.platform.statuspage;

import java.time.Instant;
import java.util.Objects;

/**
 * P3.18: publicação, na página de status, do resumo da revisão
 * pós-incidente — o bloco "Resumo publicável" do modelo da P3.11
 * (PLT-23; {@code docs/operacao/modelo-revisao-pos-incidente.md} §7).
 *
 * <p>Regras (todas recusam com erro explícito, sem publicar pela metade):
 * <ul>
 *   <li>só se publica resumo de incidente {@code RESOLVED}: revisão
 *       pós-incidente é, por definição do modelo, posterior ao fim do
 *       incidente — publicar "o que foi feito e o que muda" com o
 *       incidente aberto seria afirmar uma conclusão que ainda não
 *       existe;</li>
 *   <li>o texto é obrigatório e não vazio: publicar um resumo vazio
 *       cumpriria o aceite no papel e nada diria ao visitante;</li>
 *   <li>o conteúdo do texto é responsabilidade da revisão (o modelo da
 *       P3.11 manda: sem dado pessoal nem detalhe interno de segurança);
 *       esta classe não inventa nem reescreve texto.</li>
 * </ul>
 */
public final class PostIncidentSummary {

    private PostIncidentSummary() {
    }

    /**
     * Publica {@code summaryText} (o bloco §7 da revisão pós-incidente)
     * no incidente e devolve a cópia publicável. {@code publishedAt} é
     * entregue por quem chama para o teste usar relógio fixo.
     */
    public static StatusIncident publish(StatusIncident incident, String summaryText, Instant publishedAt) {
        Objects.requireNonNull(incident, "Incidente é obrigatório para publicar o resumo");
        Objects.requireNonNull(publishedAt, "published_at do resumo é obrigatório");
        if (incident.status() != StatusIncident.Status.RESOLVED) {
            throw new IllegalArgumentException(
                    "Resumo pós-incidente recusado: o incidente \"" + incident.title()
                            + "\" ainda está aberto (" + incident.status()
                            + ") — o modelo da P3.11 só tem resumo depois da resolução");
        }
        if (summaryText == null || summaryText.isBlank()) {
            throw new IllegalArgumentException(
                    "Resumo pós-incidente recusado: o bloco 'Resumo publicável' do modelo da P3.11 é obrigatório");
        }
        if (publishedAt.isBefore(incident.resolvedAt())) {
            throw new IllegalArgumentException(
                    "Resumo pós-incidente recusado: publicação anterior à resolução do incidente");
        }
        return incident.withPublishedSummary(summaryText, publishedAt);
    }
}

package br.com.ziipsan.integration.esfinge;

import br.com.ziipsan.integration.esfinge.EsfingeRemittance.Remittance;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Relatorio de envio da remessa e-Sfinge (ED-33), utilizavel no atestado
 * de habilitacao da VISAN (item de edital do 18 §5.1, preservado no
 * aceite): lista, por competencia, a versao do leiaute, o hash do arquivo
 * gerado, a situacao e o retorno registrado — a evidencia de que a
 * remessa foi gerada, validada, baixada e teve retorno.
 *
 * <p>Honestidade do relatorio: (a) so conta como "enviada com aceite" a
 * remessa com retorno ACCEPTED registrado — download sem retorno NAO e
 * envio comprovado; (b) se alguma remessa usou leiaute nao oficial, o
 * relatorio abre com o marcador {@code PENDENTE_LEIAUTE_OFICIAL_TCE_SC}
 * e nomeia as competencias — o atestado nao pode nascer de relatorio que
 * esconda a pendencia do leiaute; (c) o relatorio tem o seu proprio
 * SHA-256 (mesma funcao do arquivo), para ser citado e conferido.
 */
public final class EsfingeShipmentReport {

    private EsfingeShipmentReport() {}

    public record GeneratedReport(String text, String sha256, int totalRemittances, int acceptedCount) {}

    public static GeneratedReport generate(UUID tenantId, List<Remittance> remittances, OffsetDateTime generatedAt) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(remittances, "remessas sao obrigatorias");
        Objects.requireNonNull(generatedAt, "instante de geracao do relatorio e obrigatorio");
        List<Remittance> sorted = remittances.stream()
                .filter(r -> r.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(Remittance::competence).thenComparing(Remittance::generatedAt))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("RELATORIO DE ENVIO — REMESSA e-Sfinge (TCE/SC)\n");
        sb.append("Tenant: ").append(tenantId).append('\n');
        sb.append("Gerado em: ").append(generatedAt).append('\n');
        boolean anyUnofficial = sorted.stream().anyMatch(r -> !r.layoutOfficial());
        if (anyUnofficial) {
            sb.append(EsfingeLayout.OFFICIAL_LAYOUT_PENDING_MARKER)
              .append(": ha remessa(s) gerada(s) com leiaute NAO oficial do TCE/SC ")
              .append("(validacao estrutural apenas) — ver linhas marcadas.\n");
        }
        sb.append('\n');
        int accepted = 0;
        for (Remittance r : sorted) {
            boolean isAccepted = r.status() == EsfingeRemittance.Status.RETURN_REGISTERED
                    && r.returnReceipt().outcome() == EsfingeRemittance.ReturnOutcome.ACCEPTED;
            if (isAccepted) accepted++;
            sb.append("Competencia: ").append(r.competence())
              .append(" | Leiaute: ").append(r.layoutVersion())
              .append(r.layoutOfficial() ? " (oficial)" : " (NAO oficial — " + EsfingeLayout.OFFICIAL_LAYOUT_PENDING_MARKER + ")")
              .append(" | SHA-256: ").append(r.sha256())
              .append(" | Situacao: ").append(r.status());
            if (r.downloadedAt() != null)
                sb.append(" | Baixada em: ").append(r.downloadedAt()).append(" por ").append(r.downloadedBy());
            if (r.returnReceipt() != null)
                sb.append(" | Retorno: ").append(r.returnReceipt().outcome())
                  .append(" protocolo ").append(r.returnReceipt().protocolReference())
                  .append(" em ").append(r.returnReceipt().receivedOn());
            sb.append('\n');
        }
        sb.append('\n');
        sb.append("Total de remessas: ").append(sorted.size()).append('\n');
        sb.append("Enviadas com aceite (retorno ACCEPTED): ").append(accepted).append('\n');
        sb.append("Uso: evidencia para o atestado de habilitacao (VISAN). ")
          .append("Somente remessas com retorno ACCEPTED comprovam envio aceito; ")
          .append("download sem retorno nao comprova envio.\n");
        String text = sb.toString();
        return new GeneratedReport(text, EsfingeRemittanceFile.sha256(text), sorted.size(), accepted);
    }
}

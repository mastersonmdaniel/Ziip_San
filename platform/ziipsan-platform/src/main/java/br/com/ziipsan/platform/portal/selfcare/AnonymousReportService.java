package br.com.ziipsan.platform.portal.selfcare;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Relato SEM LOGIN (ED-38): cidadao relata problema (ex.: vazamento,
 * falta de agua) com foto (OBRIGATORIA, conforme o acceptance — o
 * "opcional" do acceptance recai sobre o contato; correcao D6 da
 * revisao cruzada), localizacao e contato OPCIONAL; o relato cria
 * protocolo da P1.36 com alvo {@code LOCATION_ONLY} e relatos do mesmo
 * evento sao AGRUPADOS num unico protocolo (o agrupamento segue a chave
 * de evento: categoria + celula de localizacao + janela de agrupamento
 * informada pelo chamador a partir do parametro vigente — nenhum limiar
 * de janela e presumido aqui).
 *
 * <p>Defesa antiabuso: a da P3.3 ({@code BotDefensePolicy} +
 * {@code portal.bot_defense}/{@code portal.account_throttle}) chega pela
 * porta {@link AbuseDefensePort} — desafio exigido ou limite estourado
 * recusam o relato ANTES de qualquer protocolo. Foto exige a varredura
 * da P1.20 (referencia ja varrida; o binario nao passa por esta classe).
 *
 * <p>ED-92: a ocorrencia de rede correspondente so passa a ser criada
 * "quando a ED-92 existir" — a ED-92 NAO existe nesta data; a porta
 * {@link NetworkOccurrencePort} e OPCIONAL e, ausente, o relato fica
 * SOMENTE como protocolo (declarado no resultado, nunca fingido).
 */
public final class AnonymousReportService {

    public enum Category { LEAK, NO_WATER, LOW_PRESSURE, WATER_QUALITY, SEWAGE, OTHER }

    public record Report(Category category, String locationText, String photoRef,
                         String contactOrNull, String description) {
        public Report {
            if (category == null || locationText == null || locationText.isBlank()
                    || photoRef == null || photoRef.isBlank()
                    || description == null || description.isBlank()) {
                throw new IllegalArgumentException(
                        "relato exige categoria, localizacao, foto e descricao (contato e opcional)");
            }
        }

        /** Chave de agrupamento do mesmo evento (celula = localizacao normalizada). */
        public String eventKey() {
            return category.name() + "|" + locationText.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public interface AbuseDefensePort {
        /** true quando a defesa da P3.3 libera a criacao (sem desafio pendente e dentro dos limites). */
        boolean creationAllowed(String originKey);
    }

    public interface ProtocolPort {
        /** Cria protocolo da P1.36 com alvo LOCATION_ONLY e devolve o numero/UUID do protocolo. */
        UUID createLocationOnlyProtocol(UUID tenantId, String eventKey, String locationText,
                                        String description, LocalDateTime openedAt);
        Optional<UUID> openProtocolForEvent(UUID tenantId, String eventKey, LocalDateTime since);
        void attachReport(UUID tenantId, UUID protocolId, String photoRef, String contactOrNull);
    }

    /** ED-92 (NAO EXISTE nesta data): quando existir, o adaptador cria a ocorrencia de rede. */
    public interface NetworkOccurrencePort {
        UUID createNetworkOccurrence(UUID tenantId, UUID protocolId, String eventKey);
    }

    public record Outcome(UUID protocolId, boolean groupedIntoExisting, boolean networkOccurrenceCreated) { }

    private final AbuseDefensePort abuse;
    private final ProtocolPort protocols;
    private final NetworkOccurrencePort networkOrNull;

    public AnonymousReportService(AbuseDefensePort abuse, ProtocolPort protocols,
                                  NetworkOccurrencePort networkOrNull) {
        if (abuse == null || protocols == null) {
            throw new IllegalArgumentException("defesa antiabuso (P3.3) e porta de protocolo (P1.36) sao obrigatorias");
        }
        this.abuse = abuse;
        this.protocols = protocols;
        this.networkOrNull = networkOrNull;
    }

    public Outcome submit(UUID tenantId, String originKey, Report report, LocalDateTime now,
                          java.time.Duration groupingWindow) {
        if (tenantId == null || report == null || now == null || groupingWindow == null
                || groupingWindow.isNegative() || groupingWindow.isZero()) {
            throw new IllegalArgumentException("tenant, relato, instante e janela de agrupamento sao obrigatorios");
        }
        if (!abuse.creationAllowed(originKey)) {
            throw new IllegalStateException("relato recusado pela defesa contra automacao e abuso do portal (P3.3)");
        }
        Optional<UUID> existing = protocols.openProtocolForEvent(tenantId, report.eventKey(), now.minus(groupingWindow));
        UUID protocolId;
        boolean grouped;
        if (existing.isPresent()) {
            protocolId = existing.get();
            grouped = true;
        } else {
            protocolId = protocols.createLocationOnlyProtocol(tenantId, report.eventKey(),
                    report.locationText(), report.description(), now);
            grouped = false;
        }
        protocols.attachReport(tenantId, protocolId, report.photoRef(), report.contactOrNull());
        boolean network = false;
        if (networkOrNull != null && !grouped) {
            networkOrNull.createNetworkOccurrence(tenantId, protocolId, report.eventKey());
            network = true;
        }
        return new Outcome(protocolId, grouped, network);
    }

    /** Agrupamento puro (mesma chave de evento) — util ao ensaio e a integracao. */
    public static List<String> distinctEventKeys(List<Report> reports) {
        return reports.stream().map(Report::eventKey).distinct().sorted().toList();
    }
}

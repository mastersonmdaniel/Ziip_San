package br.com.ziipsan.commercial.registry.quality;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fotografia do cadastro comercial que o motor da P3.32 avalia: fatos ja
 * lidos das tabelas donas, sem acesso a banco dentro do motor (o motor e
 * puro e testavel sobre massa sintetica, como o aceite pede).
 *
 * <p>Quem monta a fotografia e a fiacao de integracao, a partir de:
 * <ul>
 *   <li>{@code customers}, {@code customer_contacts},
 *       {@code customer_document_validations} e {@code holder_death_alerts}
 *       (registry, P1.3/P1.41) — o documento entra SO pelo indice cego
 *       ({@code documentHash}), nunca em claro;</li>
 *   <li>{@code consumer_units}, {@code unit_customers},
 *       {@code unit_subcategories} + {@code categories.regulatory_class},
 *       {@code blocks}, {@code reading_routes} (registry, P1.3/P2.23);</li>
 *   <li>{@code meter_installations} + {@code meters} e o contador de ciclos
 *       sem leitura de {@code meter_readings} (metering, P2.5/ED-14) — por
 *       fato, porque o registry nao depende do modulo metering;</li>
 *   <li>retornos de conta nao entregue da P2.8 (JOR-35) — por fato, com a
 *       chave do motivo/origem do catalogo da P2.8, porque o registry nao
 *       depende do modulo billing.</li>
 * </ul>
 *
 * <p>{@code checksumValid} e o veredito do {@code DocumentNumber} do core
 * sobre o documento decifrado na fiacao (a decifragem nao acontece no
 * motor); {@code null} = nao avaliado nesta varredura.
 */
public record CadastralSnapshot(
        List<CustomerFact> customers,
        List<ContactFact> contacts,
        List<UnitFact> units,
        List<LinkFact> links,
        List<EconomyFact> economies,
        List<RouteFact> routes,
        List<BlockFact> blocks,
        List<MeterFact> meters,
        List<ReadingFact> readings,
        List<UndeliveredFact> undelivered,
        List<DeathAlertFact> deathAlerts) {

    public CadastralSnapshot {
        customers = customers == null ? List.of() : List.copyOf(customers);
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
        units = units == null ? List.of() : List.copyOf(units);
        links = links == null ? List.of() : List.copyOf(links);
        economies = economies == null ? List.of() : List.copyOf(economies);
        routes = routes == null ? List.of() : List.copyOf(routes);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        meters = meters == null ? List.of() : List.copyOf(meters);
        readings = readings == null ? List.of() : List.copyOf(readings);
        undelivered = undelivered == null ? List.of() : List.copyOf(undelivered);
        deathAlerts = deathAlerts == null ? List.of() : List.copyOf(deathAlerts);
    }

    /** Cliente: documento presente/isento pelo indice cego; validacao externa mais recente (P1.41). */
    public record CustomerFact(
            UUID id,
            String personType,
            boolean documentPresent,
            String documentHash,
            boolean documentExempt,
            Boolean checksumValid,
            String latestValidationResult,
            LocalDate birthDate,
            boolean deleted) {
        public CustomerFact {
            if (id == null) {
                throw new IllegalArgumentException("CustomerFact sem id");
            }
        }
    }

    /**
     * Contato do cliente: estado de verificacao da P1.3 e vigencia — a
     * tabela real {@code customer_contacts} tem {@code valid_from} E
     * {@code valid_to} (V3_12); nulos = sem limite naquele extremo
     * (correcao R3: sem o validFrom, contato ainda nao vigente contava
     * como vigente).
     */
    public record ContactFact(UUID customerId, String verificationState,
                              LocalDate validFrom, LocalDate validTo) {
        public ContactFact {
            if (customerId == null) {
                throw new IllegalArgumentException("ContactFact sem customerId");
            }
        }

        /** Vigente na data: ja comecou (validFrom nulo ou <= data) e nao terminou (validTo nulo ou >= data). */
        public boolean currentOn(LocalDate date) {
            return (validFrom == null || !validFrom.isAfter(date))
                    && (validTo == null || !validTo.isBefore(date));
        }
    }

    /** Unidade consumidora: situacao cadastral, territorio, endereco, CNAE e area. */
    public record UnitFact(
            UUID id,
            String status,
            UUID blockId,
            Integer lot,
            Integer sublot,
            UUID readingRouteId,
            UUID addressId,
            boolean hasCoordinates,
            Boolean addressHasCoordinates,
            String cnaeCode,
            BigDecimal builtAreaM2,
            boolean deleted) {
        public UnitFact {
            if (id == null) {
                throw new IllegalArgumentException("UnitFact sem id");
            }
        }

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    /** Vinculo cliente-unidade (unit_customers): current = sem data de fim. */
    public record LinkFact(UUID unitId, UUID customerId, String role, boolean current) {
    }

    /** Economias vigentes da unidade, ja resolvidas para a classe regulatoria da categoria. */
    public record EconomyFact(UUID unitId, String regulatoryClass, int economies, boolean current) {
    }

    /** Rota de leitura com o setor dela (reading_routes.sector_id). */
    public record RouteFact(UUID id, UUID sectorId) {
    }

    /** Quadra com o setor dela (blocks.sector_id). */
    public record BlockFact(UUID id, UUID sectorId) {
    }

    /** Hidrometro vigente da unidade (instalacao sem retirada + o equipamento). */
    public record MeterFact(
            UUID unitId,
            UUID meterId,
            Integer manufactureYear,
            LocalDate installedOn,
            LocalDate lastVerificationOn) {
    }

    /** Contador de ciclos consecutivos sem leitura da unidade (de meter_readings). */
    public record ReadingFact(UUID unitId, int consecutiveCyclesWithoutReading) {
    }

    /** Retorno de conta nao entregue (P2.8, JOR-35): chaves do catalogo da P2.8. */
    public record UndeliveredFact(
            UUID unitId,
            UUID billId,
            String reasonKey,
            String originKey,
            LocalDate returnDate) {
    }

    /** Alerta de obito do titular (P1.41): status PENDING_REVIEW ou CONFIRMED. */
    public record DeathAlertFact(UUID customerId, UUID unitId, String status) {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Montador da fotografia, para a fiacao e para a massa sintetica dos testes. */
    public static final class Builder {
        private final List<CustomerFact> customers = new ArrayList<>();
        private final List<ContactFact> contacts = new ArrayList<>();
        private final List<UnitFact> units = new ArrayList<>();
        private final List<LinkFact> links = new ArrayList<>();
        private final List<EconomyFact> economies = new ArrayList<>();
        private final List<RouteFact> routes = new ArrayList<>();
        private final List<BlockFact> blocks = new ArrayList<>();
        private final List<MeterFact> meters = new ArrayList<>();
        private final List<ReadingFact> readings = new ArrayList<>();
        private final List<UndeliveredFact> undelivered = new ArrayList<>();
        private final List<DeathAlertFact> deathAlerts = new ArrayList<>();

        public Builder customer(CustomerFact fact) { customers.add(fact); return this; }
        public Builder contact(ContactFact fact) { contacts.add(fact); return this; }
        public Builder unit(UnitFact fact) { units.add(fact); return this; }
        public Builder link(LinkFact fact) { links.add(fact); return this; }
        public Builder economy(EconomyFact fact) { economies.add(fact); return this; }
        public Builder route(RouteFact fact) { routes.add(fact); return this; }
        public Builder block(BlockFact fact) { blocks.add(fact); return this; }
        public Builder meter(MeterFact fact) { meters.add(fact); return this; }
        public Builder reading(ReadingFact fact) { readings.add(fact); return this; }
        public Builder undelivered(UndeliveredFact fact) { undelivered.add(fact); return this; }
        public Builder deathAlert(DeathAlertFact fact) { deathAlerts.add(fact); return this; }

        public CadastralSnapshot build() {
            return new CadastralSnapshot(customers, contacts, units, links, economies,
                    routes, blocks, meters, readings, undelivered, deathAlerts);
        }
    }
}

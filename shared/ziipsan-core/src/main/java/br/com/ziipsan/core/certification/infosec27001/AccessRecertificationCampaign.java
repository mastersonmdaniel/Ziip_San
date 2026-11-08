package br.com.ziipsan.core.certification.infosec27001;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Campanha periódica de recertificação de acessos POR PAPEL E POR ESCOPO
 * (ED-73), sobre o catálogo de permissões da ED-06 — consumido por PORTA
 * ({@link PermissionCatalogPort}), sem duplicar o catálogo nem a
 * recertificação por usuário que a ED-06 já entrega
 * ({@code RecertificationService} no platform: campanha por usuário, com
 * decisão registrada; esta campanha é a leitura por papel/escopo que o
 * aceite da ED-73 pede como evidência do SGSI).
 *
 * <p>Desenho: a campanha nasce de um retrato do catálogo (um item por
 * concessão papel × escopo × permissão vigente na abertura); cada item
 * recebe a DECISÃO DO GESTOR ({@code KEEP} ou {@code REVOKE}) com decisor,
 * instante e justificativa registrados. Item sem decisão NÃO é mantido
 * por omissão: ao fim, {@link #pendingItems()} o lista como pendente —
 * o mesmo princípio da ED-06. Aplicar a revogação à atribuição é ato do
 * ADMIN na gestão de usuários (fora desta régua, declarado).
 *
 * <p>A porta é uma interface deste pacote de propósito: a fiação injeta
 * um adaptador sobre o {@code PermissionCatalog} real da ED-06
 * ({@code shared/ziipsan-security}) sem que o core passe a depender dele.
 */
public final class AccessRecertificationCampaign {

    /** Porta de leitura do catálogo de permissões da ED-06 (fiação injeta o adaptador). */
    public interface PermissionCatalogPort {
        /** Concessões vigentes do tenant: papel, escopo e permissão de cada uma. */
        List<CatalogGrant> currentGrants();
    }

    /** Uma concessão vigente no catálogo: papel × escopo × permissão (referência, sem cópia). */
    public record CatalogGrant(String role, String scope, String permission) {

        /**
         * Teto dos três campos, compatível com o {@code VARCHAR(120)} do SQL
         * pendente da ED-73 (correção D4 da revisão: concessão maior nascia
         * no Java e quebrava no insert).
         */
        public static final int GRANT_FIELD_MAX = 120;

        public CatalogGrant {
            role = requireGrantField(role, "role");
            scope = requireGrantField(scope, "scope");
            permission = requireGrantField(permission, "permission");
        }

        private static String requireGrantField(String value, String field) {
            if (value == null || value.isBlank() || value.trim().length() > GRANT_FIELD_MAX) {
                throw new IllegalArgumentException(field + " da concessão é obrigatório e tem no "
                        + "máximo " + GRANT_FIELD_MAX + " caracteres (VARCHAR(120) no SQL "
                        + "pendente da ED-73)");
            }
            return value.trim();
        }

        /** Chave do item da campanha: papel|escopo|permissão. */
        public String key() {
            return role + "|" + scope + "|" + permission;
        }
    }

    /** Decisão do gestor sobre um item da campanha. */
    public enum ManagerDecision {
        KEEP, REVOKE
    }

    /** Um item da campanha: a concessão e, quando houver, a decisão registrada. */
    public record Item(CatalogGrant grant, ManagerDecision decision, UUID decidedBy,
                       Instant decidedAt, String note) {

        public Item {
            Objects.requireNonNull(grant, "grant do item é obrigatória");
            if (decision == null) {
                // Pendente é um estado completo, não um item meio-decidido:
                // forjar pendente com dados de decisão é recusado (D1/P6).
                if (decidedBy != null || decidedAt != null
                        || (note != null && !note.isBlank())) {
                    throw new IllegalArgumentException("item pendente (sem decisão) não carrega "
                            + "decisor, instante nem justificativa de decisão");
                }
                note = null;
            } else {
                Objects.requireNonNull(decidedBy, "decidedBy (o gestor) é obrigatório com decisão");
                Objects.requireNonNull(decidedAt, "decidedAt é obrigatório com decisão");
                if (note == null || note.isBlank()) {
                    throw new IllegalArgumentException("decisão registrada exige a justificativa "
                            + "do gestor (o registro é a evidência)");
                }
                note = note.trim();
            }
        }

        /** Item ainda sem decisão do gestor. */
        public boolean pending() {
            return decision == null;
        }
    }

    private final String campaignName;
    private final LocalDate opensOn;
    private final LocalDate dueOn;
    private final Map<String, Item> items = new LinkedHashMap<>();

    private AccessRecertificationCampaign(String campaignName, LocalDate opensOn, LocalDate dueOn,
                                          List<CatalogGrant> grants) {
        this.campaignName = campaignName;
        this.opensOn = opensOn;
        this.dueOn = dueOn;
        for (CatalogGrant grant : grants) {
            Item previous = items.putIfAbsent(grant.key(), new Item(grant, null, null, null, null));
            if (previous != null) {
                throw new IllegalArgumentException("concessão duplicada no retrato do catálogo: "
                        + grant.key());
            }
        }
    }

    /**
     * Abertura da campanha: só com a norma ligada, sobre o retrato atual do
     * catálogo da ED-06 lido pela porta. Catálogo vazio não abre campanha
     * (recertificar nada não é campanha) e prazo não posterior à abertura
     * é recusado.
     */
    public static AccessRecertificationCampaign open(Iso27001Activation activation, String name,
                                                     LocalDate opensOn, LocalDate dueOn,
                                                     PermissionCatalogPort catalog) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a campanha exige nome");
        }
        Objects.requireNonNull(opensOn, "opensOn é obrigatório");
        Objects.requireNonNull(dueOn, "dueOn é obrigatório");
        if (!dueOn.isAfter(opensOn)) {
            throw new IllegalArgumentException("a campanha exige prazo depois da abertura");
        }
        Objects.requireNonNull(catalog, "catalog (porta do catálogo da ED-06) é obrigatório");
        List<CatalogGrant> grants = catalog.currentGrants();
        if (grants == null || grants.isEmpty()) {
            throw new IllegalArgumentException("campanha sem itens não abre: o catálogo de "
                    + "permissões da ED-06 não devolveu concessão vigente para recertificar");
        }
        return new AccessRecertificationCampaign(name.trim(), opensOn, dueOn, List.copyOf(grants));
    }

    /**
     * Registro da decisão do gestor sobre um item (papel × escopo ×
     * permissão). Decisão duplicada, item fora da campanha e decisão sem
     * justificativa são recusados — o registro é a evidência.
     */
    public Item decide(String itemKey, ManagerDecision decision, UUID decidedBy, Instant decidedAt,
                       String note) {
        Objects.requireNonNull(decision, "decision do gestor é obrigatória");
        Objects.requireNonNull(decidedBy, "decidedBy (o gestor) é obrigatório");
        Objects.requireNonNull(decidedAt, "decidedAt é obrigatório");
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("decisão de recertificação exige a justificativa "
                    + "registrada do gestor");
        }
        Item current = items.get(itemKey);
        if (current == null) {
            throw new IllegalArgumentException("item fora desta campanha: " + itemKey);
        }
        if (!current.pending()) {
            throw new IllegalStateException("já há decisão registrada para este item nesta "
                    + "campanha: " + itemKey);
        }
        Item decided = new Item(current.grant(), decision, decidedBy, decidedAt, note.trim());
        items.put(itemKey, decided);
        return decided;
    }

    /** Todos os itens da campanha, na ordem do retrato do catálogo. */
    public List<Item> items() {
        return List.copyOf(items.values());
    }

    /** Itens sem decisão do gestor: NÃO são mantidos por omissão. */
    public List<Item> pendingItems() {
        List<Item> pending = new ArrayList<>();
        for (Item item : items.values()) {
            if (item.pending()) {
                pending.add(item);
            }
        }
        return List.copyOf(pending);
    }

    /** Verdadeiro só quando todo item tem decisão registrada. */
    public boolean fullyDecided() {
        return pendingItems().isEmpty();
    }

    public String campaignName() {
        return campaignName;
    }

    public LocalDate opensOn() {
        return opensOn;
    }

    public LocalDate dueOn() {
        return dueOn;
    }
}

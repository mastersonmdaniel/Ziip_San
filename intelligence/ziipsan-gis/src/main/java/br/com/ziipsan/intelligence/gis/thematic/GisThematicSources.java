package br.com.ziipsan.intelligence.gis.thematic;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fontes dos temáticos da GIS-08: a lista fechada que liga o
 * {@code source_kind} da {@code gis_thematic_map} (V13_13) à view
 * física, ao tipo de geometria e aos campos que a definição pode
 * referenciar. É código, não configuração, pelo mesmo motivo do
 * {@code GisTileLayers} (GIS-06): montar SQL com nome de view ou de
 * coluna vindo do banco ou do corpo da requisição seria injeção por
 * desenho. O serviço só monta SQL a partir daqui.
 *
 * <p>{@code personal = true} marca a fonte ligada a imóvel ou
 * ligação (§12, itens 1 a 3): no backoffice exige
 * {@code gis.view-personal} e fora dele só sai agregada por quadra
 * ou setor com o mínimo de {@code gis.public_aggregation_min_units}.
 * OS é pessoal por posição (o alvo pode ser a unidade) e NUNCA é
 * publicável, nem agregada: é dado operacional interno.
 */
public enum GisThematicSources {

    UNIT_CONSUMPTION("gis_thematic_unit_consumption", true, true, "unit_id",
            Set.of("comparison", "billed_by_average", "zero_consumption"),
            Set.of("billed_consumption", "average_consumption"),
            Set.of("comparison", "consumption_type", "billed_by_average",
                    "zero_consumption", "category_code")),

    CONNECTION_STATUS("gis_thematic_connection_status", true, true, "unit_id",
            Set.of("system_role"),
            Set.of(),
            Set.of("system_role", "kind", "status_code", "category_code")),

    METER_AGE("gis_thematic_meter_age", true, true, "unit_id",
            Set.of("meter_type"),
            Set.of("age_years"),
            Set.of("meter_type", "metrological_class", "category_code")),

    UNIT_OVERDUE("gis_thematic_unit_overdue", true, true, "unit_id",
            Set.of("overdue_group"),
            Set.of("overdue_bills", "total_due"),
            Set.of("overdue_group", "category_code")),

    SERVICE_ORDER("gis_thematic_service_order", true, false, "order_id",
            Set.of("service_type_code"),
            Set.of(),
            Set.of("service_type_code", "status", "statuses",
                    "serviceTypeCodes")),

    NETWORK_ARC("gis_thematic_network_arc", false, true, "feature_id",
            Set.of("lifecycle_status", "domain", "accuracy_status",
                    "operational_status", "network_function", "type_code"),
            Set.of("length_m"),
            Set.of("domain", "lifecycle_status", "operational_status",
                    "accuracy_status", "network_function", "type_code")),

    PRECISION("gis_thematic_precision_feature", false, true, "feature_id",
            Set.of("precision_group"),
            Set.of(),
            Set.of("precision_group", "accuracy_status", "capture_method",
                    "domain", "class_code", "feature_table"));

    private final String viewName;
    private final boolean personal;
    private final boolean publishable;
    private final String idColumn;
    private final Set<String> categoryFields;
    private final Set<String> measureFields;
    private final Set<String> filterFields;

    GisThematicSources(String viewName, boolean personal, boolean publishable,
                       String idColumn, Set<String> categoryFields,
                       Set<String> measureFields, Set<String> filterFields) {
        this.viewName = viewName;
        this.personal = personal;
        this.publishable = publishable;
        this.idColumn = idColumn;
        this.categoryFields = categoryFields;
        this.measureFields = measureFields;
        this.filterFields = filterFields;
    }

    public String viewName() {
        return viewName;
    }

    public boolean personal() {
        return personal;
    }

    /** Fora do backoffice: rede/precisão integral; pessoal só agregado; OS nunca. */
    public boolean publishable() {
        return publishable;
    }

    public String idColumn() {
        return idColumn;
    }

    public boolean isCategoryField(String field) {
        return field != null && categoryFields.contains(field);
    }

    public boolean isMeasureField(String field) {
        return field != null && measureFields.contains(field);
    }

    public boolean isFilterField(String field) {
        return field != null && filterFields.contains(field);
    }

    /** Fontes com unidade (categoria do SG-883 e célula de agregação). */
    public boolean unitBased() {
        return this == UNIT_CONSUMPTION || this == CONNECTION_STATUS
                || this == METER_AGE || this == UNIT_OVERDUE;
    }

    public static Optional<GisThematicSources> byKind(String kind) {
        if (kind == null) {
            return Optional.empty();
        }
        for (GisThematicSources source : values()) {
            if (source.name().equals(kind)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }

    public static List<String> kinds() {
        return List.of(values()).stream().map(Enum::name).toList();
    }
}

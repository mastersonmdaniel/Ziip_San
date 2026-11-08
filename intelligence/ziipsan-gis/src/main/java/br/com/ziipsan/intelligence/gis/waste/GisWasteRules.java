package br.com.ziipsan.intelligence.gis.waste;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.com.ziipsan.intelligence.gis.waste.GisWasteCatalog.WasteType;

/**
 * Regras puras da GIS-14 (§4.5), espelho legível das guardas do banco
 * no SQL pendente da ED-65 (attributes_schema por tipo +
 * CHECKs das tabelas operacionais) — o banco é a autoridade (recusa
 * inclusive por SQL direto); esta classe existe para a api falhar
 * cedo, com o erro certo, e para as regras serem testáveis sem banco.
 *
 * <p>Nenhum valor de negócio é presumido: frequência, executor,
 * licença e capacidade são dados do tenant vindos nos atributos;
 * velocidade média e tempo por parada da estimativa de rota são
 * PARÂMETROS de quem pede a estimativa (parâmetros do tenant na
 * integração) — sem eles, a rota nasce sem estimativa, nunca com um
 * número inventado.
 */
public final class GisWasteRules {

    /**
     * Transições da situação do descarte irregular (§4.5: ativo, em
     * limpeza, eliminado, reincidente). Reincidência é uma NOVA
     * identificação sobre um ponto já eliminado: ELIMINATED ->
     * REINCIDENT (com a data da nova identificação), e daí o ciclo
     * de limpeza recomeça. ELIMINATED nunca volta direto a ACTIVE.
     */
    private static final Map<String, Set<String>> DUMP_TRANSITIONS = Map.of(
            "ACTIVE", Set.of("CLEANING"),
            "CLEANING", Set.of("ELIMINATED", "ACTIVE"),
            "ELIMINATED", Set.of("REINCIDENT"),
            "REINCIDENT", Set.of("CLEANING"));

    private GisWasteRules() {
    }

    public static boolean canTransitionDump(String from, String to) {
        return DUMP_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Valida os atributos de um ponto/unidade de resíduos contra o
     * tipo do §4.5. {@code attributes} é o mapa já lido do JSONB (a
     * validação de formato fino — tipos JSON — é do attributes_schema
     * no banco, pela gis_validate_attributes da V13_2; aqui ficam as
     * regras de domínio: obrigatórios, listas e faixas).
     */
    public static void ensurePointAttributes(WasteType type, Map<String, Object> attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("atributos do ponto de residuos ausentes");
        }
        for (String required : type.requiredAttributes()) {
            Object value = attributes.get(required);
            if (value == null || (value instanceof String s && s.isBlank())
                    || (value instanceof List<?> l && l.isEmpty())) {
                throw new IllegalArgumentException(
                        "atributo obrigatorio do tipo " + type.code() + " ausente: " + required);
            }
        }
        switch (type.code()) {
            case "COLLECTION_POINT" -> {
                requireFraction(attributes.get("waste_fraction"));
                requireShift(attributes.get("shift"));
            }
            case "CONTAINER" -> {
                requireFraction(attributes.get("waste_fraction"));
                String model = asString(attributes.get("container_model"));
                if (!GisWasteCatalog.CONTAINER_VOLUMES_BY_MODEL.containsKey(model)) {
                    throw new IllegalArgumentException(
                            "modelo de conteiner fora do catalogo (§4.5): " + model);
                }
                double volume = asDouble(attributes.get("volume"), "volume");
                if (volume <= 0) {
                    throw new IllegalArgumentException("volume do conteiner deve ser positivo");
                }
                // Fora da lista de referencia NAO e erro: o §4.5 marca os
                // volumes como "valor de referencia a validar" — quem
                // chama decide se avisa (ver isReferenceVolume).
            }
            case "DROP_OFF_POINT" -> {
                requireFractionList(attributes.get("accepted_fractions"), "accepted_fractions");
                String responsible = asString(attributes.get("responsible_party_kind"));
                if (!Set.of("MUNICIPALITY", "REVERSE_LOGISTICS_SYSTEM", "OPERATOR")
                        .contains(responsible)) {
                    throw new IllegalArgumentException(
                            "responsavel do PEV fora da lista (§4.5): " + responsible);
                }
            }
            case "ECOPOINT" -> requireStringList(attributes.get("accepted_materials"),
                    "accepted_materials");
            case "LITTER_BIN" -> {
                double volume = asDouble(attributes.get("volume_l"), "volume_l");
                if (volume <= 0) {
                    throw new IllegalArgumentException("volume da lixeira deve ser positivo");
                }
            }
            case "ILLEGAL_DUMP" -> ensureDumpAttributes(attributes);
            default -> {
                // Unidades sem obrigatorios especificos alem dos do tipo.
            }
        }
        Object wasteClass = attributes.get("waste_class");
        if (wasteClass != null && !GisWasteCatalog.WASTE_CLASSES.contains(asString(wasteClass))) {
            throw new IllegalArgumentException(
                    "classe do residuo fora da NBR 10004:2024 (§4.5): " + wasteClass);
        }
    }

    /** Atributos do descarte irregular (§4.5). */
    public static void ensureDumpAttributes(Map<String, Object> attributes) {
        String kind = asString(attributes.get("dump_kind"));
        if (!GisWasteCatalog.DUMP_KINDS.contains(kind)) {
            throw new IllegalArgumentException("tipo de descarte irregular fora da lista (§4.5): " + kind);
        }
        String status = asString(attributes.get("dump_status"));
        if (!GisWasteCatalog.DUMP_STATUSES.contains(status)) {
            throw new IllegalArgumentException("situacao do descarte irregular fora da lista (§4.5): " + status);
        }
        double volume = asDouble(attributes.get("estimated_volume_m3"), "estimated_volume_m3");
        if (volume < 0) {
            throw new IllegalArgumentException("volume estimado do descarte nao pode ser negativo");
        }
        Object identified = attributes.get("identified_on");
        if (identified == null || asString(identified).isBlank()) {
            throw new IllegalArgumentException("data de identificacao do descarte irregular e obrigatoria");
        }
    }

    /**
     * Foto do descarte irregular: o aceite da GIS-14 pede o ponto
     * "com foto e OS de limpeza" — sem anexo PHOTO ligado ao ponto
     * (gis_attachment), o pedido de limpeza é recusado. A foto é
     * pré-condição do pedido, não do cadastro (o ponto pode nascer
     * de uma denúncia ainda sem foto; ele só não gera OS sem ela).
     */
    public static void ensureCleanupRequestable(String dumpStatus, boolean hasPhoto) {
        if (!hasPhoto) {
            throw new IllegalArgumentException(
                    "ponto de descarte irregular sem foto: OS de limpeza exige a foto (GIS-14)");
        }
        if (!"ACTIVE".equals(dumpStatus) && !"REINCIDENT".equals(dumpStatus)) {
            throw new IllegalArgumentException(
                    "OS de limpeza so nasce de descarte ACTIVE ou REINCIDENT; situacao atual: "
                            + dumpStatus);
        }
    }

    /** Transição de situação do descarte, com a guarda da máquina. */
    public static void ensureDumpTransition(String from, String to) {
        if (!GisWasteCatalog.DUMP_STATUSES.contains(from)
                || !GisWasteCatalog.DUMP_STATUSES.contains(to)) {
            throw new IllegalArgumentException(
                    "situacao de descarte desconhecida: " + from + " -> " + to);
        }
        if (!canTransitionDump(from, to)) {
            throw new IllegalArgumentException(
                    "transicao de situacao do descarte irregular nao permitida: "
                            + from + " -> " + to);
        }
    }

    /** Atributos do setor de coleta (§4.5). */
    public static void ensureSectorAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("atributos do setor de coleta ausentes");
        }
        String kind = asString(attributes.get("collection_kind"));
        if (!GisWasteCatalog.COLLECTION_KINDS.contains(kind)) {
            throw new IllegalArgumentException("tipo de coleta do setor fora da lista (§4.5): " + kind);
        }
        Object days = attributes.get("collection_days");
        if (!(days instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("setor de coleta sem dias de coleta");
        }
        for (Object day : list) {
            if (!GisWasteCatalog.WEEK_DAYS.contains(asString(day))) {
                throw new IllegalArgumentException("dia de coleta fora da lista: " + day);
            }
        }
        requireShift(attributes.get("shift"));
        if (asString(attributes.get("executor")) == null) {
            throw new IllegalArgumentException("setor de coleta sem executor declarado (§4.5)");
        }
    }

    /**
     * Paradas da rota: sequência contígua a partir de 1, sem repetição
     * de ponto, e só tipos coletáveis do §4.5 (descarte irregular NÃO
     * é parada — é OS de limpeza; unidade NÃO é parada).
     */
    public static void ensureRouteStops(List<RouteStopInput> stops) {
        if (stops == null || stops.isEmpty()) {
            throw new IllegalArgumentException("rota de coleta sem paradas");
        }
        java.util.Set<java.util.UUID> seenPoints = new java.util.HashSet<>();
        for (int i = 0; i < stops.size(); i++) {
            RouteStopInput stop = stops.get(i);
            if (stop.sequence() != i + 1) {
                throw new IllegalArgumentException(
                        "sequencia da rota deve ser contigua a partir de 1; posicao "
                                + (i + 1) + " traz " + stop.sequence());
            }
            if (!seenPoints.add(stop.pointId())) {
                throw new IllegalArgumentException(
                        "ponto repetido na rota: " + stop.pointId());
            }
            WasteType type = GisWasteCatalog.requireType(stop.pointTypeCode());
            if (!type.routeStop()) {
                throw new IllegalArgumentException(
                        "tipo " + type.code() + " nao e parada de rota de coleta (§4.5)");
            }
        }
    }

    /** Veículo da rota: só os do catálogo (§4.5) até a frota da ED-59. */
    public static void ensureVehicle(String vehicleKind) {
        if (!GisWasteCatalog.VEHICLE_KINDS.contains(vehicleKind)) {
            throw new IllegalArgumentException(
                    "veiculo fora do catalogo da GIS-14 (§4.5; frota e da ED-59): " + vehicleKind);
        }
    }

    /**
     * Estimativa da rota (sequência, extensão, tempo — linha GIS-14
     * do §13.2): extensão é a soma dos trechos do grafo; o tempo é
     * extensão/velocidade + paradas × tempo por parada. Velocidade e
     * tempo por parada são PARÂMETROS de quem pede (tenant): sem
     * eles, NÃO há estimativa (retorna null) — número nenhum é
     * inventado como padrão.
     */
    public static RouteEstimate estimate(double totalExtensionM, int stopCount,
                                         Double avgSpeedKmh, Double minutesPerStop) {
        if (totalExtensionM < 0) {
            throw new IllegalArgumentException("extensao da rota nao pode ser negativa");
        }
        if (avgSpeedKmh == null || minutesPerStop == null) {
            return null;
        }
        if (avgSpeedKmh <= 0 || minutesPerStop < 0) {
            throw new IllegalArgumentException(
                    "parametros de estimativa invalidos (velocidade > 0, tempo por parada >= 0)");
        }
        double driveMinutes = (totalExtensionM / 1000.0) / avgSpeedKmh * 60.0;
        double totalMinutes = driveMinutes + stopCount * minutesPerStop;
        return new RouteEstimate(totalExtensionM, totalMinutes);
    }

    /**
     * Vínculo imóvel ↔ setor (§4.5/§11: serve à taxa da ED-25): o
     * ponto do imóvel (consumer_units.coordinates) dentro de
     * exatamente UM setor em operação liga; fora de todos, o imóvel
     * fica sem setor (a taxa não nasce de chute); em DOIS ou mais
     * (setores sobrepostos), vai a REVISÃO — sobreposição de setor é
     * erro de desenho a corrigir, não escolha silenciosa.
     */
    public static PropertySectorDecision decidePropertySector(List<java.util.UUID> containingSectorIds) {
        if (containingSectorIds == null || containingSectorIds.isEmpty()) {
            return new PropertySectorDecision(PropertySectorStatus.UNLINKED, null);
        }
        if (containingSectorIds.size() == 1) {
            return new PropertySectorDecision(PropertySectorStatus.LINKED, containingSectorIds.get(0));
        }
        return new PropertySectorDecision(PropertySectorStatus.REVIEW, null);
    }

    public enum PropertySectorStatus { LINKED, UNLINKED, REVIEW }

    public record PropertySectorDecision(PropertySectorStatus status,
                                         java.util.UUID sectorZoneId) {
    }

    /** Parada pedida na criação da rota (antes da sequência do grafo). */
    public record RouteStopInput(java.util.UUID pointId, String pointTypeCode, int sequence) {
    }

    /** Estimativa calculada da rota (extensão em m, tempo em min). */
    public record RouteEstimate(double extensionM, double estimatedMinutes) {
    }

    // ------------------------------------------------------------ helpers

    private static void requireFraction(Object value) {
        String fraction = asString(value);
        if (!GisWasteCatalog.WASTE_FRACTIONS.contains(fraction)) {
            throw new IllegalArgumentException("fracao de residuo fora da lista (§4.5): " + fraction);
        }
    }

    private static void requireFractionList(Object value, String field) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(field + ": ao menos uma fracao aceita e obrigatoria");
        }
        for (Object item : list) {
            requireFraction(item);
        }
    }

    private static void requireStringList(Object value, String field) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(field + ": lista nao vazia e obrigatoria");
        }
        for (Object item : list) {
            if (asString(item) == null) {
                throw new IllegalArgumentException(field + ": item em branco");
            }
        }
    }

    private static void requireShift(Object value) {
        String shift = asString(value);
        if (!GisWasteCatalog.SHIFTS.contains(shift)) {
            throw new IllegalArgumentException("turno fora da lista (§4.5): " + shift);
        }
    }

    static String asString(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    static double asDouble(Object value, String field) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(field + " nao e numero: " + s);
            }
        }
        throw new IllegalArgumentException(field + " numerico e obrigatorio");
    }

    /** Data ISO (yyyy-MM-dd) dos atributos, sem presumir formato. */
    static LocalDate asDate(Object value, String field) {
        String s = asString(value);
        if (s == null) {
            throw new IllegalArgumentException(field + " e obrigatorio");
        }
        try {
            return LocalDate.parse(s);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(field + " fora do formato ISO: " + s);
        }
    }
}

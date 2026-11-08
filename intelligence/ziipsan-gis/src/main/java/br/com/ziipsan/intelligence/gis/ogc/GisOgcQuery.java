package br.com.ziipsan.intelligence.gis.ogc;

import br.com.ziipsan.intelligence.gis.export.GisExportFeature;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Consulta da OGC API – Features partes 1 a 3 (Core, CRS por
 * negociação simples e Filtering/CQL2 básico): {@code bbox},
 * {@code datetime} (instante ou intervalo aberto/fechado),
 * {@code limit} e {@code filter} CQL2-texto RESTRITO a igualdades
 * {@code prop = 'texto'} / {@code prop = numero} ligadas por AND.
 * Operador fora do subconjunto é RECUSADO — nunca ignorado em
 * silêncio, que devolveria um conjunto maior que o pedido.
 */
public record GisOgcQuery(List<Double> bbox, Instant datetimeStart, Instant datetimeEnd,
                          Integer limit, String cqlFilter) {

    public static final int MAX_LIMIT = 10_000;

    public static GisOgcQuery parse(String bbox, String datetime, String limit, String filter) {
        List<Double> box = null;
        if (bbox != null && !bbox.isBlank()) {
            String[] p = bbox.split(",");
            if (p.length != 4) throw new IllegalArgumentException("bbox exige 4 números (minLon,minLat,maxLon,maxLat)");
            box = new ArrayList<>();
            for (String s : p) box.add(Double.parseDouble(s.trim()));
            if (box.get(0) > box.get(2) || box.get(1) > box.get(3))
                throw new IllegalArgumentException("bbox invertido");
        }
        Instant start = null, end = null;
        if (datetime != null && !datetime.isBlank()) {
            if (datetime.contains("/")) {
                String[] p = datetime.split("/", -1);
                if (!p[0].equals("..") && !p[0].isBlank()) start = Instant.parse(p[0]);
                if (p.length > 1 && !p[1].equals("..") && !p[1].isBlank()) end = Instant.parse(p[1]);
            } else start = end = Instant.parse(datetime);
        }
        Integer lim = null;
        if (limit != null && !limit.isBlank()) {
            lim = Integer.parseInt(limit.trim());
            if (lim < 1 || lim > MAX_LIMIT) throw new IllegalArgumentException("limit fora de 1.." + MAX_LIMIT);
        }
        return new GisOgcQuery(box, start, end, lim, filter);
    }

    public Predicate<GisExportFeature> predicate() {
        Predicate<GisExportFeature> pred = f -> true;
        if (bbox != null) {
            pred = pred.and(f -> f.coordinates().stream().anyMatch(c ->
                    c.get(0) >= bbox.get(0) && c.get(0) <= bbox.get(2)
                 && c.get(1) >= bbox.get(1) && c.get(1) <= bbox.get(3)));
        }
        if (datetimeStart != null || datetimeEnd != null) {
            // Conservador e nunca silencioso: com datetime pedido, só
            // passa feição com a propriedade `datetime` (ISO-8601)
            // dentro do intervalo; feição sem a propriedade é excluída.
            pred = pred.and(f -> {
                Object v = f.properties().get("datetime");
                if (v == null) return false;
                Instant t = Instant.parse(String.valueOf(v));
                return (datetimeStart == null || !t.isBefore(datetimeStart))
                    && (datetimeEnd == null || !t.isAfter(datetimeEnd));
            });
        }
        if (cqlFilter != null && !cqlFilter.isBlank()) {
            for (String cond : cqlFilter.split("(?i)\\s+AND\\s+")) pred = pred.and(equality(cond.trim()));
        }
        return pred;
    }

    private static Predicate<GisExportFeature> equality(String cond) {
        var m = java.util.regex.Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*('([^']*)'|-?[0-9]+(\\.[0-9]+)?)$").matcher(cond);
        if (!m.matches()) throw new IllegalArgumentException(
                "filter CQL2 fora do subconjunto suportado (igualdades com AND): " + cond);
        String prop = m.group(1); String lit = m.group(3) != null ? m.group(3) : cond.substring(cond.indexOf('=') + 1).trim();
        return f -> {
            Object v = f.properties().get(prop);
            if (v == null && prop.equals("typeCode")) v = f.typeCode();
            if (v == null && prop.equals("lifecycleStatus")) v = f.lifecycleStatus();
            if (v == null && prop.equals("layer")) v = f.layerKey();
            return v != null && String.valueOf(v).equals(lit);
        };
    }

    public List<GisExportFeature> apply(List<GisExportFeature> in) {
        List<GisExportFeature> out = in.stream().filter(predicate()).toList();
        return limit != null && out.size() > limit ? out.subList(0, limit) : out;
    }
}

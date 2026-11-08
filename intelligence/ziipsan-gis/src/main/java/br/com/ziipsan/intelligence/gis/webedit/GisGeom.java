package br.com.ziipsan.intelligence.gis.webedit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Geometria pura do editor web da GIS-09 (ED-53): ponto, linha e
 * polígono com vértices, mover, rotacionar e escalar (SG-838,
 * SG-839, TR-1026, TR-1027), dividir e unir arcos e inserir nó que
 * segmenta. Sem dependência de JTS/PostGIS: opera sobre coordenadas
 * planas do SRID de trabalho do editor (o serviço persiste em WKT
 * SRID 4674, como a GIS-04). Operações de precisão topológica no
 * banco (snap, ST_Split) são da camada SQL em db/pendente; aqui
 * fica a matemática que a tela e a api compartilham e que é
 * testável sem ambiente (pendência GeoServer/GDAL da ED-67 não
 * bloqueia esta lógica).
 *
 * <p>Convenções: linha é lista ordenada de vértices (mínimo 2);
 * polígono é anel fechado (primeiro = último, mínimo 4 pontos);
 * rotação/escala são em torno de um pivô explícito (a tela usa o
 * centroide); ângulos em graus, anti-horário positivo.
 */
public final class GisGeom {

    /** Vértice 2D (o Z, quando existe, trafega nos atributos/WKT). */
    public record Pt(double x, double y) {
    }

    /** Resultado de dividir um arco: os dois trechos e o nó novo. */
    public record SplitResult(List<Pt> first, List<Pt> second, Pt node) {
    }

    private GisGeom() {
    }

    // ------------------------------------------------------------- WKT

    public static List<Pt> parseLine(String wkt) {
        List<Pt> pts = parseCoords(wkt, "LINESTRING");
        ensureLine(pts);
        return pts;
    }

    public static List<Pt> parsePolygonRing(String wkt) {
        String body = bodyOf(wkt, "POLYGON");
        // POLYGON((anel externo),(anel interno)) — o editor edita o
        // anel externo; furos são preservados pela camada SQL.
        int open = body.indexOf("((");
        int close = open < 0 ? -1 : body.indexOf(')', open + 2);
        if (open < 0 || close < 0) {
            throw new IllegalArgumentException("poligono invalido");
        }
        List<Pt> ring = parsePairs(body.substring(open + 2, close));
        ensureRing(ring);
        return ring;
    }

    public static Pt parsePoint(String wkt) {
        List<Pt> pts = parseCoords(wkt, "POINT");
        if (pts.size() != 1) {
            throw new IllegalArgumentException("ponto invalido");
        }
        return pts.get(0);
    }

    public static String toLineWkt(List<Pt> line) {
        ensureLine(line);
        return "LINESTRING(" + joinPairs(line) + ")";
    }

    public static String toPolygonWkt(List<Pt> ring) {
        ensureRing(ring);
        return "POLYGON((" + joinPairs(ring) + "))";
    }

    public static String toPointWkt(Pt p) {
        return "POINT(" + fmt(p.x()) + " " + fmt(p.y()) + ")";
    }

    // ------------------------------------------------- transformações

    /** Mover: translação de todos os vértices (SG-839). */
    public static List<Pt> translate(List<Pt> shape, double dx, double dy) {
        List<Pt> out = new ArrayList<>(shape.size());
        for (Pt p : shape) {
            out.add(new Pt(p.x() + dx, p.y() + dy));
        }
        return out;
    }

    /** Rotacionar em torno do pivô (TR-1026). */
    public static List<Pt> rotate(List<Pt> shape, Pt pivot, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        List<Pt> out = new ArrayList<>(shape.size());
        for (Pt p : shape) {
            double dx = p.x() - pivot.x();
            double dy = p.y() - pivot.y();
            out.add(new Pt(pivot.x() + dx * cos - dy * sin,
                    pivot.y() + dx * sin + dy * cos));
        }
        return out;
    }

    /** Escalar em torno do pivô; fator deve ser positivo (TR-1027). */
    public static List<Pt> scale(List<Pt> shape, Pt pivot, double factor) {
        if (!(factor > 0)) {
            throw new IllegalArgumentException("fator de escala deve ser positivo");
        }
        List<Pt> out = new ArrayList<>(shape.size());
        for (Pt p : shape) {
            out.add(new Pt(pivot.x() + (p.x() - pivot.x()) * factor,
                    pivot.y() + (p.y() - pivot.y()) * factor));
        }
        return out;
    }

    /** Mover um vértice pelo índice (edição com vértices, SG-838). */
    public static List<Pt> moveVertex(List<Pt> shape, int index, Pt to) {
        if (index < 0 || index >= shape.size()) {
            throw new IllegalArgumentException("vertice fora da geometria");
        }
        List<Pt> out = new ArrayList<>(shape);
        out.set(index, to);
        return out;
    }

    /** Centroide simples (média dos vértices; pivô padrão da tela). */
    public static Pt centroid(List<Pt> shape) {
        if (shape.isEmpty()) {
            throw new IllegalArgumentException("geometria vazia");
        }
        // Anel fechado: o vértice repetido não pesa em dobro.
        int n = shape.size();
        int count = (n > 1 && shape.get(0).equals(shape.get(n - 1))) ? n - 1 : n;
        double sx = 0;
        double sy = 0;
        for (int i = 0; i < count; i++) {
            sx += shape.get(i).x();
            sy += shape.get(i).y();
        }
        return new Pt(sx / count, sy / count);
    }

    // -------------------------------------------------- arcos e nós

    /**
     * Dividir o arco no ponto de divisão (que deve estar SOBRE a
     * linha, dentro da tolerância): devolve os dois trechos, ambos
     * terminando/começando no nó novo. O ponto é projetado no
     * segmento mais próximo; projeção fora de qualquer segmento
     * dentro da tolerância é recusada (a divisão não inventa nó
     * fora do arco). Projeção colada numa extremidade (distância
     * ≤ tolerância) também é recusada: geraria arco de
     * comprimento ~0 (correção D6 da revisão ED-53).
     */
    public static SplitResult splitLineAt(List<Pt> line, Pt at, double tolerance) {
        ensureLine(line);
        if (tolerance < 0) {
            throw new IllegalArgumentException("tolerancia invalida");
        }
        int bestSeg = -1;
        Pt bestProj = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i + 1 < line.size(); i++) {
            Pt proj = projectOnSegment(line.get(i), line.get(i + 1), at);
            double d = dist(at, proj);
            if (d < bestDist) {
                bestDist = d;
                bestSeg = i;
                bestProj = proj;
            }
        }
        if (bestSeg < 0 || bestDist > tolerance) {
            throw new IllegalArgumentException("ponto de divisao fora do arco");
        }
        // Correção D6 (revisão ED-53): divisão colada numa
        // extremidade é RECUSADA — dentro da tolerância, a
        // projeção sobre a extremidade é indistinguível dela, e o
        // trecho resultante teria comprimento ~0 (arco
        // degenerado), quebrando a invariante de linha válida.
        if (dist(line.get(0), bestProj) <= tolerance
                || dist(line.get(line.size() - 1), bestProj) <= tolerance) {
            throw new IllegalArgumentException(
                    "ponto de divisao na extremidade do arco");
        }
        // Divisão exatamente num vértice existente: o nó é o vértice.
        for (int i = 1; i + 1 < line.size(); i++) {
            if (dist(line.get(i), bestProj) <= tolerance) {
                return new SplitResult(
                        List.copyOf(line.subList(0, i + 1)),
                        List.copyOf(line.subList(i, line.size())),
                        line.get(i));
            }
        }
        List<Pt> first = new ArrayList<>(line.subList(0, bestSeg + 1));
        first.add(bestProj);
        List<Pt> second = new ArrayList<>();
        second.add(bestProj);
        second.addAll(line.subList(bestSeg + 1, line.size()));
        return new SplitResult(List.copyOf(first), List.copyOf(second), bestProj);
    }

    /**
     * Inserir nó que segmenta (mesma operação do split, nomeada
     * como a GIS-09 a pede): o nó novo é vértice compartilhado dos
     * dois arcos resultantes — a segmentação é topológica, não só
     * geométrica.
     */
    public static SplitResult insertNodeAt(List<Pt> line, Pt at, double tolerance) {
        return splitLineAt(line, at, tolerance);
    }

    /**
     * Unir dois arcos que partilham uma extremidade (dentro da
     * tolerância): concatena na ordem certa, invertendo o que for
     * preciso, sem duplicar o vértice de junção. Arcos sem
     * extremidade comum são recusados — unir não inventa ligação.
     */
    public static List<Pt> mergeLines(List<Pt> a, List<Pt> b, double tolerance) {
        ensureLine(a);
        ensureLine(b);
        Pt a0 = a.get(0);
        Pt aN = a.get(a.size() - 1);
        Pt b0 = b.get(0);
        Pt bN = b.get(b.size() - 1);
        List<Pt> out = new ArrayList<>();
        if (dist(aN, b0) <= tolerance) {
            out.addAll(a);
            out.addAll(b.subList(1, b.size()));
        } else if (dist(aN, bN) <= tolerance) {
            out.addAll(a);
            List<Pt> rev = reversed(b);
            out.addAll(rev.subList(1, rev.size()));
        } else if (dist(a0, bN) <= tolerance) {
            out.addAll(b);
            out.addAll(a.subList(1, a.size()));
        } else if (dist(a0, b0) <= tolerance) {
            List<Pt> rev = reversed(a);
            out.addAll(rev);
            out.addAll(b.subList(1, b.size()));
        } else {
            throw new IllegalArgumentException("arcos sem extremidade comum");
        }
        return List.copyOf(out);
    }

    public static double length(List<Pt> line) {
        double total = 0;
        for (int i = 0; i + 1 < line.size(); i++) {
            total += dist(line.get(i), line.get(i + 1));
        }
        return total;
    }

    public static double dist(Pt a, Pt b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    // ------------------------------------------------------- internos

    private static Pt projectOnSegment(Pt a, Pt b, Pt p) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) {
            return a;
        }
        double t = ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / len2;
        double clamped = Math.max(0, Math.min(1, t));
        return new Pt(a.x() + clamped * dx, a.y() + clamped * dy);
    }

    private static List<Pt> reversed(List<Pt> line) {
        List<Pt> out = new ArrayList<>(line.size());
        for (int i = line.size() - 1; i >= 0; i--) {
            out.add(line.get(i));
        }
        return out;
    }

    private static void ensureLine(List<Pt> line) {
        if (line == null || line.size() < 2) {
            throw new IllegalArgumentException("linha exige ao menos 2 vertices");
        }
    }

    private static void ensureRing(List<Pt> ring) {
        if (ring == null || ring.size() < 4
                || !ring.get(0).equals(ring.get(ring.size() - 1))) {
            throw new IllegalArgumentException(
                    "anel de poligono exige ao menos 4 pontos e fechamento");
        }
    }

    private static String bodyOf(String wkt, String keyword) {
        if (wkt == null) {
            throw new IllegalArgumentException("WKT ausente");
        }
        String s = wkt.trim().toUpperCase(Locale.ROOT);
        if (!s.startsWith(keyword)) {
            throw new IllegalArgumentException("WKT nao e " + keyword);
        }
        return s;
    }

    private static List<Pt> parseCoords(String wkt, String keyword) {
        String body = bodyOf(wkt, keyword);
        int open = body.indexOf('(');
        int close = body.lastIndexOf(')');
        if (open < 0 || close <= open) {
            throw new IllegalArgumentException("WKT invalido");
        }
        return parsePairs(body.substring(open + 1, close).replace("(", "").replace(")", ""));
    }

    private static List<Pt> parsePairs(String text) {
        List<Pt> out = new ArrayList<>();
        for (String pair : text.split(",")) {
            String[] xy = pair.trim().split("\\s+");
            if (xy.length < 2) {
                throw new IllegalArgumentException("coordenada invalida: " + pair);
            }
            try {
                out.add(new Pt(Double.parseDouble(xy[0]), Double.parseDouble(xy[1])));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("coordenada invalida: " + pair);
            }
        }
        return out;
    }

    private static String joinPairs(List<Pt> pts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(fmt(pts.get(i).x())).append(' ').append(fmt(pts.get(i).y()));
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.7f", v);
    }
}

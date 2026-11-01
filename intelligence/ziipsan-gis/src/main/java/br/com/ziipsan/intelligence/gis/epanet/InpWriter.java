package br.com.ziipsan.intelligence.gis.epanet;

import org.locationtech.jts.geom.Coordinate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Escreve o arquivo de entrada do EPANET (.inp) a partir de {@link EpanetNetwork}.
 *
 * <p>Corrige o escritor anterior, que gerava um arquivo que o EPANET recusaria:
 * <ul>
 *   <li>nós de tubulações, bombas e válvulas vazios (sem topologia);</li>
 *   <li>[PUMPS] sem a palavra-chave {@code POWER} ou {@code HEAD};</li>
 *   <li>[OPTIONS] com palavras que pertencem a [TIMES], e tempos escritos em segundos sem unidade
 *       (o EPANET lê número solto como horas: {@code Duration 86400} seriam 86.400 horas);</li>
 *   <li>diâmetro em metros e rugosidade 0,01 com {@code Headloss H-W}, que espera C ≈ 100-150;</li>
 *   <li>número formatado pelo locale da JVM (vírgula decimal em pt-BR);</li>
 *   <li>reservatórios de nível fixo nunca emitidos, e arquivo sem {@code [END]}.</li>
 * </ul>
 *
 * <p>O arquivo usa {@code Units LPS} (vazão em L/s; comprimento e cota em m; diâmetro em mm) e
 * perda de carga de Hazen-Williams. O escritor <b>não corrige nem completa</b> o modelo: se houver
 * problema, lança {@link InpValidationException} com todos eles.
 */
public final class InpWriter {

    /** Limite de caracteres de um ID no EPANET. */
    private static final int MAX_ID_LENGTH = 31;

    private InpWriter() {
    }

    public static String write(EpanetNetwork net) {
        List<String> issues = validate(net);
        if (!issues.isEmpty()) {
            throw new InpValidationException(issues);
        }

        StringBuilder sb = new StringBuilder(4096);
        EpanetSimulationOptions opts = net.getOptions();

        sb.append("[TITLE]\n");
        sb.append(oneLine(net.getTitle())).append('\n');
        if (!oneLine(net.getDescription()).isEmpty()) {
            sb.append(oneLine(net.getDescription())).append('\n');
        }

        sb.append("\n[JUNCTIONS]\n;ID              \tElev        \tDemand\n");
        for (EpanetJunction j : net.getJunctions()) {
            sb.append(j.getId()).append('\t').append(num(j.getElevation())).append('\t')
                    .append(num(j.getDemand() == null ? BigDecimal.ZERO : j.getDemand())).append('\n');
        }

        sb.append("\n[RESERVOIRS]\n;ID              \tHead\n");
        for (EpanetReservoir r : net.getReservoirs()) {
            sb.append(r.getId()).append('\t').append(num(r.getHead())).append('\n');
        }

        sb.append("\n[TANKS]\n;ID              \tElevation\tInitLevel\tMinLevel\tMaxLevel\tDiameter\tMinVol\n");
        for (EpanetTank t : net.getTanks()) {
            sb.append(t.getId()).append('\t').append(num(t.getElevation())).append('\t')
                    .append(num(t.getInitLevel())).append('\t').append(num(t.getMinLevel())).append('\t')
                    .append(num(t.getMaxLevel())).append('\t').append(num(t.getDiameter())).append('\t')
                    .append(num(t.getMinVolume() == null ? BigDecimal.ZERO : t.getMinVolume())).append('\n');
        }

        sb.append("\n[PIPES]\n;ID              \tNode1\tNode2\tLength\tDiameter\tRoughness\tMinorLoss\tStatus\n");
        for (EpanetPipe p : net.getPipes()) {
            sb.append(p.getId()).append('\t').append(p.getNode1()).append('\t').append(p.getNode2()).append('\t')
                    .append(num(p.getLength(), 3)).append('\t').append(num(p.getDiameter(), 1)).append('\t')
                    .append(num(p.getRoughness(), 1)).append('\t')
                    .append(num(p.getMinorLoss() == null ? BigDecimal.ZERO : p.getMinorLoss())).append('\t')
                    .append(p.getStatus() ? "Open" : "Closed").append('\n');
        }

        sb.append("\n[PUMPS]\n;ID              \tNode1\tNode2\tParameters\n");
        for (EpanetPump p : net.getPumps()) {
            sb.append(p.getId()).append('\t').append(p.getNode1()).append('\t').append(p.getNode2())
                    .append("\tPOWER ").append(num(p.getPower())).append('\n');
        }

        sb.append("\n[VALVES]\n;ID              \tNode1\tNode2\tDiameter\tType\tSetting\tMinorLoss\n");
        for (EpanetValve v : net.getValves()) {
            sb.append(v.getId()).append('\t').append(v.getNode1()).append('\t').append(v.getNode2()).append('\t')
                    .append(num(v.getDiameter(), 1)).append('\t').append(v.getType().name()).append('\t')
                    .append(num(v.getSetting())).append('\t')
                    .append(num(v.getMinorLoss() == null ? BigDecimal.ZERO : v.getMinorLoss())).append('\n');
        }

        sb.append("\n[OPTIONS]\n");
        sb.append("Units\t\t\tLPS\n");
        sb.append("Headloss\t\tH-W\n");
        sb.append("Specific Gravity\t1.0\n");
        sb.append("Viscosity\t\t1.0\n");
        sb.append("Trials\t\t\t").append(opts.getMaxTrials()).append('\n');
        sb.append("Accuracy\t\t").append(num(opts.getAccuracy())).append('\n');
        sb.append("Unbalanced\t\tContinue ").append(opts.getUnbalancedMaxTrials()).append('\n');
        sb.append("Demand Multiplier\t1.0\n");
        sb.append("Emitter Exponent\t").append(num(opts.getEmitterExponent(), 3)).append('\n');
        sb.append("Quality\t\t\tNone\n");
        sb.append("Demand Model\t\t").append(opts.getDemandModel().name()).append('\n');
        sb.append("CHECKFREQ\t\t").append(opts.getCheckFrequency()).append('\n');
        sb.append("DAMPLIMIT\t\t").append(num(opts.getDampingLimit())).append('\n');

        sb.append("\n[TIMES]\n");
        sb.append("Duration\t\t").append(clock(opts.getDuration())).append('\n');
        sb.append("Hydraulic Timestep\t").append(clock(opts.getHydraulicTimestep())).append('\n');
        sb.append("Quality Timestep\t").append(clock(opts.getQualityTimestep())).append('\n');
        sb.append("Pattern Timestep\t").append(clock(opts.getPatternTimestep())).append('\n');
        sb.append("Pattern Start\t\t").append(clock(opts.getPatternStart())).append('\n');
        sb.append("Report Timestep\t\t").append(clock(opts.getReportTimestep())).append('\n');
        sb.append("Report Start\t\t").append(clock(opts.getReportStart())).append('\n');
        sb.append("Start ClockTime\t\t").append(clockOfDay(opts.getStartClockTime())).append('\n');
        sb.append("Statistic\t\t").append(statistic(opts.getStatistic())).append('\n');

        sb.append("\n[COORDINATES]\n;Node            \tX-Coord\tY-Coord\n");
        for (Map.Entry<String, Coordinate> e : net.getNodeCoordinates().entrySet()) {
            sb.append(e.getKey()).append('\t').append(coord(e.getValue().x)).append('\t')
                    .append(coord(e.getValue().y)).append('\n');
        }

        sb.append("\n[VERTICES]\n;Link            \tX-Coord\tY-Coord\n");
        for (Map.Entry<String, List<Coordinate>> e : net.getLinkVertices().entrySet()) {
            for (Coordinate c : e.getValue()) {
                sb.append(e.getKey()).append('\t').append(coord(c.x)).append('\t').append(coord(c.y)).append('\n');
            }
        }

        sb.append("\n[END]\n");
        return sb.toString();
    }

    /** Todos os problemas que impedem um .inp confiável. Lista vazia = pode escrever. */
    public static List<String> validate(EpanetNetwork net) {
        List<String> issues = new ArrayList<>(net.getIssues());

        Set<String> nodeIds = new HashSet<>();
        for (EpanetJunction j : net.getJunctions()) {
            checkId("Nó", j.getId(), nodeIds, issues);
            if (j.getElevation() == null) issues.add("Nó " + j.getId() + " sem cota.");
        }
        for (EpanetReservoir r : net.getReservoirs()) {
            checkId("Reservatório", r.getId(), nodeIds, issues);
            if (r.getHead() == null) issues.add("Reservatório " + r.getId() + " sem carga.");
        }
        for (EpanetTank t : net.getTanks()) {
            checkId("Tanque", t.getId(), nodeIds, issues);
            if (t.getElevation() == null || t.getInitLevel() == null || t.getMinLevel() == null
                    || t.getMaxLevel() == null || t.getDiameter() == null) {
                issues.add("Tanque " + t.getId() + " com dados incompletos.");
            }
        }

        Set<String> linkIds = new HashSet<>();
        for (EpanetPipe p : net.getPipes()) {
            checkId("Tubulação", p.getId(), linkIds, issues);
            checkEnds("Tubulação", p.getId(), p.getNode1(), p.getNode2(), nodeIds, issues);
            if (!(p.getLength() > 0)) issues.add("Tubulação " + p.getId() + " sem comprimento positivo.");
            if (!(p.getDiameter() > 0)) issues.add("Tubulação " + p.getId() + " sem diâmetro positivo.");
            if (!(p.getRoughness() > 0)) issues.add("Tubulação " + p.getId() + " sem rugosidade positiva.");
        }
        for (EpanetPump p : net.getPumps()) {
            checkId("Bomba", p.getId(), linkIds, issues);
            checkEnds("Bomba", p.getId(), p.getNode1(), p.getNode2(), nodeIds, issues);
            if (p.getPower() == null || p.getPower().signum() <= 0) {
                issues.add("Bomba " + p.getId() + " sem potência positiva.");
            }
        }
        for (EpanetValve v : net.getValves()) {
            checkId("Válvula", v.getId(), linkIds, issues);
            checkEnds("Válvula", v.getId(), v.getNode1(), v.getNode2(), nodeIds, issues);
            if (v.getSetting() == null) issues.add("Válvula " + v.getId() + " sem regulagem.");
            if (!(v.getDiameter() > 0)) issues.add("Válvula " + v.getId() + " sem diâmetro positivo.");
        }

        if (net.getReservoirs().isEmpty() && net.getTanks().isEmpty()) {
            issues.add("A rede não tem reservatório nem tanque: sem fonte de carga o EPANET não resolve.");
        }
        if (net.getPipes().isEmpty() && net.getPumps().isEmpty() && net.getValves().isEmpty()) {
            issues.add("A rede não tem nenhuma ligação.");
        }
        if (net.getOptions().getDemandModel() == EpanetSimulationService.DemandModel.PDA) {
            issues.add("Demanda dirigida por pressão (PDA) exige pressão mínima, requerida e expoente, "
                    + "que o modelo ainda não carrega. Use DDA.");
        }
        return issues;
    }

    private static void checkId(String kind, String id, Set<String> seen, List<String> issues) {
        if (id == null || id.isBlank()) {
            issues.add(kind + " sem ID.");
            return;
        }
        if (id.length() > MAX_ID_LENGTH) {
            issues.add(kind + " " + id + ": ID com mais de " + MAX_ID_LENGTH + " caracteres.");
        }
        if (id.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == ';' || ch == '"')) {
            issues.add(kind + " " + id + ": ID com espaço, ';' ou aspas.");
        }
        if (!seen.add(id)) {
            issues.add(kind + " " + id + ": ID repetido.");
        }
    }

    private static void checkEnds(String kind, String id, String n1, String n2, Set<String> nodeIds,
                                  List<String> issues) {
        for (String n : new String[]{n1, n2}) {
            if (n == null || n.isBlank()) {
                issues.add(kind + " " + id + " com nó de ligação vazio.");
            } else if (!nodeIds.contains(n)) {
                issues.add(kind + " " + id + " aponta para o nó inexistente " + n + ".");
            }
        }
    }

    /**
     * Palavra-chave de {@code Statistic} em [TIMES]. O EPANET aceita NONE, AVERAGED, MINIMUM,
     * MAXIMUM e RANGE; o nome do enum do projeto é AVERAGE, e escrever "AVERAGE" faz o parser
     * recusar o arquivo. Descoberto ao validar a amostra contra o EPANET real.
     */
    static String statistic(EpanetSimulationService.Statistic s) {
        return s == EpanetSimulationService.Statistic.AVERAGE ? "AVERAGED" : s.name();
    }

    // ---------- formatação independente de locale ----------

    private static String oneLine(String s) {
        return s == null ? "" : s.replace('\r', ' ').replace('\n', ' ').trim();
    }

    static String num(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    static String num(double v) {
        return num(BigDecimal.valueOf(v));
    }

    /** Número com no máximo {@code scale} casas, sem zeros à direita. */
    static String num(double v, int scale) {
        return num(BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP));
    }

    private static String coord(double v) {
        return num(BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP));
    }

    /** Segundos para H:MM:SS, o formato de tempo que o EPANET lê sem ambiguidade. */
    static String clock(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    /** Segundos desde a meia-noite para o formato de {@code Start ClockTime} (ex.: 12 AM, 6:30 PM). */
    static String clockOfDay(int seconds) {
        int s = ((seconds % 86400) + 86400) % 86400;
        int h = s / 3600;
        int m = (s % 3600) / 60;
        String suffix = h < 12 ? "AM" : "PM";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return m == 0 ? String.format(Locale.ROOT, "%d %s", h12, suffix)
                : String.format(Locale.ROOT, "%d:%02d %s", h12, m, suffix);
    }
}

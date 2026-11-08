package br.com.ziipsan.intelligence.gis.hydraulic.solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executa os binários oficiais compilados das bibliotecas EPANET 2.2
 * ({@code runepanet}, ligado a libepanet2) e SWMM 5.2 ({@code runswmm},
 * ligado a libswmm5), localizados por propriedade de sistema
 * ({@code ziipsan.epanet.bin} / {@code ziipsan.swmm.bin}) ou variável de
 * ambiente ({@code ZIIPSAN_EPANET_BIN} / {@code ZIIPSAN_SWMM_BIN}).
 * O resultado é lido do relatório textual do próprio solver ([REPORT]
 * Nodes/Links All no EPANET; resumos de continuidade/vazão no SWMM) —
 * nenhum número é calculado por este código.
 */
public final class NativeSolverRunner implements SolverPort {

    private final Path epanetBin;
    private final Path swmmBin;
    private final Duration timeout;

    public NativeSolverRunner(Path epanetBin, Path swmmBin) {
        this(epanetBin, swmmBin, Duration.ofMinutes(10));
    }

    public NativeSolverRunner(Path epanetBin, Path swmmBin, Duration timeout) {
        this.epanetBin = epanetBin;
        this.swmmBin = swmmBin;
        this.timeout = timeout;
    }

    /** Resolução padrão: propriedade de sistema, senão variável de ambiente; null = não configurado. */
    public static NativeSolverRunner fromEnvironment() {
        return new NativeSolverRunner(locate("ziipsan.epanet.bin", "ZIIPSAN_EPANET_BIN"),
                locate("ziipsan.swmm.bin", "ZIIPSAN_SWMM_BIN"));
    }

    private static Path locate(String property, String env) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) v = System.getenv(env);
        return v == null || v.isBlank() ? null : Path.of(v);
    }

    public boolean epanetAvailable() {
        return epanetBin != null && Files.isExecutable(epanetBin);
    }

    public boolean swmmAvailable() {
        return swmmBin != null && Files.isExecutable(swmmBin);
    }

    @Override
    public SolverResult runEpanet(String inpContent) {
        if (!epanetAvailable()) throw new SolverUnavailableException(
                "Biblioteca do EPANET 2.2 nao configurada (ziipsan.epanet.bin/ZIIPSAN_EPANET_BIN): nenhum resultado produzido.");
        String rpt = run(epanetBin, inpContent, "epanet");
        if (rpt.contains("EPANET ran successfully") == false && rpt.toLowerCase().contains("error")) {
            throw new SolverExecutionException("EPANET recusou o modelo; relatorio: " + excerpt(rpt));
        }
        Map<String, NodeValue> nodes = new LinkedHashMap<>();
        Map<String, LinkValue> links = new LinkedHashMap<>();
        parseEpanetTables(rpt, nodes, links);
        String version = firstMatch(rpt, Pattern.compile("EPANET Version ([0-9.]+)"));
        List<String> warnings = new ArrayList<>();
        Matcher wm = Pattern.compile("WARNING:.*").matcher(rpt);
        while (wm.find()) warnings.add(wm.group().trim());
        return new SolverResult(SolverKind.EPANET, true, version == null ? "2.2" : version,
                nodes, links, Map.of(), warnings, null, excerpt(rpt));
    }

    @Override
    public SolverResult runSwmm(String inpContent) {
        if (!swmmAvailable()) throw new SolverUnavailableException(
                "Biblioteca do SWMM 5.2 nao configurada (ziipsan.swmm.bin/ZIIPSAN_SWMM_BIN): nenhum resultado produzido.");
        String rpt = run(swmmBin, inpContent, "swmm");
        if (rpt.contains("ERROR")) throw new SolverExecutionException("SWMM recusou o modelo; relatorio: " + excerpt(rpt));
        Double continuity = lastContinuityError(rpt);
        Map<String, Double> runoff = new LinkedHashMap<>();
        Pattern runoffLine = Pattern.compile("^\\s*(\\S+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s*$", Pattern.MULTILINE);
        int runIdx = rpt.indexOf("Subcatchment Runoff Summary");
        if (runIdx >= 0) {
            Matcher mm = runoffLine.matcher(rpt.substring(runIdx));
            while (mm.find()) runoff.put(mm.group(1), Double.parseDouble(mm.group(4)));
        }
        String version = firstMatch(rpt, Pattern.compile("EPA STORM WATER MANAGEMENT MODEL - VERSION ([0-9.]+)"));
        return new SolverResult(SolverKind.SWMM, true, version == null ? "5.2" : version,
                Map.of(), Map.of(), runoff, List.of(), continuity, excerpt(rpt));
    }

    /** Tabelas "Node Results at ..." / "Link Results at ..." do relatório do EPANET (primeiro instante). */
    static void parseEpanetTables(String rpt, Map<String, NodeValue> nodes, Map<String, LinkValue> links) {
        String[] lines = rpt.split("\\R");
        String mode = null;
        boolean headerPassed = false;
        for (String line : lines) {
            if (line.contains("Node Results")) { mode = "N"; headerPassed = false; continue; }
            if (line.contains("Link Results")) { mode = "L"; headerPassed = false; continue; }
            if (mode == null) continue;
            if (line.contains("-----")) { headerPassed = true; continue; }
            if (!headerPassed) continue;
            String t = line.trim();
            if (t.isEmpty()) { mode = null; continue; }
            String[] tok = t.split("\\s+");
            try {
                if (mode.equals("N") && tok.length >= 4) {
                    nodes.putIfAbsent(tok[0], new NodeValue(num(tok[1]), num(tok[2]), num(tok[3])));
                } else if (mode.equals("L") && tok.length >= 4) {
                    links.putIfAbsent(tok[0], new LinkValue(num(tok[1]), num(tok[2]), num(tok[3])));
                }
            } catch (NumberFormatException ignored) {
                // linhas de cabecalho da tabela (Demand/Head/..., Node/L/s/...): ignora a linha, nao a tabela
            }
        }
    }

    private String run(Path bin, String inpContent, String tag) {
        try {
            Path base = Path.of(System.getProperty("ziipsan.hydraulic.workdir", System.getProperty("java.io.tmpdir")));
            Path dir = Files.createTempDirectory(base, "ziipsan-" + tag + "-");
            Path inp = dir.resolve("model.inp");
            Path rpt = dir.resolve("model.rpt");
            Path out = dir.resolve("model.out");
            Files.writeString(inp, inpContent, StandardCharsets.UTF_8);
            Process p = new ProcessBuilder(bin.toString(), inp.toString(), rpt.toString(), out.toString())
                    .redirectErrorStream(true).start();
            if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                throw new SolverExecutionException("Solver " + tag + " excedeu o tempo limite de " + timeout + ".");
            }
            if (!Files.exists(rpt)) throw new SolverExecutionException("Solver " + tag + " nao produziu relatorio (exit=" + p.exitValue() + ").");
            // ISO-8859-1: o relatorio do SWMM/EPANET pode trazer bytes fora do UTF-8; a leitura nunca falha por charset.
            return Files.readString(rpt, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new SolverExecutionException("Falha ao executar o solver " + tag + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolverExecutionException("Execucao do solver " + tag + " interrompida.");
        }
    }

    private static double num(String s) {
        return Double.parseDouble(s);
    }

    private static String firstMatch(String text, Pattern p) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** Ultimo "Continuity Error (%) .... <valor>" do relatorio (o de Flow Routing, no SWMM). */
    private static Double lastContinuityError(String text) {
        Matcher m = Pattern.compile("Continuity Error \\(%\\) \\.+\\s+([-0-9.]+)").matcher(text);
        Double last = null;
        while (m.find()) {
            try { last = Double.parseDouble(m.group(1)); } catch (NumberFormatException ignored) { }
        }
        return last;
    }

    private static String excerpt(String rpt) {
        String t = rpt.strip();
        return t.length() <= 2000 ? t : t.substring(t.length() - 2000);
    }
}

package br.com.ziipsan.intelligence.gis.hydraulic;

import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterArc;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * GIS-12: cenários sobre a foto da rede — cada um devolve uma NOVA foto
 * (a base não é tocada) e as premissas que entram na publicação (N7).
 * Falta de energia e bombeamento fora da ponta atuam sobre as bombas;
 * rompimento e fechamento de válvula/plano de manobra fecham ligações
 * pelo mecanismo do EPANET ([CONTROLS] LINK ... CLOSED AT TIME 0 e
 * Status Closed em tubulação).
 */
public final class HydraulicScenarios {

    private HydraulicScenarios() {
    }

    public enum ScenarioKind {POWER_OUTAGE, PIPE_BREAK, VALVE_CLOSURE, MANEUVER_PLAN, OFF_PEAK_PUMPING}

    public record ScenarioOutcome(ScenarioKind kind, WaterSnapshot snapshot, List<String> premises) {
    }

    public static ScenarioOutcome apply(WaterSnapshot base, ScenarioKind kind, List<String> targetCodes,
                                        String offPeakWindow) {
        List<String> targets = targetCodes == null ? List.of() : targetCodes;
        List<String> controls = new ArrayList<>(base.controls());
        List<WaterArc> arcs = new ArrayList<>(base.arcs());
        List<String> premises = new ArrayList<>();
        switch (kind) {
            case POWER_OUTAGE -> {
                List<String> pumps = arcs.stream().filter(a -> a.kind() == WaterArc.WaterArcKind.PUMP)
                        .map(WaterArc::code).toList();
                if (pumps.isEmpty()) throw new IllegalArgumentException("Cenario de falta de energia sem bomba na rede.");
                for (String p : pumps) controls.add("LINK " + p + " CLOSED AT TIME 0");
                premises.add("Falta de energia: todas as " + pumps.size() + " bomba(s) desligadas desde t=0: " + pumps);
            }
            case PIPE_BREAK -> {
                requireTargets(base, targets, WaterArc.WaterArcKind.PIPE, "rompimento");
                arcs = arcs.stream().map(a -> targets.contains(a.code())
                        ? new WaterArc(a.code(), a.name(), a.kind(), a.node1Code(), a.node2Code(), a.lengthM(),
                        a.diameterMm(), a.materialCode(), a.roughnessOverride(), a.powerKw(), a.valveType(), a.setting(), false)
                        : a).toList();
                premises.add("Rompimento: tubulacao(oes) fechada(s) (Status Closed): " + targets);
            }
            case VALVE_CLOSURE, MANEUVER_PLAN -> {
                if (targets.isEmpty()) throw new IllegalArgumentException("Cenario " + kind + " exige ao menos uma ligacao a fechar.");
                for (String code : targets) {
                    boolean exists = arcs.stream().anyMatch(a -> a.code().equals(code));
                    if (!exists) throw new IllegalArgumentException("Ligacao do cenario inexistente na foto: " + code);
                    controls.add("LINK " + code + " CLOSED AT TIME 0");
                }
                premises.add((kind == ScenarioKind.VALVE_CLOSURE ? "Fechamento de valvula" : "Plano de manobra")
                        + ": ligacoes fechadas em t=0: " + targets);
            }
            case OFF_PEAK_PUMPING -> {
                if (offPeakWindow == null || offPeakWindow.isBlank()) {
                    throw new IllegalArgumentException("Bombeamento fora da ponta exige a janela declarada (ex.: 00:00-05:00).");
                }
                List<String> pumps = arcs.stream().filter(a -> a.kind() == WaterArc.WaterArcKind.PUMP)
                        .map(WaterArc::code).toList();
                if (pumps.isEmpty()) throw new IllegalArgumentException("Bombeamento fora da ponta sem bomba na rede.");
                for (String p : pumps) controls.add("LINK " + p + " CLOSED AT CLOCKTIME " + offPeakWindow.trim().split("-")[0].trim());
                premises.add("Bombeamento fora da ponta: bombas operam na janela declarada " + offPeakWindow
                        + " (fechadas na abertura da janela de ponta registrada em [CONTROLS]); janela e premissa do cenario, nao dado de cadastro.");
            }
        }
        WaterSnapshot out = new WaterSnapshot(base.request(), base.title() + " [" + kind + "]",
                base.nodes(), arcs, base.billedVolumeM3ByNode(), base.billedPeriodDays(), base.materialHwC(), controls);
        return new ScenarioOutcome(kind, out, premises);
    }

    private static void requireTargets(WaterSnapshot base, List<String> targets, WaterArc.WaterArcKind kind, String label) {
        if (targets.isEmpty()) throw new IllegalArgumentException("Cenario de " + label + " exige a tubulacao.");
        for (String code : targets) {
            boolean ok = base.arcs().stream().anyMatch(a -> a.code().equals(code) && a.kind() == kind);
            if (!ok) throw new IllegalArgumentException("Alvo do cenario de " + label + " nao e " + kind + " na foto: " + code);
        }
    }
}

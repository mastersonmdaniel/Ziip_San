package br.com.ziipsan.intelligence.gis.epanet;

import br.com.ziipsan.intelligence.gis.epanet.ElementSpec.Kind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InpWriterTest {

    private static final GeometryFactory GF = new GeometryFactory();
    private Locale previous;

    @BeforeEach
    void useBrazilianLocale() {
        // A vírgula decimal do pt-BR é o que quebrava o arquivo anterior.
        previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(previous);
    }

    private static Map<String, BigDecimal> nums(Object... kv) {
        Map<String, BigDecimal> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], new BigDecimal(kv[i + 1].toString()));
        return m;
    }

    /** W1 (carga 100) — P1 — J1 (cota 90, 2,5 L/s) — P2 — J2 (cota 85, 1 L/s). */
    private static EpanetNetwork validNetwork() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(new ElementSpec("W1", "Poço", Kind.RESERVOIR, GF.createPoint(new Coordinate(-46.0000, -23.0)),
                null, null, null, null, nums("head_m", 100)));
        l.add(new ElementSpec("J1", "Nó 1", Kind.JUNCTION, GF.createPoint(new Coordinate(-46.0010, -23.0)),
                null, null, null, null, nums("elevation_m", 90, "demand_lps", "2.5")));
        l.add(new ElementSpec("J2", "Nó 2", Kind.JUNCTION, GF.createPoint(new Coordinate(-46.0020, -23.0)),
                null, null, null, null, nums("elevation_m", 85, "demand_lps", 1)));
        l.add(new ElementSpec("P1", "Adutora", Kind.PIPE, GF.createLineString(
                new Coordinate[]{new Coordinate(-46.0000, -23.0), new Coordinate(-46.0010, -23.0)}),
                150, new BigDecimal("110"), "PVC", null, new HashMap<>()));
        l.add(new ElementSpec("P2", "Rede", Kind.PIPE, GF.createLineString(
                new Coordinate[]{new Coordinate(-46.0010, -23.0), new Coordinate(-46.0020, -23.0)}),
                100, new BigDecimal("110.5"), "Aço", null, new HashMap<>()));
        return NetworkTopologyBuilder.build("DMC 01 - Centro", "Rede de teste", l);
    }

    @Test
    @DisplayName("rede válida gera todas as seções, termina em [END] e não usa vírgula decimal")
    void validNetworkProducesACompleteFile() {
        String inp = InpWriter.write(validNetwork());

        assertThat(inp).startsWith("[TITLE]\nDMC 01 - Centro\nRede de teste\n");
        for (String section : List.of("[JUNCTIONS]", "[RESERVOIRS]", "[TANKS]", "[PIPES]", "[PUMPS]",
                "[VALVES]", "[OPTIONS]", "[TIMES]", "[COORDINATES]", "[VERTICES]", "[END]")) {
            assertThat(inp).as("seção %s", section).contains("\n" + section + "\n");
        }
        assertThat(inp).endsWith("[END]\n");
        assertThat(inp).as("nenhum número com vírgula decimal").doesNotContain(",");
    }

    @Test
    @DisplayName("nós, reservatório e tubulações saem com unidades do EPANET em LPS")
    void sectionsCarryTheExpectedRows() {
        String inp = InpWriter.write(validNetwork());

        assertThat(inp).contains("J1\t90\t2.5\n");
        assertThat(inp).contains("J2\t85\t1\n");
        assertThat(inp).contains("W1\t100\n");
        // comprimento m, diâmetro mm, C de Hazen-Williams, perda localizada, estado
        assertThat(inp).contains("P1\tW1\tJ1\t110\t150\t140\t0\tOpen\n");
        assertThat(inp).contains("P2\tJ1\tJ2\t110.5\t100\t100\t0\tOpen\n");
    }

    @Test
    @DisplayName("[OPTIONS] e [TIMES] usam as palavras certas e tempo em H:MM:SS, não segundos soltos")
    void optionsAndTimes() {
        String inp = InpWriter.write(validNetwork());

        assertThat(inp).contains("Units\t\t\tLPS\n");
        assertThat(inp).contains("Headloss\t\tH-W\n");
        assertThat(inp).contains("Demand Model\t\tDDA\n");
        // Duration 86400 solto seriam 86.400 horas no EPANET
        assertThat(inp).contains("Duration\t\t24:00:00\n");
        assertThat(inp).contains("Hydraulic Timestep\t1:00:00\n");
        assertThat(inp).contains("Start ClockTime\t\t12 AM\n");
        assertThat(inp).contains("Statistic\t\tNONE\n");
        // palavras de [TIMES] não podem aparecer em [OPTIONS]
        String options = inp.substring(inp.indexOf("[OPTIONS]"), inp.indexOf("[TIMES]"));
        assertThat(options).doesNotContain("Duration").doesNotContain("Timestep").doesNotContain("Statistic");
    }

    @Test
    @DisplayName("[COORDINATES] traz todos os nós, inclusive o reservatório")
    void coordinatesIncludeEveryNode() {
        String inp = InpWriter.write(validNetwork());
        String section = inp.substring(inp.indexOf("[COORDINATES]"), inp.indexOf("[VERTICES]"));

        assertThat(section).contains("W1\t-46\t-23\n").contains("J1\t-46.001\t-23\n").contains("J2\t-46.002\t-23\n");
    }

    @Test
    @DisplayName("bomba usa a palavra-chave POWER")
    void pumpUsesPowerKeyword() {
        EpanetNetwork n = validNetwork();
        EpanetPump pump = new EpanetPump();
        pump.setId("B1");
        pump.setNode1("J1");
        pump.setNode2("J2");
        pump.setPower(new BigDecimal("15.5"));
        n.getPumps().add(pump);

        assertThat(InpWriter.write(n)).contains("\n[PUMPS]\n").contains("B1\tJ1\tJ2\tPOWER 15.5\n");
    }

    @Test
    @DisplayName("válvula de controle sai com tipo e regulagem")
    void valveRow() {
        EpanetNetwork n = validNetwork();
        EpanetValve v = new EpanetValve();
        v.setId("PRV1");
        v.setNode1("J1");
        v.setNode2("J2");
        v.setDiameter(100);
        v.setType(EpanetValve.ValveType.PRV);
        v.setSetting(new BigDecimal("40"));
        n.getValves().add(v);

        assertThat(InpWriter.write(n)).contains("PRV1\tJ1\tJ2\t100\tPRV\t40\t0\n");
    }

    @Test
    @DisplayName("modelo com problemas não vira arquivo: lança com todos os problemas")
    void invalidModelIsRefused() {
        EpanetNetwork n = validNetwork();
        n.getIssues().add("Nó J7 sem cota.");
        n.getPipes().get(0).setNode2("");

        assertThatThrownBy(() -> InpWriter.write(n))
                .isInstanceOf(InpValidationException.class)
                .satisfies(ex -> assertThat(((InpValidationException) ex).getIssues())
                        .anyMatch(s -> s.contains("J7"))
                        .anyMatch(s -> s.contains("P1") && s.contains("nó de ligação vazio")));
    }

    @Test
    @DisplayName("sem reservatório nem tanque o arquivo é recusado")
    void networkWithoutASourceIsRefused() {
        EpanetNetwork n = validNetwork();
        n.getReservoirs().clear();
        n.getPipes().get(0).setNode1("J2"); // evita referência a nó removido; o foco é a falta de fonte

        assertThat(InpWriter.validate(n)).anyMatch(s -> s.contains("não tem reservatório nem tanque"));
    }

    @Test
    @DisplayName("ID repetido, com espaço ou acima de 31 caracteres é recusado")
    void badIds() {
        EpanetNetwork n = validNetwork();
        n.getJunctions().get(0).setId("J 1");
        n.getJunctions().get(1).setId("X".repeat(32));
        EpanetJunction dup = new EpanetJunction();
        dup.setId("W1");
        dup.setElevation(BigDecimal.ONE);
        n.getJunctions().add(dup);

        List<String> issues = InpWriter.validate(n);
        assertThat(issues).anyMatch(s -> s.contains("J 1") && s.contains("espaço"));
        assertThat(issues).anyMatch(s -> s.contains("mais de 31"));
        assertThat(issues).anyMatch(s -> s.contains("W1") && s.contains("repetido"));
    }

    @Test
    @DisplayName("ligação para nó inexistente é recusada")
    void linkToMissingNode() {
        EpanetNetwork n = validNetwork();
        n.getPipes().get(1).setNode2("NAO-EXISTE");
        assertThat(InpWriter.validate(n)).anyMatch(s -> s.contains("P2") && s.contains("NAO-EXISTE"));
    }

    @Test
    @DisplayName("demanda dirigida por pressão (PDA) é recusada até o modelo carregar seus parâmetros")
    void pdaIsRefused() {
        EpanetNetwork n = validNetwork();
        n.getOptions().setDemandModel(EpanetSimulationService.DemandModel.PDA);
        assertThat(InpWriter.validate(n)).anyMatch(s -> s.contains("PDA"));
    }

    /**
     * Grava arquivos de exemplo em target/epanet-samples para o scripts/validate-inp.sh passar pelo
     * EPANET de verdade. Texto que "parece certo" não prova nada: só o solver aceitar prova.
     */
    @Test
    @DisplayName("grava amostras .inp para validação externa pelo EPANET")
    void writesSamplesForExternalValidation() throws Exception {
        java.nio.file.Path dir = java.nio.file.Path.of("target", "epanet-samples");
        java.nio.file.Files.createDirectories(dir);

        java.nio.file.Files.writeString(dir.resolve("rede-simples.inp"), InpWriter.write(validNetwork()));

        // Rede com registro de isolamento, válvula redutora e bomba, todos no meio de tubulações.
        List<ElementSpec> l = new ArrayList<>();
        l.add(new ElementSpec("W1", "Poço", Kind.RESERVOIR, GF.createPoint(new Coordinate(-46.0000, -23.0)),
                null, null, null, null, nums("head_m", 130)));
        l.add(new ElementSpec("J1", "Nó 1", Kind.JUNCTION, GF.createPoint(new Coordinate(-46.0010, -23.0)),
                null, null, null, null, nums("elevation_m", 90, "demand_lps", 2)));
        l.add(new ElementSpec("J2", "Nó 2", Kind.JUNCTION, GF.createPoint(new Coordinate(-46.0020, -23.0)),
                null, null, null, null, nums("elevation_m", 85, "demand_lps", 1)));
        l.add(new ElementSpec("J3", "Nó 3", Kind.JUNCTION, GF.createPoint(new Coordinate(-46.0030, -23.0)),
                null, null, null, null, nums("elevation_m", 80, "demand_lps", 1)));
        l.add(new ElementSpec("P1", "Adutora", Kind.PIPE, GF.createLineString(new Coordinate[]{
                new Coordinate(-46.0000, -23.0), new Coordinate(-46.0010, -23.0)}),
                200, new BigDecimal("102"), "PVC", null, new HashMap<>()));
        l.add(new ElementSpec("P2", "Rede 1", Kind.PIPE, GF.createLineString(new Coordinate[]{
                new Coordinate(-46.0010, -23.0), new Coordinate(-46.0020, -23.0)}),
                150, new BigDecimal("102"), "PVC", null, new HashMap<>()));
        l.add(new ElementSpec("P3", "Rede 2", Kind.PIPE, GF.createLineString(new Coordinate[]{
                new Coordinate(-46.0020, -23.0), new Coordinate(-46.0030, -23.0)}),
                100, new BigDecimal("102"), "PVC", null, new HashMap<>()));
        l.add(new ElementSpec("RG1", "Registro", Kind.VALVE, GF.createPoint(new Coordinate(-46.0005, -23.0)),
                null, null, null, null, nums()));
        l.add(new ElementSpec("PRV1", "Redutora", Kind.VALVE, GF.createPoint(new Coordinate(-46.0015, -23.0)),
                null, null, null, EpanetValve.ValveType.PRV, nums("setting", 25)));
        l.add(new ElementSpec("B1", "Booster", Kind.PUMP, GF.createPoint(new Coordinate(-46.0025, -23.0)),
                null, null, null, null, nums("power_kw", "0.05"))); // ~5 m de carga a 1 L/s
        EpanetNetwork rica = NetworkTopologyBuilder.build("Rede com registro, redutora e bomba", "amostra", l);
        assertThat(rica.getIssues()).isEmpty();
        java.nio.file.Files.writeString(dir.resolve("rede-com-valvulas-e-bomba.inp"), InpWriter.write(rica));
    }

    @Test
    @DisplayName("Statistic AVERAGE é escrito como AVERAGED, a única palavra que o EPANET aceita")
    void statisticKeyword() {
        // Confirmado contra o solver real: "AVERAGE" faz o parser recusar o arquivo.
        assertThat(InpWriter.statistic(EpanetSimulationService.Statistic.AVERAGE)).isEqualTo("AVERAGED");
        assertThat(InpWriter.statistic(EpanetSimulationService.Statistic.MAXIMUM)).isEqualTo("MAXIMUM");
        assertThat(InpWriter.statistic(EpanetSimulationService.Statistic.NONE)).isEqualTo("NONE");

        EpanetNetwork n = validNetwork();
        n.getOptions().setStatistic(EpanetSimulationService.Statistic.AVERAGE);
        assertThat(InpWriter.write(n)).contains("Statistic\t\tAVERAGED\n");
    }

    @Test
    @DisplayName("formatação de tempo")
    void timeFormatting() {
        assertThat(InpWriter.clock(86400)).isEqualTo("24:00:00");
        assertThat(InpWriter.clock(3600)).isEqualTo("1:00:00");
        assertThat(InpWriter.clock(300)).isEqualTo("0:05:00");
        assertThat(InpWriter.clock(0)).isEqualTo("0:00:00");
        assertThat(InpWriter.clockOfDay(0)).isEqualTo("12 AM");
        assertThat(InpWriter.clockOfDay(12 * 3600)).isEqualTo("12 PM");
        assertThat(InpWriter.clockOfDay(18 * 3600 + 1800)).isEqualTo("6:30 PM");
        assertThat(InpWriter.clockOfDay(3 * 3600)).isEqualTo("3 AM");
    }

    @Test
    @DisplayName("formatação numérica independe do locale e não usa notação científica")
    void numberFormatting() {
        assertThat(InpWriter.num(0.0)).isEqualTo("0");
        assertThat(InpWriter.num(100.0)).isEqualTo("100");
        assertThat(InpWriter.num(1.0E-5)).isEqualTo("0.00001");
        assertThat(InpWriter.num(1.0E7)).isEqualTo("10000000");
        assertThat(InpWriter.num(110.456789, 3)).isEqualTo("110.457");
        assertThat(InpWriter.num(new BigDecimal("2.500"))).isEqualTo("2.5");
    }
}

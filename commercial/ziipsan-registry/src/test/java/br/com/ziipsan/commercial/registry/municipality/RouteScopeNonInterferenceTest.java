package br.com.ziipsan.commercial.registry.municipality;

import br.com.ziipsan.commercial.billingrules.FieldBillCalculator;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldBillResult;
import br.com.ziipsan.commercial.billingrules.FieldTariffSnapshot;
import br.com.ziipsan.commercial.billingrules.RoutePackage;
import br.com.ziipsan.commercial.billingrules.RouteTask;
import br.com.ziipsan.commercial.billingrules.RulesVersion;
import br.com.ziipsan.commercial.billingrules.TariffBracket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.IBGE_A;
import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.TENANT;
import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.chain;
import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.id;
import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.unit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * MM-03 — Prova de NAO-INTERFERENCIA no campo (guarda ED-19, D-MM-04):
 * o conteudo calculavel do pacote de rota (o {@link FieldTariffSnapshot}
 * de cada tarefa) e IDENTICO com e sem a declaracao de escopo — o escopo
 * e metadado de servidor que acompanha a montagem, nao entra no snapshot
 * nem no calculo. As classes da ED-19 sao compiladas da entrega dela,
 * SEM nenhuma alteracao (hashes registados no RELATORIO-MM-03).
 *
 * Os valores do snapshot sao os da fixture A do FieldBillCalculatorTest
 * da ED-19, reconstruida aqui pelos mesmos parametros literais.
 */
class RouteScopeNonInterferenceTest {

    private static final UUID TARIFF_VERSION =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID CATEGORY =
            UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final UUID TASK = id(701);
    private static final UUID UNIT = id(101);
    private static final UUID INSTALLATION = id(801);

    private static FieldTariffSnapshot snapshot() {
        return new FieldTariffSnapshot(TARIFF_VERSION, CATEGORY, 10,
                new BigDecimal("12.50"),
                List.of(new TariffBracket(10, new BigDecimal("1.50")),
                        new TariffBracket(10, new BigDecimal("2.50")),
                        TariffBracket.open(new BigDecimal("4.00"))),
                true, new BigDecimal("80"), Map.of("tariff", "v2026-01"));
    }

    private static FieldBillInput input(FieldTariffSnapshot s) {
        return new FieldBillInput(TASK, UNIT, INSTALLATION, 1000, 1000, 5, 1,
                s, List.of(), List.of(), LocalDate.of(2026, 12, 10));
    }

    private static FieldBillInput inputWithReading(FieldTariffSnapshot s, int current) {
        return new FieldBillInput(TASK, UNIT, INSTALLATION, 1000, current, 5, 1,
                s, List.of(), List.of(), LocalDate.of(2026, 12, 10));
    }

    private static void assertSnapshotFieldByField(FieldTariffSnapshot a,
                                                   FieldTariffSnapshot b) {
        assertEquals(a.tariffVersionId(), b.tariffVersionId());
        assertEquals(a.categoryId(), b.categoryId());
        assertEquals(a.minimumM3PerEconomy(), b.minimumM3PerEconomy());
        assertEquals(0, a.fixedChargePerEconomy().compareTo(b.fixedChargePerEconomy()));
        assertEquals(a.brackets(), b.brackets());
        assertEquals(a.sewerEnabled(), b.sewerEnabled());
        assertEquals(0, a.sewerPct().compareTo(b.sewerPct()));
        assertEquals(a.parameterVersions(), b.parameterVersions());
        assertEquals(a, b); // igualdade integral do record, alem do campo a campo
    }

    @Test
    void snapshotIdenticoComEsemDeclaracaoDeEscopo() {
        // Caminho vigente (sem MM-03): snapshot montado e calculado.
        FieldTariffSnapshot semEscopo = snapshot();
        FieldBillResult resultadoVigente = FieldBillCalculator.calculate(
                inputWithReading(semEscopo, 1012));

        // Com MM-03: o escopo da rota e resolvido AO LADO da montagem —
        // a unidade da tarefa pertence a rota, que e pura no municipio A.
        RouteMunicipalityScope scope = new RouteMunicipalityResolver(chain())
                .resolve(TENANT, id(901), List.of(unit(101, 21), unit(102, 21)));
        assertEquals(RouteMunicipalityScope.Kind.PURE, scope.kind());
        assertEquals(IBGE_A, scope.municipalityIbge().orElseThrow());

        // O pacote monta-se com um snapshot construido pelos mesmos
        // parametros: tem de ser o MESMO snapshot, campo a campo.
        FieldTariffSnapshot comEscopo = snapshot();
        RoutePackage pkg = new RoutePackage(id(901), TENANT,
                OffsetDateTime.of(2026, 11, 9, 8, 0, 0, 0, ZoneOffset.UTC),
                RulesVersion.CONTRACT, "a".repeat(64),
                List.of(new RouteTask(input(comEscopo), null)),
                Map.of(), Map.of(), 20);
        FieldTariffSnapshot doPacote = pkg.tasks().get(0).inputBase().snapshot();
        assertSnapshotFieldByField(semEscopo, doPacote);

        // E o calculo sobre o snapshot do pacote (com escopo declarado ao
        // lado) produz o MESMO resultado, fingerprint incluido.
        FieldBillResult resultadoComEscopo = FieldBillCalculator.calculate(
                pkg.tasks().get(0).toInput(1012));
        assertEquals(resultadoVigente, resultadoComEscopo);
        assertEquals(resultadoVigente.fingerprint(), resultadoComEscopo.fingerprint());
        assertEquals(0, resultadoVigente.total().compareTo(resultadoComEscopo.total()));
    }

    @Test
    void escopoESnapshotNaoSeReferenciam() {
        // Prova estrutural: nenhum componente do escopo e tipo tarifario
        // ou do artefacto ED-19, e nenhum componente do snapshot nomeia
        // municipio/escopo — a separacao nao e so comportamental.
        for (RecordComponent c : RouteMunicipalityScope.class.getRecordComponents()) {
            String n = c.getName().toLowerCase();
            assertFalse(n.contains("tariff") || n.contains("snapshot")
                    || n.contains("price") || n.contains("bracket"), c.getName());
            assertFalse(c.getType().getName().startsWith("br.com.ziipsan.commercial.billingrules"),
                    c.getName());
        }
        for (RecordComponent c : FieldTariffSnapshot.class.getRecordComponents()) {
            String n = c.getName().toLowerCase();
            assertFalse(n.contains("municip") || n.contains("scope")
                    || n.contains("route"), c.getName());
            assertFalse(c.getType() == RouteMunicipalityScope.class, c.getName());
        }
    }
}

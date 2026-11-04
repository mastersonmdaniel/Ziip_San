package br.com.ziipsan.commercial.registry.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Motor em memória: carga, ensaio, conciliação, aplicação e idempotência,
 * com aplicador e gateway falsos. O caminho JDBC real (JdbcLegacyStagingStore,
 * JdbcLegacyUnitApplier, ServiceOrderBatchGateway) está implementado mas ainda
 * sem IT próprio — fica como pendência explícita da integração, não como
 * prova: nenhum teste aqui o executa.
 *
 * <p>Dados 100% sintéticos: nomes "Fulano Sintético N", CPF de teste
 * 111.444.777-35 e CNPJ alfanumérico de exemplo da documentação técnica
 * (12.ABC.345/01DE-35). Nenhum documento bruto pode aparecer no staging:
 * o teste de privacidade abaixo varre unidade, issues e máscara atrás
 * dele.
 */
class LegacyImportServiceTest {

    private static final String VALID_CPF = "111.444.777-35";
    private static final String VALID_CNPJ_ALFA = "12.abc.345/01de-35";

    private static String mappingJson(String path) {
        try (InputStream in = LegacyImportServiceTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class FakeApplier implements LegacyUnitApplier {
        final List<String> appliedMatriculas = new ArrayList<>();

        @Override
        public Outcome apply(StagedUnit unit, UUID actorId) {
            appliedMatriculas.add(unit.matricula());
            return new Outcome(UUID.randomUUID(), List.of());
        }
    }

    private static final class FakeGateway implements LegacyBatchOrderGateway {
        int ordersOpened;

        @Override
        public OpenedOrder openBatchOrder(LegacyBatch batch, List<StagedUnit> appliedUnits, UUID actorId) {
            ordersOpened++;
            return new OpenedOrder(UUID.randomUUID(), "CLOSED_EXECUTED");
        }
    }

    private record Fixture(InMemoryLegacyStagingStore store, LegacyImportService service,
                           FakeApplier applier, FakeGateway gateway,
                           LegacyMapping piloto, LegacyMapping gsan) {
    }

    private static Fixture fixture() {
        InMemoryLegacyStagingStore store = new InMemoryLegacyStagingStore();
        FakeApplier applier = new FakeApplier();
        FakeGateway gateway = new FakeGateway();
        LegacyImportService service = new LegacyImportService(store, applier, gateway);
        return new Fixture(store, service, applier, gateway,
                LegacyMapping.parse(mappingJson("/legacy/mappings/piloto-v1.json")),
                LegacyMapping.parse(mappingJson("/legacy/mappings/gsan-v1.json")));
    }

    private static final String PILOTO_CSV = """
            matricula_legada;matricula;localidade;setor;quadra;rota;categoria;lote;sublote;sequencia_leitura;saldo;dt_ligacao;titular;cpf_cnpj
            LEG-1;910001;1;10;5;20;RES;10;0;1;1.234,56;05/03/2020;Fulano Sintético 1;111.444.777-35
            LEG-2;910002;1;10;5;20;COM;11;0;2;100,00;06/03/2020;Empresa Sintética Alfa;12.abc.345/01de-35
            LEG-3;910003;1;20;6;30;RES;12;0;3;0,00;07/03/2020;Fulano Sintético 3;111.444.777-36
            """;

    private static final String GSAN_SQL = """
            INSERT INTO imovel (cod_imovel, matricula, cod_localidade, cod_setor, cod_quadra, cod_rota, categoria, numero_lote, numero_sublote, vl_saldo, data_ligacao, titular, doc_titular) VALUES
              ('GS-1', '920001', '1', '10', '5', '20', 'RES', 20, 0, 500.00, '2020-04-01', 'Fulano Sintético 9', '11144477735');
            """;

    private static final String PILOTO_INSTALLMENTS_CSV = """
            matricula_legada;acordo;parcela;vencimento;valor
            LEG-1;ACORDO-7;1;10/04/2026;200,00
            LEG-1;ACORDO-7;2;10/05/2026;200,00
            LEG-9;ACORDO-9;1;10/04/2026;50,00
            """;

    private static LegacyImportService.LoadOutcome loadPiloto(Fixture fixture, String key) {
        return fixture.service().load(new LegacyImportService.LoadRequest(fixture.piloto(),
                new CsvLegacyReader(PILOTO_CSV, ';'), "CSV", key, UUID.randomUUID()));
    }

    @Test
    @DisplayName("dois mapeamentos, um motor: CSV do piloto e dump do GSAN encenam no mesmo formato")
    void twoMappingsOneEngine() {
        Fixture fixture = fixture();
        LegacyImportService.LoadOutcome piloto = loadPiloto(fixture, "carga-piloto-1");
        LegacyImportService.LoadOutcome gsan = fixture.service().load(
                new LegacyImportService.LoadRequest(fixture.gsan(),
                        new SqlDumpLegacyReader(GSAN_SQL, "imovel"),
                        "SQL_DUMP", "carga-gsan-1", UUID.randomUUID()));
        assertThat(piloto.stagedUnits()).isEqualTo(3);
        assertThat(gsan.stagedUnits()).isEqualTo(1);
        assertThat(piloto.batch().sourceSystem()).isEqualTo("PILOTO");
        assertThat(gsan.batch().sourceSystem()).isEqualTo("GSAN");
        assertThat(piloto.batch().id()).isNotEqualTo(gsan.batch().id());
    }

    @Test
    @DisplayName("recarga da mesma chave devolve o lote existente sem duplicar")
    void idempotentReload() {
        Fixture fixture = fixture();
        LegacyImportService.LoadOutcome first = loadPiloto(fixture, "carga-piloto-1");
        LegacyImportService.LoadOutcome second = loadPiloto(fixture, "carga-piloto-1");
        assertThat(second.alreadyExisted()).isTrue();
        assertThat(second.batch().id()).isEqualTo(first.batch().id());
        assertThat(second.stagedUnits()).isEqualTo(3);
    }

    @Test
    @DisplayName("ensaio não aplica nem abre OS; aplicação aplica só as válidas e abre uma OS")
    void dryRunWritesNothingApplyWritesOnce() {
        Fixture fixture = fixture();
        LegacyImportService.LoadOutcome load = loadPiloto(fixture, "carga-piloto-1");
        LegacyReconciliation.LegacyTotals totals = new LegacyReconciliation.LegacyTotals(
                Map.of("10", 2L, "20", 1L), Map.of("20", 2L, "30", 1L), Map.of("RES", 2L, "COM", 1L),
                133456L, Map.of("10", 133456L, "20", 0L));
        LegacyImportService.DryRunReport dryRun = fixture.service().dryRun(load.batch().id(), totals);
        assertThat(dryRun.reconciliation().allMatch()).isTrue();
        assertThat(dryRun.applicableUnits()).isEqualTo(2);
        assertThat(dryRun.rejectedUnits()).isEqualTo(1);
        assertThat(fixture.applier().appliedMatriculas).isEmpty();
        assertThat(fixture.gateway().ordersOpened).isZero();

        LegacyImportService.ApplyOutcome applied =
                fixture.service().apply(load.batch().id(), UUID.randomUUID());
        assertThat(applied.appliedNew()).isEqualTo(2);
        assertThat(applied.rejected()).isEqualTo(1);
        assertThat(applied.orderId()).isNotNull();
        assertThat(fixture.applier().appliedMatriculas).containsExactlyInAnyOrder("910001", "910002");
        assertThat(fixture.gateway().ordersOpened).isEqualTo(1);

        LegacyImportService.ApplyOutcome again =
                fixture.service().apply(load.batch().id(), UUID.randomUUID());
        assertThat(again.alreadyApplied()).isTrue();
        assertThat(fixture.gateway().ordersOpened).isEqualTo(1);
    }

    @Test
    @DisplayName("totais divergentes do legado reprovam a conciliação")
    void mismatchedTotalsFailReconciliation() {
        Fixture fixture = fixture();
        LegacyImportService.LoadOutcome load = loadPiloto(fixture, "carga-piloto-1");
        LegacyReconciliation.LegacyTotals wrong = new LegacyReconciliation.LegacyTotals(
                Map.of("10", 99L), Map.of(), Map.of(), 1L, Map.of());
        assertThat(fixture.service().dryRun(load.batch().id(), wrong).reconciliation().allMatch()).isFalse();
    }

    @Test
    @DisplayName("connected_on vai ao staging e o documento alfanumérico é aceito")
    void connectedOnAndAlphanumericCnpj() {
        Fixture fixture = fixture();
        LegacyImportService.LoadOutcome load = loadPiloto(fixture, "carga-piloto-1");
        List<StagedUnit> units = fixture.store().listUnits(load.batch().id());
        assertThat(units).hasSize(3);
        assertThat(units.get(0).connectedOn().toString()).isEqualTo("2020-03-05");
        assertThat(units.get(0).balanceCents()).isEqualTo(123456L);
        assertThat(units.get(1).holderDocumentKind()).isEqualTo("CNPJ");
        assertThat(units.get(1).holderDocumentValid()).isTrue();
        assertThat(units.get(1).hasErrors()).isFalse();
        assertThat(units.get(2).holderDocumentValid()).isFalse();
        assertThat(units.get(2).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("prestações encenadas com referência ao acordo; sem unidade, recusadas")
    void installmentsStagedWithAgreementRef() {
        Fixture fixture = fixture();
        LegacyImportService.LoadOutcome load = loadPiloto(fixture, "carga-piloto-1");
        LegacyImportService.LoadInstallmentsOutcome outcome = fixture.service().loadInstallments(
                load.batch().id(), fixture.piloto(), new CsvLegacyReader(PILOTO_INSTALLMENTS_CSV, ';'));
        assertThat(outcome.accepted()).isEqualTo(2);
        assertThat(outcome.rejectedRows()).hasSize(1);
        assertThat(outcome.rejectedRows().get(0).issues()).anyMatch(
                issue -> issue.code().equals("REFERENCE_UNKNOWN"));
        List<StagedInstallment> staged = fixture.store().listInstallments(load.batch().id());
        assertThat(staged).hasSize(2);
        assertThat(staged).allMatch(inst -> inst.legacyAgreementRef().equals("ACORDO-7"));
        assertThat(staged).extracting(StagedInstallment::unitLegacyKey).containsOnly("LEG-1");
        assertThat(staged).extracting(StagedInstallment::amountCents).containsExactly(20000L, 20000L);
    }

    @Test
    @DisplayName("chave legada duplicada no arquivo é recusada na carga, não encenada")
    void duplicateLegacyKeyRejectedAtLoad() {
        Fixture fixture = fixture();
        String csv = """
                matricula_legada;matricula;setor;rota;categoria;saldo
                LEG-1;910001;10;20;RES;10,00
                LEG-1;910099;10;20;RES;20,00
                """;
        LegacyImportService.LoadOutcome load = fixture.service().load(
                new LegacyImportService.LoadRequest(fixture.piloto(),
                        new CsvLegacyReader(csv, ';'), "CSV", "carga-dup-1", UUID.randomUUID()));
        assertThat(load.stagedUnits()).isEqualTo(1);
        assertThat(load.rejectedRows()).hasSize(1);
        assertThat(load.rejectedRows().get(0).issues()).anyMatch(
                issue -> issue.code().equals("DUPLICATE_LEGACY_KEY"));
    }

    @Test
    @DisplayName("linha sem chave legada é recusada na carga")
    void missingLegacyKeyRejectedAtLoad() {
        Fixture fixture = fixture();
        String csv = """
                matricula_legada;matricula;setor;rota;categoria;saldo
                ;910001;10;20;RES;10,00
                """;
        LegacyImportService.LoadOutcome load = fixture.service().load(
                new LegacyImportService.LoadRequest(fixture.piloto(),
                        new CsvLegacyReader(csv, ';'), "CSV", "carga-nokey-1", UUID.randomUUID()));
        assertThat(load.stagedUnits()).isZero();
        assertThat(load.rejectedRows()).hasSize(1);
    }

    @Test
    @DisplayName("privacidade: nenhum campo do staging nem issue carrega o documento bruto ou normalizado")
    void noRawDocumentInStaging() {
        Fixture fixture = fixture();
        LegacyImportService.LoadOutcome load = loadPiloto(fixture, "carga-priv-1");
        List<String> forbidden = List.of(VALID_CPF, "11144477735",
                VALID_CNPJ_ALFA, "12ABC34501DE35", "111.444.777-36", "11144477736");
        for (StagedUnit unit : fixture.store().listUnits(load.batch().id())) {
            String dump = String.join("|",
                    String.valueOf(unit.legacyKey()), String.valueOf(unit.matricula()),
                    String.valueOf(unit.holderName()), String.valueOf(unit.holderDocumentKind()),
                    String.valueOf(unit.holderDocumentMasked()), String.valueOf(unit.holderDocumentValid()));
            for (String secret : forbidden) {
                assertThat(dump).doesNotContain(secret);
            }
            for (LegacyIssue issue : unit.issues()) {
                for (String secret : forbidden) {
                    assertThat(issue.message()).doesNotContain(secret);
                }
            }
        }
    }
}

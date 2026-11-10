package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billing.catalog.ChargeVersion;
import br.com.ziipsan.commercial.billing.math.Money;
import br.com.ziipsan.commercial.billing.servicecatalog.RevenueNature;
import br.com.ziipsan.commercial.billing.tariff.SewerFactorPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.COMPETENCE;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.FROM;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.TO;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.companyScopes;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.definition;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.fixedResolution;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.sewerResolution;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.waterResolution;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INV-C2-06 (estendida pelo adendo D-04) e INV-C2-07: snapshot completo
 * — 100% das linhas com {@code chargeVersionId} e municipio do escopo
 * resolvido por linha, municipio da UC no cabecalho,
 * {@code parameterVersionIds} herdados — e gravacao obrigatoria na
 * emissao via porta (emitir sem snapshot = falha).
 */
class ChargeSnapshotDocumentTest {

    static final String MUNICIPALITY = "4200001";
    static final List<UUID> PARAMETER_IDS = List.of(UUID.randomUUID(), UUID.randomUUID());

    static ChargeResolution municipalFixedResolution() {
        ChargeDefinition def = definition("TFDI", CalculationModel.FIXED_PER_UNIT, MUNICIPALITY);
        ChargeVersion version = new ChargeVersion(UUID.randomUUID(), def.id(), 1,
                LocalDate.of(2026, 1, 1), null, CalculationModel.FIXED_PER_UNIT, null, null, null,
                new ChargeVersion.ChargeParameters(null, d("99.00"), null));
        return new ChargeResolution(def, version);
    }

    static ChargeCalculationContext context() {
        return ChargeCalculationContext.builder()
                .unitRef("UC-FIXTURE").billReference("BILL-FIXTURE-1")
                .municipalityIbge(MUNICIPALITY).competence(COMPETENCE)
                .schedule(t1Schedule()).periodFrom(FROM).periodTo(TO).groups(List.of(res(1)))
                .waterConsumption(27)
                .sewer(new BillInput.SewerBilling(27,
                        new SewerParameters(d("80"), d("100"), null, SewerFactorPolicy.EXACT, false)))
                .fixedPartValue(d("25.00")).fixedPartChargeCode("TFDI")
                .resolvedCharges(List.of(waterResolution(), sewerResolution(), municipalFixedResolution()))
                .tariffVersionScopes(companyScopes())
                .parameterVersionIds(PARAMETER_IDS)
                .build();
    }

    @Test
    @DisplayName("INV-C2-06/D-04: toda a linha da composicao tem entrada no snapshot, com a versao usada")
    void snapshotCoversEveryLineWithVersion() {
        ChargeCalculationContext ctx = context();
        BillComposition composition = BillComposer.compose(ctx);
        ChargeSnapshotDocument document = ChargeSnapshotDocument.fromComposition(ctx, composition);
        assertThat(document.lines()).hasSize(composition.lines().size());
        assertThat(document.lines()).hasSize(3);
        for (int i = 0; i < composition.lines().size(); i++) {
            BillChargeLine line = composition.lines().get(i);
            ChargeSnapshotDocument.Line snapshotLine = document.lines().get(i);
            assertThat(snapshotLine.chargeCode()).isEqualTo(line.chargeCode());
            assertThat(snapshotLine.chargeVersionId()).isEqualTo(line.chargeVersionId());
            assertThat(snapshotLine.owner()).isEqualTo(line.owner());
            assertThat(snapshotLine.nature()).isEqualTo(line.revenueNature());
            assertThat(snapshotLine.model()).isEqualTo(line.model());
            assertThat(snapshotLine.amount()).isEqualTo(line.amount());
        }
    }

    @Test
    @DisplayName("D-04: municipio por linha = escopo RESOLVIDO da definicao vencedora; cabecalho = municipio da UC")
    void snapshotCarriesResolvedScopePerLineAndHeader() {
        ChargeCalculationContext ctx = context();
        ChargeSnapshotDocument document = ChargeSnapshotDocument.fromComposition(ctx,
                BillComposer.compose(ctx));
        assertThat(document.municipalityIbge()).isEqualTo(MUNICIPALITY);
        assertThat(document.lines())
                .filteredOn(l -> l.chargeCode().equals("TFDI"))
                .singleElement()
                .satisfies(l -> assertThat(l.municipalityIbge()).isEqualTo(MUNICIPALITY));
        assertThat(document.lines())
                .filteredOn(l -> l.chargeCode().equals("AGUA"))
                .singleElement()
                .satisfies(l -> assertThat(l.municipalityIbge()).isNull());
        assertThat(document.toJson()).contains("\"municipalityIbge\":\"" + MUNICIPALITY + "\"");
    }

    @Test
    @DisplayName("snapshot herda os parameterVersionIds do mecanismo vigente e os totais da composicao")
    void snapshotInheritsParameterVersionIdsAndTotals() {
        ChargeCalculationContext ctx = context();
        BillComposition composition = BillComposer.compose(ctx);
        ChargeSnapshotDocument document = ChargeSnapshotDocument.fromComposition(ctx, composition);
        assertThat(document.parameterVersionIds()).isEqualTo(PARAMETER_IDS);
        assertThat(document.ownSubtotal()).isEqualTo(composition.ownSubtotal());
        assertThat(document.thirdPartyTotal()).isEqualTo(composition.thirdPartyTotal());
        assertThat(document.total()).isEqualTo(composition.total());
        assertThat(document.billReference()).isEqualTo("BILL-FIXTURE-1");
        assertThat(document.competence()).isEqualTo(COMPETENCE);
    }

    @Test
    @DisplayName("INV-C2-06: linha sem chargeVersionId torna o snapshot incompleto = falha fechada")
    void lineWithoutVersionFailsSnapshot() {
        BillChargeLine orphan = new BillChargeLine("AGUA", null, ChargeOwner.OWN,
                RevenueNature.TARIFF, CalculationModel.PROGRESSIVE_BY_CONSUMPTION, d("112.00"),
                null, null);
        BillComposition composition = new BillComposition(List.of(orphan), null, null,
                d("112.00"), Money.ZERO, Money.ZERO, d("112.00"), Money.ZERO, List.of(),
                Money.ZERO, List.of(), Money.ZERO, Money.ZERO, List.of(), Money.ZERO, d("112.00"));
        assertThatThrownBy(() -> ChargeSnapshotDocument.fromComposition(context(), composition))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INV-C2-06");
    }

    /** Porta falsa de teste: regista o documento gravado, sem persistir nada. */
    static final class RecordingWriter implements ChargeSnapshotWriter {
        final List<ChargeSnapshotDocument> written = new ArrayList<>();

        @Override
        public void write(ChargeSnapshotDocument document) {
            written.add(document);
        }
    }

    @Test
    @DisplayName("INV-C2-07: emissao com porta falsa grava o snapshot exactamente uma vez")
    void emissionWritesSnapshotThroughPort() {
        ChargeCalculationContext ctx = context();
        ChargeSnapshotDocument document = ChargeSnapshotDocument.fromComposition(ctx,
                BillComposer.compose(ctx));
        RecordingWriter writer = new RecordingWriter();
        ChargeSnapshotWriter.requireAndWrite(writer, document);
        assertThat(writer.written).containsExactly(document);
    }

    @Test
    @DisplayName("INV-C2-07: emitir sem snapshot (porta ausente) = falha, nunca emissao silenciosa")
    void emissionWithoutWriterFails() {
        ChargeCalculationContext ctx = context();
        ChargeSnapshotDocument document = ChargeSnapshotDocument.fromComposition(ctx,
                BillComposer.compose(ctx));
        assertThatThrownBy(() -> ChargeSnapshotWriter.requireAndWrite(null, document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INV-C2-07");
    }
}

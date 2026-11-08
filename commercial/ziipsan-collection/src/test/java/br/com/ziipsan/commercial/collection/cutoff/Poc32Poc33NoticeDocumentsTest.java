package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.core.cutoff.CutoffEnums;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POC-32 (gerar e imprimir notificacoes para unidades cortadas, ao
 * menos 2 emissoes) e POC-33 (consultar por filtros e reimprimir com os
 * dados originais) do roteiro VISAN, alinea j.
 */
class Poc32Poc33NoticeDocumentsTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant ISSUED_AT = Instant.parse("2026-11-16T13:00:00Z"); // seg 10h em Sao Paulo

    @Test
    @DisplayName("POC-32: duas unidades cortadas geram duas notificacoes pelo motor de modelos; a ativa nao gera, com motivo")
    void poc32GeneratesForCutUnits() {
        var templates = new CutoffExtTestSupport.FakeTemplates();
        var cut1 = new CutoffNoticeDocuments.CutUnit(UUID.randomUUID(),
                CutoffEnums.ConnectionStatus.CUT, Map.of("unitRef", "UC-1"));
        var cut2 = new CutoffNoticeDocuments.CutUnit(UUID.randomUUID(),
                CutoffEnums.ConnectionStatus.CUT, Map.of("unitRef", "UC-2"));
        var active = new CutoffNoticeDocuments.CutUnit(UUID.randomUUID(),
                CutoffEnums.ConnectionStatus.ACTIVE, Map.of("unitRef", "UC-3"));
        var batch = CutoffNoticeDocuments.generateForCutUnits(TENANT, List.of(cut1, cut2, active),
                templates, CutoffExtTestSupport.extParams(), ISSUED_AT);
        assertThat(batch.issued()).hasSize(2);
        assertThat(batch.issued()).extracting(CutoffNoticeDocuments.IssuedNotice::templateCode)
                .containsOnly("NOTIFICACAO_POS_CORTE");
        assertThat(batch.issued()).extracting(CutoffNoticeDocuments.IssuedNotice::templateVersion)
                .containsOnly("v7");
        assertThat(batch.notGenerated()).hasSize(1);
        assertThat(batch.notGenerated().get(0).unitId()).isEqualTo(active.unitId());
        assertThat(batch.notGenerated().get(0).reason()).isEqualTo("CONNECTION_NOT_CUT");
        assertThat(templates.renderedCodes).containsExactly("NOTIFICACAO_POS_CORTE", "NOTIFICACAO_POS_CORTE");
    }

    @Test
    @DisplayName("POC-33: consulta por unidade, periodo e modelo; reimpressao devolve os dados ORIGINAIS sem re-renderizar")
    void poc33QueryAndFrozenReprint() {
        var templates = new CutoffExtTestSupport.FakeTemplates();
        var u1 = UUID.randomUUID();
        var u2 = UUID.randomUUID();
        var batch = CutoffNoticeDocuments.generateForCutUnits(TENANT, List.of(
                new CutoffNoticeDocuments.CutUnit(u1, CutoffEnums.ConnectionStatus.CUT, Map.of("debtor", "A")),
                new CutoffNoticeDocuments.CutUnit(u2, CutoffEnums.ConnectionStatus.CUT, Map.of("debtor", "B"))),
                templates, CutoffExtTestSupport.extParams(), ISSUED_AT);
        int rendersAfterIssue = templates.renderedCodes.size();

        var byUnit = CutoffNoticeDocuments.query(batch.issued(),
                new CutoffNoticeDocuments.NoticeFilters(u1, null, null, null, null),
                ZoneId.of("America/Sao_Paulo"));
        assertThat(byUnit).hasSize(1);
        var byPeriod = CutoffNoticeDocuments.query(batch.issued(),
                new CutoffNoticeDocuments.NoticeFilters(null, null,
                        LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16), null),
                ZoneId.of("America/Sao_Paulo"));
        assertThat(byPeriod).hasSize(2);
        var outsidePeriod = CutoffNoticeDocuments.query(batch.issued(),
                new CutoffNoticeDocuments.NoticeFilters(null, null,
                        LocalDate.of(2026, 11, 17), null, null), ZoneId.of("America/Sao_Paulo"));
        assertThat(outsidePeriod).isEmpty();

        var reprint = CutoffNoticeDocuments.reprint(byUnit.get(0));
        assertThat(reprint.documentRef()).isEqualTo(byUnit.get(0).documentRef());
        assertThat(reprint.templateVersion()).isEqualTo("v7");
        assertThat(reprint.frozenVariables()).containsEntry("debtor", "A");
        // Reimpressao NAO chama o motor de modelos de novo.
        assertThat(templates.renderedCodes).hasSize(rendersAfterIssue);
    }
}

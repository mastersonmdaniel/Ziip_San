package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.core.cutoff.CutoffEnums;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notificacoes de corte das unidades JA cortadas — POC-32 e POC-33 do
 * roteiro VISAN (alinea j; TR-401/SG-386 e TR-405):
 *
 * <ul>
 *   <li><b>POC-32:</b> gerar e imprimir notificacoes para unidades com
 *       ligacao de agua CORTADA (pelo menos 2 emissoes na demonstracao),
 *       pelo motor de modelos da P2.30 (JOR-06, via
 *       {@link DunningPorts.TemplatePort} — a mesma porta da P4.1).
 *       Unidade que nao esta cortada nao gera: volta registrada como
 *       nao gerada, com o motivo — nunca entra em silencio.</li>
 *   <li><b>POC-33:</b> consultar a notificacao por diversos filtros e
 *       REIMPRIMI-LA COM OS DADOS ORIGINAIS: a emissao congela as
 *       variaveis, o codigo e a versao do modelo e a referencia do
 *       documento; a reimpressao devolve exatamente esse registro, sem
 *       nova renderizacao (mudanca posterior de modelo ou de cadastro
 *       nao altera o que foi emitido — regra da P2.30).</li>
 * </ul>
 */
public final class CutoffNoticeDocuments {

    private CutoffNoticeDocuments() {
    }

    /** Unidade candidata a notificacao de pos-corte, com a situacao lida do cadastro (P1.3). */
    public record CutUnit(UUID unitId, CutoffEnums.ConnectionStatus waterStatus,
                          Map<String, Object> noticeVariables) {
        public CutUnit {
            if (unitId == null || waterStatus == null) {
                throw new IllegalArgumentException("unidade e situacao da ligacao de agua sao obrigatorias");
            }
            noticeVariables = noticeVariables == null ? Map.of() : Map.copyOf(noticeVariables);
        }
    }

    /**
     * Notificacao emitida, com os dados originais CONGELADOS na emissao:
     * e este registro — e nao uma nova renderizacao — que a reimpressao
     * da POC-33 devolve.
     */
    public record IssuedNotice(UUID id, UUID tenantId, UUID unitId, String templateCode,
                               String templateVersion, String documentRef,
                               Map<String, Object> frozenVariables, Instant issuedAt) {
        public IssuedNotice {
            if (id == null || tenantId == null || unitId == null || templateCode == null
                    || templateVersion == null || documentRef == null || issuedAt == null) {
                throw new IllegalArgumentException(
                        "notificacao emitida exige id, tenant, unidade, modelo/versao, documento e instante");
            }
            frozenVariables = frozenVariables == null ? Map.of() : Map.copyOf(frozenVariables);
        }
    }

    /** Unidade nao notificada, com o motivo registrado. */
    public record NotGenerated(UUID unitId, String reason) {
    }

    public record GenerationBatch(List<IssuedNotice> issued, List<NotGenerated> notGenerated) {
    }

    /** Filtros da consulta da POC-33 (TR-405: "por diversas fontes de filtros"). */
    public record NoticeFilters(UUID unitId, UUID noticeId, LocalDate issuedFrom, LocalDate issuedTo,
                                String templateCode) {
        /** Verdadeiro quando a notificacao casa com todos os filtros presentes. */
        public boolean matches(IssuedNotice notice, java.time.ZoneId zone) {
            if (unitId != null && !unitId.equals(notice.unitId())) {
                return false;
            }
            if (noticeId != null && !noticeId.equals(notice.id())) {
                return false;
            }
            if (templateCode != null && !templateCode.equals(notice.templateCode())) {
                return false;
            }
            LocalDate issuedDate = notice.issuedAt().atZone(zone).toLocalDate();
            if (issuedFrom != null && issuedDate.isBefore(issuedFrom)) {
                return false;
            }
            return issuedTo == null || !issuedDate.isAfter(issuedTo);
        }
    }

    /** Reimpressao: os dados originais da emissao, sem nova renderizacao. */
    public record Reprint(UUID noticeId, String templateCode, String templateVersion,
                          String documentRef, Map<String, Object> frozenVariables) {
    }

    /**
     * POC-32: emite a notificacao de cada unidade CORTADA pelo motor de
     * modelos; as demais voltam em {@code notGenerated} com o motivo.
     */
    public static GenerationBatch generateForCutUnits(UUID tenantId, List<CutUnit> units,
                                                      DunningPorts.TemplatePort templates,
                                                      CutoffExtParameters extParams, Instant issuedAt) {
        if (tenantId == null || units == null || templates == null || extParams == null || issuedAt == null) {
            throw new IllegalArgumentException("tenant, unidades, motor de modelos, parametros e instante sao obrigatorios");
        }
        String templateCode = extParams.cutUnitNoticeTemplate();
        List<IssuedNotice> issued = new ArrayList<>();
        List<NotGenerated> notGenerated = new ArrayList<>();
        for (CutUnit unit : units) {
            if (unit.waterStatus() != CutoffEnums.ConnectionStatus.CUT) {
                notGenerated.add(new NotGenerated(unit.unitId(), "CONNECTION_NOT_CUT"));
                continue;
            }
            var rendered = templates.render(tenantId, templateCode, unit.noticeVariables());
            issued.add(new IssuedNotice(UUID.randomUUID(), tenantId, unit.unitId(),
                    rendered.templateCode(), rendered.templateVersion(), rendered.documentRef(),
                    unit.noticeVariables(), issuedAt));
        }
        return new GenerationBatch(List.copyOf(issued), List.copyOf(notGenerated));
    }

    /** POC-33 (consulta): aplica os filtros sobre as notificacoes emitidas. */
    public static List<IssuedNotice> query(List<IssuedNotice> issued, NoticeFilters filters,
                                           java.time.ZoneId zone) {
        if (issued == null || filters == null || zone == null) {
            throw new IllegalArgumentException("emissoes, filtros e fuso sao obrigatorios");
        }
        return issued.stream().filter(n -> filters.matches(n, zone)).toList();
    }

    /**
     * POC-33 (reimpressao): devolve os dados ORIGINAIS congelados na
     * emissao. Nao chama o motor de modelos: modelo novo nao reescreve
     * documento emitido.
     */
    public static Reprint reprint(IssuedNotice notice) {
        if (notice == null) {
            throw new IllegalArgumentException("notificacao e obrigatoria");
        }
        return new Reprint(notice.id(), notice.templateCode(), notice.templateVersion(),
                notice.documentRef(), notice.frozenVariables());
    }
}

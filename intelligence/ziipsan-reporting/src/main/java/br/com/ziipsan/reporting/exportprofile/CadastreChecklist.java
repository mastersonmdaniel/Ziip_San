package br.com.ziipsan.reporting.exportprofile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checklist de completude do cadastro tecnico pelas NBR 12586, 12587,
 * 13133 e 14166 (acceptance da ED-82), com vinculo a rede de referencia
 * cadastral municipal, sobre as normas compradas na P1.28 e os
 * relatorios do cadastro da P3.27.
 *
 * <p>Os topicos de verificacao sao SOMENTE os que as fontes nomeiam:
 * (1) a completude do cadastro tecnico (o proprio objeto do acceptance);
 * (2) o vinculo a rede de referencia cadastral municipal (acceptance);
 * (3) a precisao do levantamento, separando na meta / fora da meta /
 * aproximado (relatorio de precisao da P3.27); (4) os relatorios de
 * extensao do cadastro por dominio/funcao/material/DN/decada/setor
 * (P3.27). Cada topico e verificado contra CADA uma das 4 normas:
 * 16 itens nomeados, todos com requisito {@code [SEM FONTE]} ate a
 * conferencia contra o texto comprado na P1.28.
 */
public final class CadastreChecklist {

    public enum ItemStatus {
        /** Ainda nao conferido contra o texto da norma: bloqueia qualquer declaracao de completude. */
        PENDING_VERIFICATION,
        VERIFIED,
        NON_CONFORMING,
        NOT_APPLICABLE
    }

    public record Evaluation(String itemCode, ItemStatus status, boolean referenceNetworkLinked,
                             String evaluatedBy, String note) {
        public Evaluation {
            if (itemCode == null || itemCode.isBlank()) {
                throw new ExportValidationException("CHECKLIST_EVAL_ITEM_REQUIRED", "avaliacao sem item");
            }
            if (status == null) {
                throw new ExportValidationException("CHECKLIST_EVAL_STATUS_REQUIRED",
                        "avaliacao de " + itemCode + " sem status");
            }
            if (status != ItemStatus.PENDING_VERIFICATION
                    && (evaluatedBy == null || evaluatedBy.isBlank())) {
                throw new ExportValidationException("CHECKLIST_EVAL_ACTOR_REQUIRED",
                        "avaliacao de " + itemCode + " com status " + status + " exige quem avaliou");
            }
            // Correcao D2 da revisao da ED-82: NOT_APPLICABLE sem justificativa deixava o
            // checklist "completo" sem nenhum item verificado (sonda J3 do revisor).
            if (status == ItemStatus.NOT_APPLICABLE && (note == null || note.isBlank())) {
                throw new ExportValidationException("CHECKLIST_EVAL_JUSTIFICATION_REQUIRED",
                        "avaliacao de " + itemCode + " com status NOT_APPLICABLE exige justificativa "
                                + "(note nao-vazia) de quem avaliou");
            }
        }
    }

    private static final List<String[]> TOPICS = List.of(
            new String[]{"COMPLETUDE", "Completude do cadastro tecnico (objeto do acceptance da ED-82)", "false"},
            new String[]{"REDE-REFERENCIA", "Vinculo do cadastro a rede de referencia cadastral municipal (acceptance da ED-82)", "true"},
            new String[]{"PRECISAO", "Precisao do levantamento: na meta, fora da meta e aproximado (relatorio de precisao da P3.27)", "false"},
            new String[]{"RELATORIOS", "Relatorios do cadastro: extensao por dominio, funcao, material, DN, decada e setor (P3.27)", "false"});

    private CadastreChecklist() {
    }

    public static List<CadastreChecklistItem> items() {
        List<CadastreChecklistItem> out = new ArrayList<>();
        for (String norm : ExportReferences.CADASTRE_NORMS) {
            String shortNorm = norm.replace("ABNT NBR ", "NBR");
            for (String[] topic : TOPICS) {
                out.add(new CadastreChecklistItem(shortNorm + "-" + topic[0], norm, topic[1],
                        CadastreChecklistItem.REQUIREMENT_WITHOUT_SOURCE, Boolean.parseBoolean(topic[2])));
            }
        }
        return List.copyOf(out);
    }

    /**
     * O cadastro so e COMPLETO quando TODOS os itens estao VERIFIED (ou
     * NOT_APPLICABLE com justificativa de quem avaliou) e todo item de
     * vinculo esta com a rede de referencia ligada. Item pendente ou nao
     * conforme, ou avaliacao ausente, impede a declaracao - completude
     * nao se presume.
     */
    public static boolean isComplete(Map<String, Evaluation> evaluationsByItemCode) {
        if (evaluationsByItemCode == null) {
            return false;
        }
        for (CadastreChecklistItem item : items()) {
            Evaluation ev = evaluationsByItemCode.get(item.itemCode());
            if (ev == null) {
                return false;
            }
            if (ev.status() == ItemStatus.PENDING_VERIFICATION || ev.status() == ItemStatus.NON_CONFORMING) {
                return false;
            }
            // Defesa em profundidade da correcao D2: o construtor ja recusa, e o
            // isComplete reafirma a semantica prometida no Javadoc desta classe.
            if (ev.status() == ItemStatus.NOT_APPLICABLE && (ev.note() == null || ev.note().isBlank())) {
                return false;
            }
            if (item.requiresReferenceNetworkLink() && !ev.referenceNetworkLinked()) {
                return false;
            }
        }
        return true;
    }
}

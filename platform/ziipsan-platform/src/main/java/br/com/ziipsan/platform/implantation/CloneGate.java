package br.com.ziipsan.platform.implantation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Portão do clone mascarado (ED-09, PLT-39): quando um clone pode nascer.
 *
 * <p>O aceite é taxativo numa ordem: a <b>avaliação do risco de
 * reidentificação é registrada no RIPD do tenant ANTES do primeiro clone</b>.
 * Sem esse registro, nenhum clone — nem o primeiro, nem os seguintes, porque
 * a avaliação é a condição registrada do processo, não um ritual de estreia.
 * A avaliação precisa concluir pela viabilidade (uma avaliação que conclui
 * risco inaceitável bloqueia o clone até nova avaliação favorável) e precisa
 * ser anterior ao pedido.
 *
 * <p>Segunda condição: o clone nasce de um tenant de <b>produção</b> e para
 * um fim declarado — homologação ou treinamento (testar mudança de
 * parâmetro, treinar usuários, ensaiar integração sem dado pessoal). Clone
 * de clone é recusado: a fonte é sempre a produção, para a linhagem do dado
 * sintético não se perder em gerações.
 *
 * <p>Esta classe é a regra pura do portão; a execução física da cópia
 * (provisionar o tenant, copiar o cadastro técnico, aplicar
 * {@link CloneAnonymization} linha a linha e rodar a varredura) é do serviço
 * de provisionamento na integração — ver o relatório da ED-09.
 */
public final class CloneGate {

    private CloneGate() {
    }

    /** Avaliação de risco de reidentificação, como registrada no RIPD do tenant. */
    public record ReidentificationAssessment(UUID tenantId, Instant assessedAt, UUID assessedBy,
                                             boolean viable, String ripdReference) {
        public ReidentificationAssessment {
            if (tenantId == null || assessedAt == null || assessedBy == null) {
                throw new IllegalArgumentException(
                        "A avaliação do RIPD exige tenant, instante e avaliador.");
            }
            if (ripdReference == null || ripdReference.isBlank()) {
                throw new IllegalArgumentException(
                        "A referência do registro no RIPD é obrigatória.");
            }
        }
    }

    public record CloneRequest(UUID sourceTenantId, boolean sourceIsProduction,
                               boolean sourceIsClone, CloneAnonymization.Purpose purpose,
                               Instant requestedAt) {
        public CloneRequest {
            if (sourceTenantId == null || purpose == null || requestedAt == null) {
                throw new IllegalArgumentException(
                        "O pedido de clone exige tenant de origem, finalidade e instante.");
            }
        }
    }

    /** Motivos de bloqueio do clone (vazio = liberado). */
    public static List<String> blockers(CloneRequest request, ReidentificationAssessment assessment) {
        if (request == null) {
            throw new IllegalArgumentException("O pedido de clone é obrigatório.");
        }
        List<String> out = new ArrayList<>();
        if (!request.sourceIsProduction()) {
            out.add("O clone nasce da produção do tenant; a origem informada não é produção.");
        }
        if (request.sourceIsClone()) {
            out.add("Clone de clone é recusado: a origem precisa ser o tenant de produção.");
        }
        if (assessment == null) {
            out.add("Sem avaliação do risco de reidentificação registrada no RIPD do tenant: "
                    + "nenhum clone é liberado (a avaliação precede o primeiro clone).");
            return List.copyOf(out);
        }
        if (!assessment.tenantId().equals(request.sourceTenantId())) {
            out.add("A avaliação do RIPD é de outro tenant.");
        }
        if (!assessment.viable()) {
            out.add("A avaliação registrada no RIPD concluiu risco inaceitável de reidentificação.");
        }
        if (!assessment.assessedAt().isBefore(request.requestedAt())) {
            out.add("A avaliação do RIPD não é anterior ao pedido: ela precisa preceder o clone.");
        }
        return List.copyOf(out);
    }

    public static boolean allowed(CloneRequest request, ReidentificationAssessment assessment) {
        return blockers(request, assessment).isEmpty();
    }
}

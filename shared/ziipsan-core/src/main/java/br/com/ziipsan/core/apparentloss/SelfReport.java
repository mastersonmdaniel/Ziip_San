package br.com.ziipsan.core.apparentloss;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Programa de autodenuncia (aceite da ED-95): o proprio usuario
 * declara a irregularidade e, quando o tenant optou pelo programa
 * ({@code fraud.self_report_fine_waiver_enabled} declarado true —
 * chave proposta, ver {@link LossParameters}), a multa e isenta.
 *
 * <p>Autodenuncia NAO gera debito, corte nem auto de infracao: ela
 * abre um caso em revisao humana, cuja saida e OS de
 * recadastramento/vistoria decidida por humano (a regularizacao e o
 * eventual lancamento seguem os fluxos proprios — cobranca da
 * diferenca, se houver, nao nasce desta classe e o auto segue a
 * ED-24). A isencao registrada aqui e a da MULTA do programa; ela
 * nao decide debito algum.
 */
public final class SelfReport {

    private SelfReport() {
    }

    public enum CaseStatus {
        PENDING_HUMAN_REVIEW, DECIDED, DISMISSED
    }

    public record Case(
            UUID id, UUID tenantId, UUID unitId, String declaredIrregularity,
            LocalDateTime reportedAt, boolean fineWaiverApplies, CaseStatus status) {

        public Case {
            Objects.requireNonNull(id, "id do caso de autodenuncia e obrigatorio");
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            Objects.requireNonNull(unitId, "unidade da autodenuncia e obrigatoria");
            if (declaredIrregularity == null || declaredIrregularity.isBlank())
                throw new IllegalArgumentException("autodenuncia sem a irregularidade declarada nao se registra");
            Objects.requireNonNull(reportedAt, "data/hora da autodenuncia e obrigatoria");
            Objects.requireNonNull(status, "situacao do caso e obrigatoria");
        }

        static Case opened(UUID tenantId, UUID unitId, String declaredIrregularity, LocalDateTime at) {
            return new Case(UUID.randomUUID(), tenantId, unitId, declaredIrregularity, at,
                    true, CaseStatus.PENDING_HUMAN_REVIEW);
        }
    }
}

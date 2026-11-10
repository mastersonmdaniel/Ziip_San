package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-68 (padrao {@code DunningPorts} da P4.1): as superficies das
 * tarefas vizinhas — ED-37 (empreiteiras), P3.1/P4.1 (dividas elegiveis e
 * acordos) — sao consumidas por interfaces proprias, SEM inventar
 * assinaturas das classes delas. A integracao liga as implementacoes
 * reais; porta nao ligada (null no servico) = a operacao que a exige
 * recusa explicitamente, nunca finge o efeito.
 */
public final class ThirdPartyPorts {

    private ThirdPartyPorts() {
    }

    /**
     * E3 — Leitura do cadastro de empreiteiras da ED-37. A integracao
     * delega ao cadastro real da ED-37 (perfil de empreiteira +
     * {@code ContractorContract} da ED-37); esta porta so pergunta pela
     * situacao da empresa NESTE tenant. Sem perfil OU sem contrato de
     * empreiteira activo, a empresa nao e elegivel para receber cessao.
     */
    public interface ContractorDirectoryPort {
        Optional<ContractorStanding> standingOf(UUID tenantId, UUID companySupplierId);

        record ContractorStanding(boolean contractorProfilePresent, boolean contractorContractActive) {
            public boolean eligible() {
                return contractorProfilePresent && contractorContractActive;
            }
        }
    }

    /**
     * E4 — Candidatas a cessao: dividas lidas da superficie de cobranca
     * (P3.1/P4.1). A candidata carrega apenas o necessario a seleccao e ao
     * payload minimo (E5); o identificador do devedor e o MINIMO declarado
     * pela origem.
     */
    public interface EligibleDebtsPort {
        List<CandidateDebt> candidates(UUID tenantId);
    }

    /** Divida candidata (leitura; a seleccao pura e de AssignmentCriteria). */
    public record CandidateDebt(UUID debtId, UUID unitId, BigDecimal amount,
                                LocalDate dueDate, String debtorIdentifier) {
        public CandidateDebt {
            if (debtId == null || unitId == null || amount == null || dueDate == null
                    || debtorIdentifier == null || debtorIdentifier.isBlank()) {
                throw new ThirdPartyCollectionException("CANDIDATE_INCOMPLETE",
                        "candidata exige divida, UC, valor, vencimento e identificador minimo do devedor");
            }
            if (amount.signum() < 0) {
                throw new ThirdPartyCollectionException("CANDIDATE_AMOUNT_INVALID",
                        "candidata com valor negativo: " + amount);
            }
        }

        public MinimalDebtorData toMinimalData() {
            return new MinimalDebtorData(debtId, unitId, amount, dueDate, debtorIdentifier);
        }
    }

    /**
     * E7 — Acordo valido da P4.1 para a divida: a integracao delega ao
     * servico de acordos da P4.1. Sem acordo valido, pagamento NAO e
     * atribuido a empresa, mesmo dentro da janela do contrato.
     */
    public interface ValidAgreementPort {
        boolean hasValidAgreement(UUID tenantId, UUID debtId);
    }
}

package br.com.ziipsan.platform.telephony;

import br.com.ziipsan.core.sac.Customer360View;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-44. Integracao real com PABX/operadora e PENDENCIA EXTERNA
 * categoria B: o nucleo recebe o evento de chamada pela porta e e provado
 * com duble honesto declarado nos testes — nenhum PABX e nomeado aqui.
 */
public final class TelephonyPorts {
    private TelephonyPorts() {}

    /** Evento de chamada recebida entregue pelo PABX (duble nos testes). */
    public record IncomingCall(UUID tenantId, String callId, String callerNumber,
                               UUID attendantId, Instant receivedAt) {
        public IncomingCall {
            java.util.Objects.requireNonNull(tenantId); java.util.Objects.requireNonNull(callId);
            java.util.Objects.requireNonNull(callerNumber); java.util.Objects.requireNonNull(attendantId);
            java.util.Objects.requireNonNull(receivedAt);
        }
    }

    /** Contato JA EXISTENTE nas fontes (cadastro/atendimento): unidade + telefone como guardado. */
    public record ContactEntry(UUID consumerUnitId, String storedPhone) {}

    /** Le os dados de contato que as fontes ja tem — sem cadastro paralelo. */
    public interface ContactDirectoryPort { List<ContactEntry> contactsOf(UUID tenantId); }

    /** A ficha que a chamada abre e a Customer360View da ED-35. */
    public interface Customer360Port { Optional<Customer360View> viewOf(UUID tenantId, UUID consumerUnitId); }

    /** Log de acesso (append-only). */
    public interface AccessLogPort { void append(TelephonyAccessLog entry); }

    /** Cofre: devolve o segredo da referencia; o VALOR nunca vive neste codigo. */
    public interface VaultPort { Optional<String> secretOf(String credentialReference); }

    /** Cliente URA de terceiro cadastrado: so a REFERENCIA ao cofre. */
    public record UraClient(String clientId, String credentialReference, boolean enabled) {}
    public interface UraClientRegistryPort { Optional<UraClient> clientOf(UUID tenantId, String clientId); }

    /**
     * Candidato a discagem JA SELECIONADO por uma regua existente
     * (regua de cobranca da P4.1/ED-29 ou fila de atendimento da ED-35):
     * a ED-44 nao inventa regua de campanha — materializa a lista a partir
     * da selecao recebida, carregando o codigo do motivo/passo da origem.
     */
    public record DialingCandidate(UUID consumerUnitId, String storedPhone, String sourceRuleCode,
                                   String sourceKind) {
        public DialingCandidate {
            java.util.Objects.requireNonNull(consumerUnitId);
            java.util.Objects.requireNonNull(sourceRuleCode, "codigo da regua de origem obrigatorio");
            java.util.Objects.requireNonNull(sourceKind, "origem (COBRANCA/ATENDIMENTO) obrigatoria");
            // Correcao R2 (revisao ED-44): o dominio do codigo espelha o CHECK
            // do SQL (source_kind IN ('COBRANCA','ATENDIMENTO')) — qualquer
            // valor fora do conjunto e recusado aqui, nao so na persistencia.
            if (!sourceKind.equals("COBRANCA") && !sourceKind.equals("ATENDIMENTO"))
                throw new IllegalArgumentException("origem invalida (esperado COBRANCA/ATENDIMENTO): " + sourceKind);
        }
    }
    public interface DialingCandidateSourcePort { List<DialingCandidate> selectedOf(UUID tenantId); }
}

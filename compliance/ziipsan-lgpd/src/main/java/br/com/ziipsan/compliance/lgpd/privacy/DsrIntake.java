package br.com.ziipsan.compliance.lgpd.privacy;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Abertura do pedido do titular (aceite da P3.40): TODO pedido nasce como protocolo da
 * P1.36 do tipo privacidade — no portal (titular autenticado, área Privacidade) ou no
 * atendimento (registro do atendente). Sem protocolo, não há pedido: o aceite não admite
 * pedido "solto" no módulo lgpd.
 *
 * <p>O protocolo é o da {@code V8_1} ({@code service_protocols}): número por tenant e ano,
 * especificação por catálogo do tenant ({@code service_protocol_specs}, código
 * VARCHAR(60)) com o prazo, e o motor de prazos da P1.36 contando o vencimento. Esta
 * classe é a régua da abertura do lado do lgpd: exige o protocolo e a especificação,
 * fixa o prazo de 15 dias do art. 19, II ({@link DsrDeadline}) que a especificação tem
 * de refletir, e devolve o que a escrita grava em {@code lgpd_data_subject_request}
 * (com {@code protocol_id} e {@code origin_channel} da {@code V23_1}).
 *
 * <p>O código da especificação de privacidade é do catálogo DO TENANT e é informado por
 * quem abre — nenhum código é presumido aqui, porque a {@code V8_1} não semeia
 * especificação nenhuma (precedente dela: especificação é política do tenant gravada em
 * catálogo, não conteúdo embutido no motor). Tenant sem a especificação de privacidade
 * cadastrada não abre pedido até cadastrá-la com {@code deadline_days} = 15.
 */
public record DsrIntake(UUID protocolId, String protocolSpecCode, Channel channel,
                        DataSubjectRight right, UUID subjectId, UUID representativeId,
                        DsrDeadline deadline) {

    /** Canais de abertura desta tarefa: portal do titular autenticado ou atendimento. */
    public enum Channel {
        /** Portal, área Privacidade (titular autenticado da P3.3). */
        PORTAL,
        /** Atendimento — balcão (registro do atendente). */
        COUNTER,
        /** Atendimento — telefone (registro do atendente). */
        PHONE
    }

    public DsrIntake {
        Objects.requireNonNull(protocolId, "protocolId é obrigatório: todo pedido nasce "
                + "como protocolo da P1.36 do tipo privacidade");
        if (protocolSpecCode == null || protocolSpecCode.isBlank()
                || protocolSpecCode.length() > 60) {
            throw new IllegalArgumentException("protocolSpecCode é obrigatório (catálogo "
                    + "service_protocol_specs da P1.36, até 60 caracteres)");
        }
        Objects.requireNonNull(channel, "channel é obrigatório");
        Objects.requireNonNull(right, "right é obrigatório (direito do art. 18)");
        Objects.requireNonNull(subjectId, "subjectId é obrigatório");
        Objects.requireNonNull(deadline, "deadline é obrigatório");
    }

    /**
     * Abre o pedido sobre um protocolo já criado pela P1.36.
     *
     * @param specDeadlineDays {@code deadline_days} da especificação no catálogo do
     *                         tenant: tem de ser o prazo legal de 15 dias — especificação
     *                         de privacidade com outro prazo é recusada, porque o prazo
     *                         é da lei (art. 19, II), não da política do tenant
     */
    public static DsrIntake open(UUID protocolId, String protocolSpecCode, int specDeadlineDays,
                                 Channel channel, DataSubjectRight right, UUID subjectId,
                                 UUID representativeId, LocalDate openedDate) {
        if (specDeadlineDays != DsrDeadline.FULL_DECLARATION_DAYS) {
            throw new IllegalArgumentException("a especificação de privacidade deve ter "
                    + "deadline_days = " + DsrDeadline.FULL_DECLARATION_DAYS
                    + " (LGPD, art. 19, II); a do catálogo tem " + specDeadlineDays);
        }
        return new DsrIntake(protocolId, protocolSpecCode, channel, right, subjectId,
                representativeId, DsrDeadline.fromOpening(openedDate));
    }
}

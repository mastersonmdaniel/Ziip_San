package br.com.ziipsan.core.certification.quality9001;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Saída não conforme CLASSIFICADA na ocorrência da P2.25 (ED-69): a
 * saída que não atende ao requisito (produto, serviço ou entrega de um
 * processo do mapa) é identificada, segregada na prática operacional do
 * tenant e ganha uma disposição declarada — e o registro formal da não
 * conformidade é a ocorrência ÚNICA da P2.25 ({@code cert_occurrence} da
 * V21_2, {@code kind = 'NONCONFORMITY'}), nunca um segundo registro de
 * não conformidade criado por este pacote. Aqui guarda-se a
 * CLASSIFICAÇÃO da saída sobre a ocorrência: qual processo a gerou, a
 * descrição do desvio e a disposição decidida.
 *
 * <p>O vínculo é pelo {@code occurrenceId} (UUID da ocorrência), com FK
 * composta {@code (tenant_id, occurrence_id)} no SQL pendente contra
 * {@code cert_occurrence (tenant_id, id)} — ocorrência inexistente ou de
 * outro tenant não aceita classificação. Que a ocorrência vinculada é do
 * tipo NONCONFORMITY é conferido na fiação de integração (o banco não
 * cruza o {@code kind} em CHECK sem trigger; limite declarado no
 * relatório da ED-69, não disfarçado).
 *
 * <p>As disposições são as decisões usuais sobre uma saída não conforme,
 * em palavras do produto (não são texto de norma): retrabalhar até
 * conformar, reparar, aceitar sob concessão de quem pode conceder,
 * segregar para não usar/entregar por engano, sucatear/descartar ou
 * devolver à origem. Disposição CONCESSION exige quem concedeu diferente
 * de quem identificou? Não — o aceite da ED-69 só exige pessoas
 * diferentes na proposta/aceite da ATIVAÇÃO; aqui exige-se apenas
 * decisor declarado.
 *
 * <p>Só com a norma ligada: o nascimento passa pela porta da
 * {@link Iso9001Activation}. Com a norma desligada, a ocorrência da
 * P2.25 continua existindo como registro livre (desenho da V21_2) — o
 * que não nasce é a classificação DESTE pacote.
 */
public record NonconformingOutput(UUID occurrenceId, String processCode, String description,
                                  Disposition disposition, UUID decidedBy, LocalDate decidedOn) {

    /** Disposição decidida sobre a saída não conforme (palavras do produto). */
    public enum Disposition {
        /** Refazer o trabalho até a saída conformar. */
        REWORK,
        /** Corrigir a saída, com registro do reparo. */
        REPAIR,
        /** Aceitar como está, sob concessão registrada de quem pode conceder. */
        CONCESSION,
        /** Separar e identificar para não ser usada ou entregue por engano. */
        SEGREGATION,
        /** Descartar a saída (sucata). */
        SCRAP,
        /** Devolver a saída à origem (fornecedor/processo anterior). */
        RETURN_TO_ORIGIN
    }

    public NonconformingOutput {
        Objects.requireNonNull(occurrenceId, "occurrenceId é obrigatório (a ocorrência "
                + "NONCONFORMITY da P2.25 à qual esta classificação se liga)");
        if (processCode == null || processCode.isBlank()
                || processCode.trim().length() > ProcessMapEntry.PROCESS_CODE_MAX) {
            throw new IllegalArgumentException("processCode é obrigatório (processo do mapa "
                    + "que gerou a saída) e tem no máximo " + ProcessMapEntry.PROCESS_CODE_MAX
                    + " caracteres");
        }
        processCode = processCode.trim();
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description é obrigatória (o que desviou do "
                    + "requisito, declarado pelo tenant)");
        }
        description = description.trim();
        Objects.requireNonNull(disposition, "disposition é obrigatória (a decisão sobre a saída)");
        Objects.requireNonNull(decidedBy, "decidedBy é obrigatório (quem decidiu a disposição)");
        Objects.requireNonNull(decidedOn, "decidedOn é obrigatório (quando decidiu)");
    }

    /** Nascimento da classificação: só com a norma ligada (porta da ativação). */
    public static NonconformingOutput classify(Iso9001Activation activation, UUID occurrenceId,
                                               String processCode, String description,
                                               Disposition disposition, UUID decidedBy,
                                               LocalDate decidedOn) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new NonconformingOutput(occurrenceId, processCode, description, disposition,
                decidedBy, decidedOn);
    }

    /** A saída está contida quando a disposição a impede de seguir como se conforme fosse. */
    public boolean contained() {
        return disposition == Disposition.SEGREGATION || disposition == Disposition.SCRAP
                || disposition == Disposition.RETURN_TO_ORIGIN
                || disposition == Disposition.REWORK || disposition == Disposition.REPAIR;
    }
}

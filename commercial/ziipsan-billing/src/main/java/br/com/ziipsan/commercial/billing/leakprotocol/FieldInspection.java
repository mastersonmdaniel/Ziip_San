package br.com.ziipsan.commercial.billing.leakprotocol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vistoria de campo da revisao por vazamento (ED-23), executada no
 * celular pelo app da ED-20 sobre a OS {@code LEAK_REVIEW} da P3.28 --
 * sem papel: a vistoria so existe como registro digital fotografado.
 *
 * <p>A vistoria consome a execucao de campo da ED-20 por DADOS do
 * comprovante dela ({@code FieldExecution.Receipt}: OS, instante de
 * conclusao, foto de antes e foto de depois utilizaveis -- varredura
 * CLEARED e desfoque quando o tenant exige) e a confirmacao de
 * identidade do hidrometro/lacre da ED-20 ({@code IdentityScan}). Nada
 * disso e reimplementado aqui: quem constroi esta vistoria entrega os
 * identificadores ja validados pela ED-20, e esta classe exige a
 * presenca de todos eles -- vistoria sem foto de antes/depois, sem
 * identidade confirmada ou sem leitura do hidrometro NAO se constroi.
 *
 * <p>Os achados sao FATOS registrados pelo tecnico (vazamento
 * encontrado ou nao, onde, leitura no ato). Nenhum limiar, tolerancia
 * ou criterio de vazamento e presumido: a conclusao tecnica e do laudo
 * ({@link LeakReport}), e o desconto, da P3.28.
 */
public record FieldInspection(
        UUID serviceOrderId,
        UUID technicianId,
        Instant inspectedAt,
        UUID beforePhotoId,
        UUID afterPhotoId,
        String meterIdentityCode,
        BigDecimal meterReading,
        boolean leakFound,
        String leakLocation,
        List<String> findingNotes) {

    public FieldInspection {
        Objects.requireNonNull(serviceOrderId, "OS LEAK_REVIEW da vistoria e obrigatoria");
        Objects.requireNonNull(technicianId, "tecnico da vistoria e obrigatorio");
        Objects.requireNonNull(inspectedAt, "instante da vistoria e obrigatorio");
        Objects.requireNonNull(beforePhotoId,
                "foto de antes da ED-20 e obrigatoria: vistoria sem papel exige a foto digital");
        Objects.requireNonNull(afterPhotoId,
                "foto de depois da ED-20 e obrigatoria: vistoria sem papel exige a foto digital");
        if (meterIdentityCode == null || meterIdentityCode.isBlank()) {
            throw new IllegalArgumentException(
                    "identidade do hidrometro/lacre confirmada pela ED-20 (QR/codigo de barras/NFC) e obrigatoria");
        }
        Objects.requireNonNull(meterReading, "leitura do hidrometro no ato da vistoria e obrigatoria");
        if (meterReading.signum() < 0) {
            throw new IllegalArgumentException("leitura do hidrometro nao pode ser negativa");
        }
        if (leakFound && (leakLocation == null || leakLocation.isBlank())) {
            throw new IllegalArgumentException(
                    "vazamento encontrado exige o local registrado na vistoria (sem papel: o registro e o laudo de campo)");
        }
        findingNotes = findingNotes == null ? List.of() : List.copyOf(findingNotes);
    }
}

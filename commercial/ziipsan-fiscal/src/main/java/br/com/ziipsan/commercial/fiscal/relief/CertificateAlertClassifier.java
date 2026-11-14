package br.com.ziipsan.commercial.fiscal.relief;

import br.com.ziipsan.commercial.fiscal.port.CertificateVault;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Classificacao do certificado A1 pelos limiares de alerta (F-21, art. 894:
 * aviso a 30/15/7 dias, processo diario; vencido, a emissao e bloqueada).
 * Limiares sao da fonte; nao sao parametros inventados.
 */
public final class CertificateAlertClassifier {

    public enum Classification { OK, ALERT_30, ALERT_15, ALERT_7, EXPIRED }

    private CertificateAlertClassifier() {
    }

    public static Classification classify(CertificateVault.CertificateRef ref, OffsetDateTime now) {
        if (ref == null || ref.notAfter() == null) {
            return Classification.EXPIRED; // sem certificado de referencia = tratado como vencido
        }
        LocalDate expiry = ref.notAfter().toLocalDate();
        LocalDate today = now.toLocalDate();
        long days = ChronoUnit.DAYS.between(today, expiry);
        if (days < 0) {
            return Classification.EXPIRED;
        }
        if (days <= 7) {
            return Classification.ALERT_7;
        }
        if (days <= 15) {
            return Classification.ALERT_15;
        }
        if (days <= 30) {
            return Classification.ALERT_30;
        }
        return Classification.OK;
    }

    /** O limiar nomeado de um alerta (para a chave de idempotencia do log). */
    public static String thresholdOf(Classification classification) {
        return switch (classification) {
            case ALERT_30 -> "D30";
            case ALERT_15 -> "D15";
            case ALERT_7 -> "D7";
            case EXPIRED -> "EXPIRED";
            case OK -> "NONE";
        };
    }
}

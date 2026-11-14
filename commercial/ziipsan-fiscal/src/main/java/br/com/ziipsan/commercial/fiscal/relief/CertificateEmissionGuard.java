package br.com.ziipsan.commercial.fiscal.relief;

import br.com.ziipsan.commercial.fiscal.port.CertificateVault;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Guarda de certificado na emissao (DESENHO-PA02 secao 3.8; F-21): certificado
 * de referencia vencido, revogado ou inexistente => emissao recusada com
 * motivo explicito, no ponto da guarda de producao existente.
 *
 * <p><b>P-02-07 continua [A DEFINIR]:</b> nenhuma emissao em contingencia
 * contorna esta guarda — nao se inventa excepcao de contingencia. Se um dia a
 * regra existir com fonte, entra como ramo nomeado aqui.
 */
public final class CertificateEmissionGuard {

    private CertificateEmissionGuard() {
    }

    /**
     * @throws IllegalStateException se nao houver certificado valido para emitir
     */
    public static CertificateVault.CertificateRef requireValidForEmission(
            Optional<CertificateVault.CertificateRef> reference, OffsetDateTime now) {
        CertificateVault.CertificateRef ref = reference.orElseThrow(() ->
                new IllegalStateException(
                        "Emissao recusada: nenhum certificado de referencia para o emitente"));
        if (CertificateAlertClassifier.classify(ref, now)
                == CertificateAlertClassifier.Classification.EXPIRED) {
            throw new IllegalStateException(
                    "Emissao recusada: certificado vencido (validade " + ref.notAfter() + ")");
        }
        return ref;
    }
}

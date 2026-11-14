package br.com.ziipsan.app.job;

import br.com.ziipsan.commercial.fiscal.relief.CertificateAlertPlanner;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adaptador REAL da porta de origem do scheduler de alertas de certificado
 * (correccao R-03 da REVISAO-PA-02: a porta tinha zero implementacoes bean).
 *
 * <p>Fonte: o registo de certificados de referencia alimentado pelo
 * provisionamento de cada tenant/emitente. Nao existe hoje, em nenhum
 * modulo, uma enumeracao transversal de certificados — o cofre vivo
 * ({@code CertificateVault}) resolve por (tenant, emitente) — por isso a
 * origem do job e este registo explicito: quem provisiona um emitente
 * regista aqui o seu certificado de referencia, e o job le o retrato.
 * Sem registos, o job corre e nao emite nada (comportamento correcto).
 */
public final class RegistryCertificateSourcePort
        implements CertificateAlertScheduler.CertificateSourcePort {

    private final List<CertificateAlertPlanner.TenantCertificate> certificates =
            new CopyOnWriteArrayList<>();

    /** Regista (ou substitui) o certificado de referencia de um tenant/emitente. */
    public void register(CertificateAlertPlanner.TenantCertificate certificate) {
        certificates.removeIf(c -> c.tenantId().equals(certificate.tenantId())
                && c.reference().certificateId().equals(certificate.reference().certificateId()));
        certificates.add(certificate);
    }

    @Override
    public List<CertificateAlertPlanner.TenantCertificate> referenceCertificates() {
        return List.copyOf(certificates);
    }
}

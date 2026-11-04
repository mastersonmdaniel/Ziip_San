package br.com.ziipsan.platform.domain;

import br.com.ziipsan.platform.domain.TenantDomainStore.DomainView;

import java.time.Instant;

/**
 * Emissão de certificados para o domínio próprio (P2.27). A interface
 * é a costura para a automação da AWS (LC.2): a LC.2 fornece um
 * emissor ACM sem tocar no serviço nem no store.
 *
 * <p>O emissor local ({@link TestCertificateIssuer}) emite certificados
 * de teste — é o que o plano pede para o ambiente local — e NUNCA deve
 * ser ligado em produção: ele não prova controle do domínio a nenhuma
 * autoridade pública, só registra datas para exercitar renovação e
 * revogação. A prova real de posse continua sendo o TXT + CNAME do
 * serviço, não este emissor.
 */
public interface CertificateIssuer {

    /** Certificado emitido (datas, não bytes: o TLS termina na borda, LC.2). */
    record IssuedCert(String provider, Instant issuedAt, Instant notAfter) {
    }

    /** Emite o primeiro certificado do domínio (após TXT + CNAME ok). */
    IssuedCert issue(String hostname);

    /** Renova o certificado vigente do domínio. */
    IssuedCert renew(DomainView domain);

    /** Revoga o certificado vigente do domínio (best-effort no serviço). */
    void revoke(DomainView domain);
}

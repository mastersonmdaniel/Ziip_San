package br.com.ziipsan.platform.domain;

import br.com.ziipsan.platform.domain.CertificateIssuer.IssuedCert;
import br.com.ziipsan.platform.domain.TenantDomainStore.DomainView;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Emissor de certificados de TESTE (P2.27, ambiente local). Não emite
 * certificado real algum: registra emissor {@code TEST} e datas, para
 * que renovação (janela de 30 dias antes do vencimento) e revogação
 * sejam exercitadas sem a AWS. A LC.2 substitui este emissor pelo ACM.
 *
 * <p>Validade de 90 dias (mesma ordem de grandeza dos certificados
 * públicos automáticos) e janela de renovação de 30 dias: o serviço
 * renova quando {@code agora >= notAfter - 30 dias} ou o certificado
 * já venceu.
 */
public final class TestCertificateIssuer implements CertificateIssuer {

    /** Validade do certificado de teste. */
    public static final Duration VALIDITY = Duration.ofDays(90);
    /** Janela de renovação antes do vencimento. */
    public static final Duration RENEWAL_WINDOW = Duration.ofDays(30);

    private final Clock clock;

    public TestCertificateIssuer(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public IssuedCert issue(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            throw new IllegalArgumentException("hostname é obrigatório");
        }
        Instant now = clock.instant();
        return new IssuedCert("TEST", now, now.plus(VALIDITY));
    }

    @Override
    public IssuedCert renew(DomainView domain) {
        if (domain == null) {
            throw new IllegalArgumentException("domain é obrigatório");
        }
        Instant now = clock.instant();
        return new IssuedCert("TEST", now, now.plus(VALIDITY));
    }

    @Override
    public void revoke(DomainView domain) {
        if (domain == null) {
            throw new IllegalArgumentException("domain é obrigatório");
        }
        // Nada a revogar fora do banco: o certificado de teste só existe
        // como registro de datas. O serviço marca REVOKED no store.
    }

    /** Instante a partir do qual o certificado pode ser renovado. */
    public static Instant renewAfter(Instant notAfter) {
        return notAfter.minus(RENEWAL_WINDOW);
    }
}

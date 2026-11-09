package br.com.ziipsan.core.certification.external;

import java.util.Set;
import java.util.TreeSet;

/**
 * As seis normas dos pacotes ED-69 a ED-74 sobre o modulo
 * {@code certification} (ADR-0041), identificadas pelos parametros
 * REAIS do catalogo do core V3 ({@code cert.<norma>.enabled} e
 * {@code cert.<norma>.edition}, classe d, desligadas por padrao, e o
 * escopo declarado comum {@code cert.scope_statement}).
 *
 * <p>Este enum NAO duplica ativacao alguma: ele apenas nomeia, para o
 * pacote de evidencia da ED-75, as normas que as ED-69..ED-74 ja
 * ligam/desligam pelo nucleo da P1.32. "Norma ligada" neste pacote e
 * o conjunto declarado pelo chamador a partir desses parametros.</p>
 */
public enum CertificationStandard {

    ISO_9001("cert.iso9001.enabled", "cert.iso9001.edition"),
    ISO_14001("cert.iso14001.enabled", "cert.iso14001.edition"),
    ISO_45001("cert.iso45001.enabled", "cert.iso45001.edition"),
    ISO_55001("cert.iso55001.enabled", "cert.iso55001.edition"),
    ISO_27001("cert.iso27001.enabled", "cert.iso27001.edition"),
    ISO_27701("cert.iso27701.enabled", "cert.iso27701.edition");

    private final String enabledParam;
    private final String editionParam;

    CertificationStandard(String enabledParam, String editionParam) {
        this.enabledParam = enabledParam;
        this.editionParam = editionParam;
    }

    /** Parametro do core V3 que liga a norma (classe d, padrao desligado). */
    public String enabledParam() {
        return enabledParam;
    }

    /** Parametro do core V3 com a edicao em uso da norma. */
    public String editionParam() {
        return editionParam;
    }

    /** Parametro comum do escopo declarado (core V3). */
    public static final String SCOPE_PARAM = "cert.scope_statement";

    /** Todas as seis normas (o conjunto maximo de um pacote de evidencia). */
    public static Set<CertificationStandard> all() {
        return Set.of(values());
    }

    /** Copia ordenada e imutavel de um subconjunto declarado. */
    public static Set<CertificationStandard> subsetOf(Set<CertificationStandard> standards) {
        if (standards == null) {
            return Set.of();
        }
        return Set.copyOf(new TreeSet<>(standards));
    }
}

package br.com.ziipsan.commercial.billing.field;

import java.util.Locale;
import java.util.Objects;

/**
 * Portoes da emissao simultanea avaliados na submissao (ED-19), antes de
 * qualquer conta nascer no servidor:
 *
 * <ol>
 *   <li><b>Critica por foto (ED-18):</b> a leitura por foto com OCR roda a
 *       critica ANTES da emissao; o veredito que chega na submissao e o da
 *       {@code OcrComparison} da ED-18 (a imagem nunca sai do aparelho).
 *       {@code REVIEW} segura a emissao — a conta nao se confirma com
 *       leitura em revisao. {@code SUGGESTION} nao segura: o leiturista ja
 *       decidiu em campo, e a decisao viaja registrada.</li>
 *   <li><b>Assinatura (P3.34, Lei 14.063):</b> quando o tipo de documento
 *       exige assinatura (nivel minimo configurado em
 *       {@code sign.min_level.<tipo>}), o documento de campo assina NO
 *       APARELHO; aparelho sem o certificado exigido NAO emite o documento
 *       assinado — a submissao volta {@code CERTIFICATE_MISSING} e a conta
 *       fica retida para regularizacao, nunca "assinada" por simulacao.
 *       Este e o comportamento do teste com certificado ausente do aceite.</li>
 * </ol>
 */
public final class FieldEmissionGates {

    /** Vereditos da critica OCR da ED-18 (espelho por nome, sem acoplar modulos). */
    public enum OcrGate {
        DISABLED, MATCH, SUGGESTION, REVIEW;

        public static OcrGate fromEd18Verdict(String verdictName) {
            if (verdictName == null || verdictName.isBlank()) {
                throw new IllegalArgumentException("Veredito OCR da ED-18 e obrigatorio na submissao");
            }
            try {
                return valueOf(verdictName.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Veredito OCR fora do catalogo da ED-18: " + verdictName, e);
            }
        }
    }

    /** Nivel de assinatura da Lei 14.063 (espelho por nome dos niveis da P3.34). */
    public enum SignatureGateLevel {
        SIMPLE, ADVANCED, QUALIFIED;

        public static SignatureGateLevel fromP334Name(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Nivel de assinatura fora de SIMPLE, ADVANCED, QUALIFIED (P3.34): " + name, e);
            }
        }
    }

    private FieldEmissionGates() {
    }

    /** A critica de OCR segura a emissao? */
    public static boolean ocrHolds(OcrGate verdict) {
        Objects.requireNonNull(verdict, "veredito OCR e obrigatorio");
        return verdict == OcrGate.REVIEW;
    }

    /**
     * O portao de assinatura barra a emissao?
     *
     * @param requiredLevel nivel minimo do tipo na P3.34; {@code null} = o
     *                      tipo nao exige assinatura (sem portao)
     * @param deviceCertificatePresent o aparelho tem o certificado exigido
     * @param deviceSignatureLevel nivel que o aparelho declara ter assinado;
     *                             {@code null} = nao assinou
     */
    public static boolean signatureBlocks(SignatureGateLevel requiredLevel,
                                          boolean deviceCertificatePresent,
                                          SignatureGateLevel deviceSignatureLevel) {
        if (requiredLevel == null) {
            return false;
        }
        if (!deviceCertificatePresent) {
            return true;
        }
        if (deviceSignatureLevel == null) {
            return true;
        }
        return deviceSignatureLevel.ordinal() < requiredLevel.ordinal();
    }
}

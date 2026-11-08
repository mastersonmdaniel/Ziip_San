package br.com.ziipsan.platform.portal.verify;

import java.util.Locale;
import java.util.UUID;

/**
 * Conferencia do recebedor do Pix (P3.35): o nome e o CNPJ que o pagador ve
 * no aplicativo do banco sao os cadastrados no PSP para a chave/conta do
 * tenant. A regra desta tarefa: o recebedor cadastrado no PSP TEM de ser a
 * razao social e o CNPJ do cadastro do tenant (P1.41) — divergencia e
 * nao-conformidade explicita (a cobranca nao deve sair com recebedor
 * divergente, porque e indistinguivel de golpe para o pagador).
 *
 * <p>As duas pontas sao portas: a identidade legal vem do cadastro do
 * tenant; o recebedor do PSP vem do perfil/contrato do provedor (P3.2).
 * Nenhum nome ou CNPJ e escrito aqui.
 *
 * <p>Comportamento conservador declarado (correcao D7 da revisao
 * cruzada): se o PSP TRUNCAR o nome do recebedor na exibicao/cadastro,
 * a comparacao NAO casa e o veredito e {@code *_DIVERGENT} — falso
 * nao-conforme, nunca falso conforme. Nome truncado exige correcao do
 * cadastro no PSP, nao afrouxamento desta conferencia.
 */
public final class PixReceiverCheck {

    /** Identidade legal do tenant (cadastro, P1.41). */
    public record TenantLegalIdentity(String legalName, String taxId) {
        public TenantLegalIdentity {
            if (legalName == null || legalName.isBlank() || taxId == null || taxId.isBlank()) {
                throw new IllegalArgumentException("identidade legal do tenant exige razao social e CNPJ");
            }
        }
    }

    /** Recebedor como cadastrado no PSP para o tenant. */
    public record PspReceiver(String receiverName, String receiverTaxId) { }

    public enum Verdict { CONFORMING, NAME_DIVERGENT, TAX_ID_DIVERGENT, BOTH_DIVERGENT }

    public record Result(Verdict verdict, String expectedName, String expectedTaxId,
                         String pspName, String pspTaxId) {
        public boolean conforming() {
            return verdict == Verdict.CONFORMING;
        }
    }

    public interface LegalIdentityPort {
        TenantLegalIdentity legalIdentityOf(UUID tenantId);
    }

    public interface PspReceiverPort {
        PspReceiver receiverRegisteredAtPsp(UUID tenantId);
    }

    private PixReceiverCheck() { }

    public static Result check(TenantLegalIdentity legal, PspReceiver psp) {
        if (legal == null || psp == null) {
            throw new IllegalArgumentException("identidade legal e recebedor do PSP sao obrigatorios");
        }
        if (psp.receiverName() == null || psp.receiverName().isBlank()
                || psp.receiverTaxId() == null || psp.receiverTaxId().isBlank()) {
            throw new IllegalArgumentException("recebedor do PSP sem nome ou sem CNPJ nao e conferivel: nao-conformidade de cadastro");
        }
        boolean nameOk = normalizeName(legal.legalName()).equals(normalizeName(psp.receiverName()));
        boolean taxOk = digits(legal.taxId()).equals(digits(psp.receiverTaxId()));
        Verdict verdict = nameOk && taxOk ? Verdict.CONFORMING
                : !nameOk && !taxOk ? Verdict.BOTH_DIVERGENT
                : !nameOk ? Verdict.NAME_DIVERGENT : Verdict.TAX_ID_DIVERGENT;
        return new Result(verdict, legal.legalName(), legal.taxId(), psp.receiverName(), psp.receiverTaxId());
    }

    /**
     * Nome comparavel: maiusculas, espacos colapsados e pontuacao de
     * abreviatura/sufixo societario removida (correcao D7: a promessa
     * original nao era implementada — {@code "... LTDA."} divergia de
     * {@code "... LTDA"}). Pontos sao removidos ({@code "S.A."} vira
     * {@code "SA"}, {@code "LTDA."} vira {@code "LTDA"}) e virgulas viram
     * espaco, antes do colapso de espacos.
     */
    static String normalizeName(String name) {
        return name.strip().toUpperCase(Locale.ROOT)
                .replace(".", "")
                .replace(",", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    static String digits(String taxId) {
        return taxId.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    }
}

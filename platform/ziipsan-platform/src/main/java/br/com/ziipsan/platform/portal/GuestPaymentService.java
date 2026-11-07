package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Pagamento sem conta (ADR-0043, P3.3): o convidado informa o codigo de
 * vinculacao impresso e recebe SOMENTE a fatura em aberto daquele codigo.
 *
 * <p>Garantias implementadas aqui:
 * <ul>
 *   <li>a resposta ({@link GuestInvoiceView}) tem exatamente 5 componentes —
 *       valor, vencimento, mes de referencia, linha digitavel e Pix copia e
 *       cola — sem nome, endereco, documento ou consumo (o teste de conteudo
 *       serializa a resposta e recusa qualquer outra chave);</li>
 *   <li>codigo inexistente, codigo sem fatura em aberto, sufixo divergente
 *       e formato invalido devolvem a MESMA resposta (nao encontrado
 *       indistinto);</li>
 *   <li>limite por origem e por codigo ({@code portal.account_throttle}),
 *       com 429 ao estourar;</li>
 *   <li>cada consulta e registrada (sucesso ou nao), sem dado do titular;</li>
 *   <li>nenhuma sessao e criada: a resposta nao carrega token nem cookie.</li>
 * </ul>
 * O modo vem de {@code portal.guest_payment} (classe b): OFF recusa o
 * caminho inteiro; CODE_AND_DOCUMENT_SUFFIX exige ainda o sufixo do
 * documento do titular, conferido pela porta (o adaptador decifra; aqui
 * nunca chega documento em claro).
 */
public final class GuestPaymentService {

    /** A resposta do convidado: SOMENTE estes 5 campos, nesta ordem. */
    public record GuestInvoiceView(BigDecimal amount, LocalDate dueDate, YearMonth referenceMonth,
                                   String digitableLine, String pixCopyPaste) {
        public GuestInvoiceView {
            if (amount == null || dueDate == null || referenceMonth == null
                    || digitableLine == null || digitableLine.isBlank()
                    || pixCopyPaste == null || pixCopyPaste.isBlank()) {
                throw new IllegalArgumentException("fatura do convidado exige valor, vencimento, referencia, linha digitavel e Pix");
            }
        }
    }

    /** Registro de cada consulta de convidado (sem dado pessoal do titular). */
    public record GuestQueryAudit(UUID tenantId, String originKey, String codeHash,
                                  boolean found, Instant queriedAt) { }

    /** Porta de dados do caminho de convidado (adaptador = integracao). */
    public interface GuestInvoicePort {
        Optional<GuestInvoiceView> findOpenInvoice(UUID tenantId, String codeHash);
        /** Conferencia do sufixo do documento do titular, sem expor o documento. */
        boolean documentSuffixMatches(UUID tenantId, String codeHash, String documentSuffix);
        int queriesByOrigin(UUID tenantId, String originKey);
        int queriesByCode(UUID tenantId, String codeHash);
        void recordQuery(GuestQueryAudit audit);
    }

    /** Porta do indice cego do codigo (FieldProtector no adaptador). */
    public interface CodeHasher {
        String hash(UUID tenantId, String code);
    }

    private final GuestInvoicePort port;
    private final CodeHasher hasher;

    public GuestPaymentService(GuestInvoicePort port, CodeHasher hasher) {
        if (port == null || hasher == null) {
            throw new IllegalArgumentException("port e hasher sao obrigatorios");
        }
        this.port = port;
        this.hasher = hasher;
    }

    /**
     * @param documentSuffix sufixo do documento do titular; exigido so no modo CODE_AND_DOCUMENT_SUFFIX
     */
    public GuestInvoiceView query(UUID tenantId, String originKey, String code, String documentSuffix,
                                  PortalParameters params, PortalParameters.AccountThrottle throttle,
                                  Instant now) {
        if (tenantId == null || originKey == null || originKey.isBlank() || now == null
                || params == null || throttle == null) {
            throw new IllegalArgumentException("tenant, origem, instante, parametros e limites sao obrigatorios");
        }
        PortalParameters.GuestPaymentMode mode = params.guestPaymentMode();
        String codeHash = null;
        boolean shapeOk = LinkingCode.hasCodeShape(code);
        if (shapeOk) {
            codeHash = hasher.hash(tenantId, code);
        }
        // Limite por ORIGEM vale para TODA consulta, inclusive com codigo
        // malformado (revisao cruzada da P3.3): sem isto, o atacante fugia do
        // 429 e da contagem usando codigos fora do formato. O limite por
        // codigo so existe quando ha hash calculavel.
        if (port.queriesByOrigin(tenantId, originKey) >= throttle.maxGuestQueriesPerOrigin()
                || (codeHash != null && port.queriesByCode(tenantId, codeHash) >= throttle.maxGuestQueriesPerCode())) {
            port.recordQuery(new GuestQueryAudit(tenantId, originKey, codeHash, false, now));
            throw new ApiException.TooManyRequests("Muitas consultas. Aguarde e tente novamente.");
        }
        if (mode == PortalParameters.GuestPaymentMode.OFF) {
            port.recordQuery(new GuestQueryAudit(tenantId, originKey, codeHash, false, now));
            throw new ApiException.Forbidden("Pagamento sem conta desligado para este tenant.");
        }
        GuestInvoiceView found = null;
        if (shapeOk) {
            Optional<GuestInvoiceView> invoice = port.findOpenInvoice(tenantId, codeHash);
            // No modo com sufixo, a conferencia do sufixo roda SEMPRE (haja
            // fatura ou nao), para o trabalho — e o tempo — nao revelar se o
            // codigo existe; o resultado so vale junto da fatura presente.
            boolean suffixOk = true;
            if (mode == PortalParameters.GuestPaymentMode.CODE_AND_DOCUMENT_SUFFIX) {
                suffixOk = documentSuffix != null && !documentSuffix.isBlank()
                        && port.documentSuffixMatches(tenantId, codeHash, documentSuffix);
            }
            if (invoice.isPresent() && suffixOk) {
                found = invoice.get();
            }
        }
        port.recordQuery(new GuestQueryAudit(tenantId, originKey, codeHash, found != null, now));
        if (found == null) {
            // Resposta indistinta: inexistente, sem fatura, sufixo errado ou formato invalido.
            throw new ApiException.NotFound("Nenhuma fatura em aberto para o codigo informado.");
        }
        return found;
    }
}

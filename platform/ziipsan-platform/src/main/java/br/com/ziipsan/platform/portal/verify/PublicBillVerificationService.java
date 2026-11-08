package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.portal.PortalParameters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Verificacao publica de autenticidade da fatura (P3.35, PLT-11;
 * {@code GET /api/v1/public/documents/verify}): pelo codigo curto ou pelo
 * QR impresso na conta, confirma valor, vencimento, recebedor e hash do
 * documento — sem expor dado pessoal.
 *
 * <p>Contrato anti-oraculo (o cuidado central da tarefa):
 * <ul>
 *   <li>a resposta de sucesso ({@link VerificationResult}) tem SOMENTE os
 *       campos de conferencia da fatura e do recebedor — recebedor e a
 *       pessoa juridica do tenant (razao social e CNPJ, dados publicos ja
 *       impressos na conta, CDC art. 42-A). Titular, endereco, documento do
 *       titular, matricula, unidade e consumo NAO existem neste record e
 *       nunca sao lidos pela porta;</li>
 *   <li>codigo inexistente, malformado, revogado, de fatura cancelada e
 *       hash divergente devolvem EXATAMENTE a mesma resposta
 *       ({@code authentic=false}, demais campos nulos): a consulta nao
 *       distingue "codigo nao existe" de "documento adulterado";</li>
 *   <li>verificacao desligada no tenant ({@code portal.verify_enabled})
 *       devolve a mesma resposta de nao-autentica: o estado do parametro
 *       nao e observavel pelo resultado;</li>
 *   <li>o codigo tem 80 bits ({@link VerificationCode}) e as tentativas
 *       sao limitadas por origem e por codigo com os mesmos tetos do
 *       caminho de convidado da P3.3 ({@code portal.account_throttle},
 *       ADR-0043 item 4): estouro = 429, registrado;</li>
 *   <li>TODA consulta e registrada sem dado do titular (V8_3): tenant,
 *       indice cego do codigo quando calculavel, origem e resultado.</li>
 * </ul>
 *
 * <p>A autenticidade exige as tres conferencias juntas: codigo localizado
 * pelo indice cego e ativo, fatura nao cancelada e hash recalculado igual
 * ao gravado na emissao ({@link BillDocumentHash}).
 */
public final class PublicBillVerificationService {

    /**
     * A resposta publica: SOMENTE estes campos. {@code authentic=false}
     * vem sempre com os demais nulos (forma unica de falha).
     */
    public record VerificationResult(boolean authentic, BigDecimal amount, LocalDate dueDate,
                                     YearMonth referenceMonth, String receiverName,
                                     String receiverTaxId, String documentHash) {
        public VerificationResult {
            if (authentic && (amount == null || dueDate == null || referenceMonth == null
                    || receiverName == null || receiverName.isBlank()
                    || receiverTaxId == null || receiverTaxId.isBlank()
                    || documentHash == null || documentHash.isBlank())) {
                throw new IllegalArgumentException(
                        "resultado autentico exige valor, vencimento, competencia, recebedor e hash");
            }
            if (!authentic && (amount != null || dueDate != null || referenceMonth != null
                    || receiverName != null || receiverTaxId != null || documentHash != null)) {
                throw new IllegalArgumentException(
                        "resultado nao autentico nao carrega nenhum dado da fatura (forma unica de falha)");
            }
        }

        public static VerificationResult notAuthentic() {
            return new VerificationResult(false, null, null, null, null, null, null);
        }
    }

    /** Fatura localizada pelo indice cego do codigo: so o que a conferencia precisa. */
    public record VerifiableBill(UUID billId, int billVersionNo, BigDecimal amount, LocalDate dueDate,
                                 YearMonth referenceMonth, String receiverName, String receiverTaxId,
                                 String documentHash, boolean codeActive, boolean billCancelled) { }

    /** Registro de cada consulta (sem dado do titular). */
    public record VerificationQueryAudit(UUID tenantId, String originKey, String codeHash,
                                         String result, Instant queriedAt) {
        public static final String AUTHENTIC = "AUTHENTIC";
        public static final String NOT_AUTHENTIC = "NOT_AUTHENTIC";
        public static final String RATE_LIMITED = "RATE_LIMITED";
    }

    /** Porta de dados da verificacao (adaptador = integracao sobre a V8_3 e a conta da P2.5). */
    public interface VerificationPort {
        Optional<VerifiableBill> findByCodeHash(UUID tenantId, String codeHash);
        int queriesByOrigin(UUID tenantId, String originKey);
        int queriesByCode(UUID tenantId, String codeHash);
        void recordQuery(VerificationQueryAudit audit);
    }

    /** Porta do indice cego do codigo (FieldProtector no adaptador, contexto de VerificationCode). */
    public interface CodeHasher {
        String hash(UUID tenantId, String code);
    }

    private final VerificationPort port;
    private final CodeHasher hasher;

    public PublicBillVerificationService(VerificationPort port, CodeHasher hasher) {
        if (port == null || hasher == null) {
            throw new IllegalArgumentException("port e hasher sao obrigatorios");
        }
        this.port = port;
        this.hasher = hasher;
    }

    public VerificationResult verify(UUID tenantId, String originKey, String code,
                                     FraudProtectionParameters params,
                                     PortalParameters.AccountThrottle throttle, Instant now) {
        if (tenantId == null || originKey == null || originKey.isBlank() || now == null
                || params == null || throttle == null) {
            throw new IllegalArgumentException("tenant, origem, instante, parametros e limites sao obrigatorios");
        }
        String codeHash = null;
        boolean shapeOk = VerificationCode.hasCodeShape(code);
        if (shapeOk) {
            codeHash = hasher.hash(tenantId, code);
        }
        // Limite por ORIGEM vale para TODA consulta, inclusive malformada
        // (mesma correcao da revisao cruzada da P3.3); o por codigo, quando ha hash.
        if (port.queriesByOrigin(tenantId, originKey) >= throttle.maxGuestQueriesPerOrigin()
                || (codeHash != null && port.queriesByCode(tenantId, codeHash) >= throttle.maxGuestQueriesPerCode())) {
            port.recordQuery(new VerificationQueryAudit(tenantId, originKey, codeHash,
                    VerificationQueryAudit.RATE_LIMITED, now));
            throw new ApiException.TooManyRequests("Muitas consultas. Aguarde e tente novamente.");
        }
        VerificationResult result = VerificationResult.notAuthentic();
        if (params.verifyEnabled() && shapeOk) {
            Optional<VerifiableBill> found = port.findByCodeHash(tenantId, codeHash);
            if (found.isPresent()) {
                VerifiableBill bill = found.get();
                String recomputed = BillDocumentHash.compute(tenantId, bill.billId(), bill.billVersionNo(),
                        bill.amount(), bill.dueDate(), bill.referenceMonth(),
                        bill.receiverName(), bill.receiverTaxId());
                if (bill.codeActive() && !bill.billCancelled()
                        && BillDocumentHash.matches(bill.documentHash(), recomputed)) {
                    result = new VerificationResult(true, bill.amount(), bill.dueDate(),
                            bill.referenceMonth(), bill.receiverName(), bill.receiverTaxId(),
                            bill.documentHash());
                }
            }
        }
        port.recordQuery(new VerificationQueryAudit(tenantId, originKey, codeHash,
                result.authentic() ? VerificationQueryAudit.AUTHENTIC : VerificationQueryAudit.NOT_AUTHENTIC,
                now));
        return result;
    }
}

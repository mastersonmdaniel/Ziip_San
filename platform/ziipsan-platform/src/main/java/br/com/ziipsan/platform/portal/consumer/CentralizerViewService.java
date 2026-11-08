package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Visao do orgao centralizador e do grande cliente com muitas unidades
 * no portal do consumidor (ED-22 sobre a P3.20; a ED-38 exibe): lista
 * das unidades vinculadas, exportacao e fatura agrupadora da
 * competencia. O acesso exige a conta do portal vinculada como
 * centralizador/grande cliente - unidade fora da lista e recusada
 * antes de qualquer leitura (mesmo portao da P3.20).
 */
public final class CentralizerViewService {

    /** Unidade da visao (matricula de exibicao vem da porta; nenhum dado e inventado aqui). */
    public record UnitSummary(UUID unitId, String registration, BigDecimal openAmount) {
        public UnitSummary {
            if (unitId == null || registration == null || registration.isBlank() || openAmount == null) {
                throw new IllegalArgumentException("unidade da visao incompleta");
            }
        }
    }

    /** Fatura agrupadora exibida no portal. */
    public record GroupedBillView(String code, YearMonth reference, BigDecimal total, int memberCount) {
        public GroupedBillView {
            if (code == null || code.isBlank() || reference == null || total == null || memberCount < 1) {
                throw new IllegalArgumentException("fatura agrupadora da visao incompleta");
            }
        }
    }

    public interface CentralizerPort {
        boolean isCentralizerAccount(UUID tenantId, String consumerSubject, UUID centralizerCustomerId);
        List<UnitSummary> units(UUID tenantId, UUID centralizerCustomerId);
        GroupedBillView groupedBill(UUID tenantId, UUID centralizerCustomerId, YearMonth reference);
    }

    private final CentralizerPort port;

    public CentralizerViewService(CentralizerPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port e obrigatoria");
        }
        this.port = port;
    }

    /**
     * Correcao pos-revisao (D6): o canal NAO e mais fixado em
     * {@code OWN_ACCOUNT}. Ele entra como parametro de cada operacao (mesmo
     * desenho de {@code ConsumerHistoryService} da P3.20) e e o
     * {@code ConsumerAccessGuard} da P3.20 quem decide: conta propria e
     * gov.br passam; {@code GUEST} e recusado pelo guard. Nenhum
     * comportamento proprio do gov.br e inventado aqui - so o canal
     * declarado pelo chamador e repassado ao guard. As sobrecargas sem
     * canal mantem o padrao {@code OWN_ACCOUNT} por compatibilidade.
     */
    private void requireAccess(UUID tenantId, String consumerSubject, UUID centralizerCustomerId,
                               ConsumerAccessGuard.Channel channel) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        if (tenantId == null || consumerSubject == null || consumerSubject.isBlank()
                || centralizerCustomerId == null) {
            throw new IllegalArgumentException("tenant, consumidor e centralizador sao obrigatorios");
        }
        if (!port.isCentralizerAccount(tenantId, consumerSubject, centralizerCustomerId)) {
            throw new ApiException.Forbidden("Conta do portal nao e a do orgao centralizador/grande cliente.");
        }
    }

    /** Lista das unidades do centralizador/grande cliente. */
    public List<UnitSummary> units(UUID tenantId, String consumerSubject, UUID centralizerCustomerId) {
        return units(tenantId, consumerSubject, centralizerCustomerId,
                ConsumerAccessGuard.Channel.OWN_ACCOUNT);
    }

    /** Lista das unidades, com o canal de acesso declarado pelo chamador (D6). */
    public List<UnitSummary> units(UUID tenantId, String consumerSubject, UUID centralizerCustomerId,
                                   ConsumerAccessGuard.Channel channel) {
        requireAccess(tenantId, consumerSubject, centralizerCustomerId, channel);
        return List.copyOf(port.units(tenantId, centralizerCustomerId));
    }

    /** Exportacao das unidades em CSV (';'), para a ED-38 exibir/baixar. */
    public String exportUnitsCsv(UUID tenantId, String consumerSubject, UUID centralizerCustomerId) {
        return exportUnitsCsv(tenantId, consumerSubject, centralizerCustomerId,
                ConsumerAccessGuard.Channel.OWN_ACCOUNT);
    }

    /** Exportacao das unidades em CSV, com o canal declarado pelo chamador (D6). */
    public String exportUnitsCsv(UUID tenantId, String consumerSubject, UUID centralizerCustomerId,
                                 ConsumerAccessGuard.Channel channel) {
        List<UnitSummary> units = units(tenantId, consumerSubject, centralizerCustomerId, channel);
        StringBuilder sb = new StringBuilder("unidade;matricula;valor_em_aberto\n");
        for (UnitSummary unit : units) {
            sb.append(unit.unitId()).append(';')
                    .append(unit.registration()).append(';')
                    .append(unit.openAmount().toPlainString()).append('\n');
        }
        return sb.toString();
    }

    /** Fatura agrupadora da competencia (codigo unico, total e membros). */
    public GroupedBillView groupedBill(UUID tenantId, String consumerSubject, UUID centralizerCustomerId,
                                       YearMonth reference) {
        return groupedBill(tenantId, consumerSubject, centralizerCustomerId, reference,
                ConsumerAccessGuard.Channel.OWN_ACCOUNT);
    }

    /** Fatura agrupadora da competencia, com o canal declarado pelo chamador (D6). */
    public GroupedBillView groupedBill(UUID tenantId, String consumerSubject, UUID centralizerCustomerId,
                                       YearMonth reference, ConsumerAccessGuard.Channel channel) {
        requireAccess(tenantId, consumerSubject, centralizerCustomerId, channel);
        if (reference == null) {
            throw new IllegalArgumentException("competencia e obrigatoria");
        }
        return port.groupedBill(tenantId, centralizerCustomerId, reference);
    }
}

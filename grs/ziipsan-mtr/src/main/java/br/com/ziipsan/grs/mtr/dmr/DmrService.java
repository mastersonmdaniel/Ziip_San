package br.com.ziipsan.grs.mtr.dmr;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E3.
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.repository.WasteMtrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * DMR trimestral (ED-104, E3). A agregação é determinística e conferível à mão: soma das
 * quantidades dos MTRs emitidos pelo tenant no trimestre, com o recorte dos já destinados,
 * agrupada por código de classe e por destinador no conteúdo JSON. O trimestre vizinho não
 * contamina: a janela é [primeiro dia do trimestre, primeiro dia do trimestre seguinte).
 * Uma DMR por (tenant, ano, trimestre): regerar em DRAFT substitui a agregação; CLOSED não muda.
 */
@Service
@RequiredArgsConstructor
public class DmrService {

    private final WasteMtrRepository mtrRepository;
    private final WasteDmrRepository dmrRepository;

    /** Janela do trimestre (ano, 1..4). */
    public static LocalDate[] quarterWindow(int year, int quarter) {
        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("trimestre inválido: " + quarter);
        }
        LocalDate start = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
        return new LocalDate[]{start, start.plusMonths(3).minusDays(1), start.plusMonths(3)};
    }

    @Transactional
    public WasteDmr buildQuarterlyDmr(int year, int quarter) {
        UUID tenantId = requireTenant();
        LocalDate[] w = quarterWindow(year, quarter);
        WasteDmr dmr = dmrRepository.findByTenantIdAndYearAndQuarter(tenantId, year, quarter)
                .orElseGet(() -> {
                    WasteDmr d = new WasteDmr();
                    d.setTenantId(tenantId);
                    d.setYear(year);
                    d.setQuarter(quarter);
                    d.setPeriodStart(w[0]);
                    d.setPeriodEnd(w[1]);
                    return d;
                });
        if (dmr.getStatus() == WasteDmr.DmrStatus.CLOSED) {
            throw new IllegalStateException("DMR " + year + "T" + quarter + " já está fechada");
        }

        OffsetDateTime from = w[0].atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = w[2].atStartOfDay().atOffset(ZoneOffset.UTC);
        List<WasteMtr> mtrs = mtrRepository.findIssuedInPeriod(tenantId, from, to);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal destined = BigDecimal.ZERO;
        Map<String, BigDecimal> byClass = new TreeMap<>();
        Map<String, BigDecimal> byDestination = new TreeMap<>();
        for (WasteMtr m : mtrs) {
            total = total.add(m.getQuantityKg());
            if (m.getStatus() == WasteMtr.MtrStatus.DESTINED) {
                destined = destined.add(m.getQuantityKg());
            }
            byClass.merge(m.getWasteClass().getCode(), m.getQuantityKg(), BigDecimal::add);
            byDestination.merge(m.getDestination().getId().toString(), m.getQuantityKg(), BigDecimal::add);
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("mtrCount", mtrs.size());
        content.put("totalQuantityKg", total.stripTrailingZeros().toPlainString());
        content.put("destinedQuantityKg", destined.stripTrailingZeros().toPlainString());
        content.put("byWasteClassCode", plainStrings(byClass));
        content.put("byDestinationId", plainStrings(byDestination));
        content.put("mtrNumbers", mtrs.stream().map(WasteMtr::getMtrNumber).sorted().toList());

        dmr.setMtrCount(mtrs.size());
        dmr.setTotalQuantityKg(total);
        dmr.setDestinedQuantityKg(destined);
        dmr.setContent(Json.write(content));
        return dmrRepository.save(dmr);
    }

    private static Map<String, String> plainStrings(Map<String, BigDecimal> values) {
        Map<String, String> out = new TreeMap<>();
        values.forEach((k, v) -> out.put(k, v.stripTrailingZeros().toPlainString()));
        return out;
    }

    @Transactional
    public WasteDmr closeDmr(int year, int quarter) {
        UUID tenantId = requireTenant();
        WasteDmr dmr = dmrRepository.findByTenantIdAndYearAndQuarter(tenantId, year, quarter)
                .orElseThrow(() -> new IllegalArgumentException("DMR não encontrada: " + year + "T" + quarter));
        if (dmr.getStatus() == WasteDmr.DmrStatus.CLOSED) {
            throw new IllegalStateException("DMR " + year + "T" + quarter + " já está fechada");
        }
        dmr.setStatus(WasteDmr.DmrStatus.CLOSED);
        dmr.setClosedAt(OffsetDateTime.now());
        return dmrRepository.save(dmr);
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }
}

package br.com.ziipsan.platform.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;

import java.util.ArrayList;
import java.util.List;

/**
 * II-8: guarda de cardinalidade do go-live — impõe no registry a parte
 * inegociável da {@link MetricLabelPolicy}: qualquer medidor que chegue
 * com um rótulo proibido sempre ({@code uc_id} e os demais de
 * {@link MetricLabelPolicy#FORBIDDEN_LABELS}) é RECUSADO
 * ({@link MeterFilterReply#DENY} via {@link MeterFilter#accept(Meter.Id)}),
 * não registado com o rótulo
 * removido nem, pior, com o rótulo aceite.
 *
 * <p>Por que recusar, e não remover como o {@code TenantTagGuard} (P0.8)
 * faz com {@code tenant}/{@code tenant_id}? Porque um rótulo por UC ou
 * por cliente é erro de desenho de quem regista, não variação legítima:
 * remover em silêncio esconderia o erro e a série nasceria com outro
 * significado. A recusa é detectável (o medidor não existe no registry)
 * e o teste da II-8 a prova. Os dois guardas compõem: o da P0.8 trata
 * dos rótulos restritos de tenant; este trata dos proibidos. Nenhum
 * medidor existente na base usa rótulo proibido — a guarda é aditiva e
 * não muda o comportamento de nenhuma série actual.
 *
 * <p>O registo como bean vive em {@link ObservabilityGoLiveConfig}.
 */
public final class CardinalityGuard implements MeterFilter {

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        List<String> keys = new ArrayList<>();
        for (Tag tag : id.getTags()) {
            keys.add(tag.getKey());
        }
        return MetricLabelPolicy.hasForbiddenLabel(keys)
                ? MeterFilterReply.DENY
                : MeterFilterReply.NEUTRAL;
    }
}

package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.certification.governance.LegalRequirement;
import br.com.ziipsan.core.quality.MonthlyClosing;
import br.com.ziipsan.core.watersystems.WaterSystem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-80 para as dependencias REAIS — consumo por
 * referencia, sem duplicar:
 * <ul>
 *   <li>{@link SystemsPort} — sistemas SAA/SES da ED-79 (a captacao
 *       referencia o sistema; manancial declarado vive la);</li>
 *   <li>{@link LegalRequirementsPort} — requisitos legais da P3.31
 *       (a ligacao outorga/licenca se ancora num requisito legal
 *       registrado do tenant, nao numa afirmacao solta);</li>
 *   <li>{@link RawWaterQualityPort} — qualidade operacional da
 *       ED-50: as linhas do fechamento mensal dela
 *       ({@code MonthlyClosing.ParameterLine}) para o ponto de
 *       captacao; a ED-80 le, nao reavalia qualidade.</li>
 * </ul>
 * O catalogo/automonitoramento/DBO da ED-105 nao entram por porta:
 * sao consumidos como classes reais nos servicos (DCP e biossolido).
 */
public final class SewerRawPorts {

    private SewerRawPorts() {}

    public interface SystemsPort {
        Optional<WaterSystem> systemOf(UUID tenantId, String systemCode);
    }

    public interface LegalRequirementsPort {
        Optional<LegalRequirement> requirementOf(UUID tenantId, UUID requirementId);
    }

    public interface RawWaterQualityPort {
        List<MonthlyClosing.ParameterLine> monthlyLinesFor(UUID tenantId, String samplingPointCode, int year, int month);
    }
}

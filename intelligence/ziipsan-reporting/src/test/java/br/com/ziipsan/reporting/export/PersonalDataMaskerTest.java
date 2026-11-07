package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.registry.ReportColumn;
import br.com.ziipsan.reporting.registry.ReportColumnType;
import br.com.ziipsan.reporting.spi.ReportUserContext.MaskCeiling;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataMaskerTest {

    private final ReportColumn documento =
            new ReportColumn("documento_pagador", ReportColumnType.DOCUMENT, true);
    private final ReportColumn canal =
            new ReportColumn("canal", ReportColumnType.TEXT, false);

    @Test
    void mascaraPeloTeto() {
        assertThat(PersonalDataMasker.mask(documento, "12345678900", MaskCeiling.UNMASKED))
                .isEqualTo("12345678900");
        assertThat(PersonalDataMasker.mask(documento, "12345678900", MaskCeiling.PARTIAL))
                .isEqualTo("***00");
        assertThat(PersonalDataMasker.mask(documento, "12345678900", MaskCeiling.FULL))
                .isEqualTo("***");
    }

    @Test
    void colunaNaoPessoalNuncaEMascaradaENuloPassa() {
        assertThat(PersonalDataMasker.mask(canal, "PIX", MaskCeiling.FULL)).isEqualTo("PIX");
        assertThat(PersonalDataMasker.mask(documento, null, MaskCeiling.FULL)).isNull();
        // coluna DOCUMENT mascara mesmo sem a flag personal (defesa em profundidade)
        ReportColumn docSemFlag = new ReportColumn("doc", ReportColumnType.DOCUMENT, false);
        assertThat(PersonalDataMasker.mask(docSemFlag, "12345678900", MaskCeiling.FULL)).isEqualTo("***");
    }
}

package br.com.ziipsan.commercial.fiscal.nfse;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Regras puras da NFS-e avulsa (P3.6): qual etapa vale para um serviço, quando os grupos de
 * IBS e CBS entram na nota e quando a ausência deles gera rejeição. Sem leitura de dados e
 * sem relógio próprio: quem chama informa a data de emissão (o serviço usa {@code Clock}
 * injetado), o que torna cada regra testável por etapa, com relógio fixo.
 */
public final class NfseEtapaRule {

    /**
     * Último dia em que a ausência dos grupos de IBS e CBS NÃO gera rejeição na NFS-e
     * (nota do CGNFS-e de 07/08/2026, citada no aceite da P3.6). A partir de 01/01/2027 a
     * ausência passa a ser causa de rejeição na regra vigente modelada aqui.
     */
    public static final LocalDate FIM_DA_TOLERANCIA_SEM_IBS_CBS = LocalDate.of(2026, 12, 31);

    private NfseEtapaRule() {
    }

    /**
     * Etapa aplicável: a do tipo de serviço, salvo se o prestador é optante do Simples
     * Nacional — nesse caso vale {@link NfseEtapa#SIMPLES_NACIONAL} (01/01/2027),
     * independentemente da etapa do tipo.
     *
     * @throws NullPointerException se a etapa do tipo for nula: tipo de serviço avulso sem
     *                              etapa registrada é erro de cadastro, nunca um default.
     */
    public static NfseEtapa etapaAplicavel(NfseEtapa etapaDoTipo, boolean prestadorOptanteSimples) {
        Objects.requireNonNull(etapaDoTipo,
                "Tipo de serviço avulso sem etapa de obrigatoriedade registrada (Ato Conjunto RFB/CGIBS 4/2026)");
        return prestadorOptanteSimples ? NfseEtapa.SIMPLES_NACIONAL : etapaDoTipo;
    }

    /**
     * A NFS-e emitida na data da etapa ou depois sai com os grupos de IBS e CBS; emitida
     * antes, sai sem eles.
     */
    public static boolean incluiGruposIbsCbs(LocalDate dataEmissao, NfseEtapa etapaAplicavel) {
        Objects.requireNonNull(dataEmissao, "Data de emissão é obrigatória");
        Objects.requireNonNull(etapaAplicavel, "Etapa aplicável é obrigatória");
        return !dataEmissao.isBefore(etapaAplicavel.inicioObrigatoriedade());
    }

    /**
     * Regra de rejeição vigente: sem os grupos de IBS e CBS, a NFS-e só é rejeitada quando a
     * emissão é posterior a {@link #FIM_DA_TOLERANCIA_SEM_IBS_CBS} (31/12/2026). Até essa
     * data, inclusive, a ausência não gera rejeição — mesmo para serviço cuja etapa já
     * venceu (a etapa manda incluir; a tolerância é o que a produção restrita não rejeita).
     * Com os grupos presentes, nunca há rejeição por este motivo.
     */
    public static boolean ausenciaDeIbsCbsGeraRejeicao(LocalDate dataEmissao, boolean comGruposIbsCbs) {
        Objects.requireNonNull(dataEmissao, "Data de emissão é obrigatória");
        if (comGruposIbsCbs) {
            return false;
        }
        return dataEmissao.isAfter(FIM_DA_TOLERANCIA_SEM_IBS_CBS);
    }
}

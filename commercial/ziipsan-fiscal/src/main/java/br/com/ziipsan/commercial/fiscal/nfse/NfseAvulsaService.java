package br.com.ziipsan.commercial.fiscal.nfse;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.core.json.Json;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gera a NFS-e padrão nacional de um serviço avulso executado por uma ordem de serviço
 * (P3.6; deps P1.8 — motor de OS do core — e P1.39 — {@link DocumentNumber}).
 *
 * <p>O que este serviço faz aqui, de forma verificável: valida prestador e tomador com a
 * validação de CNPJ alfanumérico da P1.39 (sem duplicar validador), resolve a etapa do Ato
 * Conjunto RFB/CGIBS 4/2026 do tipo de serviço, decide a inclusão dos grupos de IBS e CBS
 * pela data de emissão lida do {@link Clock} injetado, confere a regra de rejeição vigente
 * e monta o payload da DPS em JSON exclusivamente com {@link Json} do core.
 *
 * <p>O que ele NÃO faz: transmitir à produção restrita da NFS-e nacional. A transmissão
 * real (assinatura, endpoint, retorno do ambiente nacional) não é testável neste pacote e
 * fica atrás da porta {@link NfseNacionalGateway}; sem uma implementação ligada, a nota
 * nasce {@link Status#GERADA_NAO_TRANSMITIDA} — nunca "autorizada" por presunção.
 *
 * <p>Nenhum campo tem default silencioso: comando incompleto é
 * {@link IllegalArgumentException} explícito, na linha da regra do projeto de que ausência
 * de dado é erro, não valor presumido. Alíquotas/valores de IBS e CBS NÃO são inventados
 * aqui: a spec da P3.6 só exige a presença dos grupos a partir da etapa; os valores, quando
 * existirem, virão do cadastro do serviço/prestador e são fora do escopo desta tarefa.
 */
public final class NfseAvulsaService {

    /** Ambiente da NFS-e nacional tratado nesta tarefa: somente produção restrita. */
    public enum Ambiente { PRODUCAO_RESTRITA }

    public enum Status { GERADA_NAO_TRANSMITIDA }

    /**
     * @param serviceOrderId        OS que executou o serviço avulso (P1.8)
     * @param tipoServicoCodigo     código do tipo de serviço avulso cadastrado
     * @param etapaDoTipo           etapa registrada para o tipo (Ato Conjunto 4/2026)
     * @param prestadorCnpj         CNPJ do prestador (numérico ou alfanumérico, P1.39)
     * @param prestadorSimples      prestador optante do Simples Nacional
     * @param tomadorCnpj           CNPJ do tomador (numérico ou alfanumérico, P1.39)
     * @param descricaoServico      discriminação do serviço prestado
     * @param valorServico          valor do serviço (maior que zero)
     * @param codigoMunicipioIbge   município da prestação (código IBGE de 7 dígitos)
     */
    public record Comando(UUID serviceOrderId, String tipoServicoCodigo, NfseEtapa etapaDoTipo,
                          String prestadorCnpj, boolean prestadorSimples, String tomadorCnpj,
                          String descricaoServico, BigDecimal valorServico, String codigoMunicipioIbge) {
    }

    /**
     * @param payloadDpsJson payload da DPS serializado com {@link Json} do core
     * @param rejeitadaPorAusenciaDeIbsCbs resultado da regra de rejeição vigente na data
     */
    public record NfseGerada(UUID serviceOrderId, String tipoServicoCodigo, NfseEtapa etapaAplicavel,
                             LocalDate dataEmissao, boolean comGruposIbsCbs,
                             boolean rejeitadaPorAusenciaDeIbsCbs, Ambiente ambiente, Status status,
                             String prestadorCnpjNormalizado, String tomadorCnpjNormalizado,
                             String payloadDpsJson) {
    }

    /** Porta de transmissão ao ambiente nacional. Sem implementação ligada, nada é transmitido. */
    public interface NfseNacionalGateway {
        void transmitir(NfseGerada nfse);
    }

    private final Clock clock;

    public NfseAvulsaService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock é obrigatório (relógio injetável)");
    }

    public NfseGerada gerar(Comando cmd) {
        Objects.requireNonNull(cmd, "Comando de geração da NFS-e é obrigatório");
        if (cmd.serviceOrderId() == null) {
            throw new IllegalArgumentException("A NFS-e avulsa exige a ordem de serviço de origem");
        }
        if (cmd.tipoServicoCodigo() == null || cmd.tipoServicoCodigo().isBlank()) {
            throw new IllegalArgumentException("Tipo de serviço avulso é obrigatório");
        }
        if (!DocumentNumber.isValidCnpj(cmd.prestadorCnpj())) {
            throw new IllegalArgumentException("CNPJ do prestador inválido (validação P1.39, CNPJ alfanumérico)");
        }
        if (!DocumentNumber.isValidCnpj(cmd.tomadorCnpj())) {
            throw new IllegalArgumentException("CNPJ do tomador inválido (validação P1.39, CNPJ alfanumérico)");
        }
        if (cmd.descricaoServico() == null || cmd.descricaoServico().isBlank()) {
            throw new IllegalArgumentException("Discriminação do serviço é obrigatória");
        }
        if (cmd.valorServico() == null || cmd.valorServico().signum() <= 0) {
            throw new IllegalArgumentException("Valor do serviço deve ser maior que zero");
        }
        if (cmd.codigoMunicipioIbge() == null || !cmd.codigoMunicipioIbge().matches("\\d{7}")) {
            throw new IllegalArgumentException("Município da prestação exige código IBGE de 7 dígitos");
        }

        NfseEtapa etapa = NfseEtapaRule.etapaAplicavel(cmd.etapaDoTipo(), cmd.prestadorSimples());
        LocalDate emissao = LocalDate.now(clock);
        boolean comGrupos = NfseEtapaRule.incluiGruposIbsCbs(emissao, etapa);
        boolean rejeitada = NfseEtapaRule.ausenciaDeIbsCbsGeraRejeicao(emissao, comGrupos);

        String prestador = DocumentNumber.normalize(cmd.prestadorCnpj());
        String tomador = DocumentNumber.normalize(cmd.tomadorCnpj());

        Map<String, Object> dps = new LinkedHashMap<>();
        dps.put("ambiente", Ambiente.PRODUCAO_RESTRITA.name());
        dps.put("ordemDeServicoId", cmd.serviceOrderId().toString());
        dps.put("tipoServico", cmd.tipoServicoCodigo());
        dps.put("dataEmissao", emissao.toString());
        dps.put("prestadorCnpj", prestador);
        dps.put("tomadorCnpj", tomador);
        dps.put("municipioPrestacaoIbge", cmd.codigoMunicipioIbge());
        dps.put("descricaoServico", cmd.descricaoServico());
        dps.put("valorServico", cmd.valorServico());
        dps.put("etapaIbsCbs", etapa.name());
        dps.put("etapaInicio", etapa.inicioObrigatoriedade().toString());
        // Grupos de IBS e CBS: presentes (como grupos declarados) só a partir da etapa.
        // Valores/alíquotas não são inventados nesta tarefa — ver javadoc da classe.
        dps.put("gruposIbsCbsPresentes", comGrupos);

        return new NfseGerada(cmd.serviceOrderId(), cmd.tipoServicoCodigo(), etapa, emissao, comGrupos,
                rejeitada, Ambiente.PRODUCAO_RESTRITA, Status.GERADA_NAO_TRANSMITIDA,
                prestador, tomador, Json.write(dps));
    }
}

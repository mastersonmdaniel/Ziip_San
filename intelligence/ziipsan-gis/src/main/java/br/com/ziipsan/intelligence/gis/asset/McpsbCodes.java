package br.com.ziipsan.intelligence.gis.asset;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.ControlMode;

/**
 * Codigos MCPSB da NR 16/2026 da ANA (Res. ANA 302/2026) gravados SO
 * no ativo (P1.35), com os formatos que a spec da P1.35 fixa:
 * {@code cib} 25 posicoes (so bem individual), {@code loc_ibge} 7,
 * {@code ac} 6, {@code nts_sds} um dos 19 codigos, {@code up},
 * {@code uar} 7 (atribuido pela agencia), {@code at1}..{@code at5}
 * com {@code 00} quando nao se aplica, modo de controle
 * ({@code INDIVIDUAL}/{@code MASS}) e unidade de medida.
 *
 * <p><b>[SEM FONTE] declarados:</b> (a) a LISTA dos 19 codigos
 * {@code nts_sds} nao esta nas fontes locais — este record exige que
 * o codigo pertença ao conjunto entregue pela fiacao a partir da
 * tabela original do MCPSB; sem conjunto, nenhum codigo e aceito
 * como valido (a validacao acusa, nao presume); (b) o tamanho de
 * {@code up} (2 ou 3 digitos) a P1.35 manda conferir no manual antes
 * da migration — aqui aceitam-se 2 ou 3 digitos e a pendencia fica
 * nomeada; (c) a VIDA UTIL por perfil de agencia so vira dado depois
 * de conferida na tabela original do MCPSB: {@link UsefulLifeProfile}
 * e o mecanismo, e NENHUM perfil e semeado nesta entrega.
 */
public record McpsbCodes(String cib, String locIbge, String ac, String ntsSds,
                         String up, String uar, List<String> at,
                         ControlMode controlMode, String measureUnit,
                         String subdivisionCode, Double extensionMeters) {

    public McpsbCodes {
        at = at == null ? List.of() : List.copyOf(at);
        if (at.size() > 5) throw new IllegalArgumentException("at1..at5: no maximo 5 posicoes");
        for (String v : at) {
            if (v == null || v.length() != 2)
                throw new IllegalArgumentException("cada AT tem 2 posicoes ('00' quando nao se aplica): " + v);
        }
        if (cib != null) {
            if (cib.length() != 25) throw new IllegalArgumentException("cib tem 25 posicoes: " + cib);
            if (controlMode != ControlMode.INDIVIDUAL)
                throw new IllegalArgumentException("cib so existe em bem individual (controle INDIVIDUAL)");
        }
        if (locIbge != null && locIbge.length() != 7)
            throw new IllegalArgumentException("loc_ibge tem 7 posicoes: " + locIbge);
        if (ac != null && ac.length() != 6)
            throw new IllegalArgumentException("ac tem 6 posicoes: " + ac);
        if (uar != null && uar.length() != 7)
            throw new IllegalArgumentException("uar tem 7 posicoes: " + uar);
        if (up != null && !(up.length() == 2 || up.length() == 3))
            throw new IllegalArgumentException("up tem 2 ou 3 digitos (tamanho a conferir no manual do MCPSB): " + up);
        if (extensionMeters != null) {
            if (extensionMeters < 0) throw new IllegalArgumentException("extensao negativa");
            if (subdivisionCode == null || subdivisionCode.isBlank())
                throw new IllegalArgumentException("extensao exige a subdivisao da NR 16/2026");
        }
    }

    /** Codigos minimos (tudo pendente de classificacao do tenant/agencia). */
    public static McpsbCodes empty() {
        return new McpsbCodes(null, null, null, null, null, null, List.of(), null, null, null, null);
    }

    /**
     * Confere o {@code nts_sds} contra a lista dos 19 codigos da
     * tabela original do MCPSB. Lista vazia/nula = [SEM FONTE]:
     * a conferencia NAO e possivel e o metodo devolve falso com o
     * motivo registravel por {@link #ntsSdsCheckNote}.
     */
    public boolean ntsSdsInOfficialList(List<String> official19) {
        if (ntsSds == null) return false;
        return official19 != null && official19.contains(ntsSds);
    }

    public String ntsSdsCheckNote(List<String> official19) {
        if (official19 == null || official19.isEmpty())
            return "[SEM FONTE] lista dos 19 codigos nts_sds nao disponivel nesta entrega; conferencia pendente (tabela original do MCPSB)";
        return ntsSdsInOfficialList(official19) ? "OK" : "nts_sds fora da lista oficial do MCPSB";
    }

    /**
     * Perfil de vida util por agencia: par UP/UAR -> anos, carregado
     * da tabela original do MCPSB conferida (mecanismo). Sem perfil
     * ou sem par correspondente, a vida util fica AUSENTE.
     */
    public record UsefulLifeProfile(String agencyProfile, Map<String, Integer> yearsByUpUar) {
        public UsefulLifeProfile {
            if (agencyProfile == null || agencyProfile.isBlank())
                throw new IllegalArgumentException("perfil de agencia obrigatorio");
            yearsByUpUar = yearsByUpUar == null ? Map.of() : Map.copyOf(yearsByUpUar);
            yearsByUpUar.forEach((k, v) -> {
                if (v == null || v <= 0) throw new IllegalArgumentException("vida util do perfil deve ser positiva: " + k);
            });
        }

        public Optional<Integer> usefulLifeFor(String up, String uar) {
            if (up == null || uar == null) return Optional.empty();
            return Optional.ofNullable(yearsByUpUar.get(up + "/" + uar));
        }
    }

    /** Aplica o perfil ao ativo: devolve os anos ou vazio (nunca inventa). */
    public Optional<Integer> usefulLifeFrom(UsefulLifeProfile profile) {
        Objects.requireNonNull(profile, "perfil de agencia e obrigatorio para derivar a vida util");
        return profile.usefulLifeFor(up, uar);
    }
}

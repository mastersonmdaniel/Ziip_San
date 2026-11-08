package br.com.ziipsan.compliance.lgpd.privacy;

import java.util.Map;
import java.util.Set;

/**
 * A privacidade do titular é OBRIGAÇÃO de todo tenant (aceite da P3.40): não depende de
 * pacote (a ED-74 é o que a ISO/IEC 27701 pede ALÉM da lei), nem de flag, nem de parâmetro.
 *
 * <p>Por isso esta classe não lê pacote nenhum nem flag nenhuma: os argumentos existem
 * para o teste do aceite provar exatamente isso — tenant sem a ED-74, sem pacote algum e
 * até com uma suposta flag de desligamento continua com a privacidade ATIVA. Qualquer
 * código futuro que queira condicionar a privacidade a pacote/flag terá de mudar esta
 * classe e quebrar o teste que guarda a obrigação.
 */
public final class PrivacyAvailability {

    private PrivacyAvailability() {
    }

    /**
     * @param tenantPackages pacotes/verticais ativos do tenant (ignorado por desenho)
     * @param flags          flags do tenant (ignoradas por desenho)
     * @return sempre {@code true}: a obrigação é legal, não comercial
     */
    public static boolean isActive(Set<String> tenantPackages, Map<String, String> flags) {
        return true;
    }
}

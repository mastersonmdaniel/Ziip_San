package br.com.ziipsan.core.access;

/**
 * Modo de uma sessão de acesso de plataforma a um tenant (P3.17; ADR-0020).
 *
 * <ul>
 *   <li>{@code ASSISTED}: acesso assistido — o atendente do Ziip só entra no
 *       contexto do tenant com o consentimento vigente dele, vinculado na
 *       sessão ({@code consentId});</li>
 *   <li>{@code EMERGENCY}: acesso de emergência (break-glass) — sem
 *       consentimento, mas só com incidente de segurança declarado, só por
 *       {@code PLATFORM_OWNER} e só com reautenticação MFA no momento da
 *       abertura; a sessão é somente leitura e fica registrada no registro
 *       do incidente.</li>
 * </ul>
 */
public enum AccessMode {
    ASSISTED,
    EMERGENCY
}

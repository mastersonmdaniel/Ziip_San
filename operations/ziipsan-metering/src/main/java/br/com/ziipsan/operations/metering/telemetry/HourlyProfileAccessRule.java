package br.com.ziipsan.operations.metering.telemetry;

/**
 * O acesso à curva horária (ED-17) tem dois usos distintos, e o parâmetro
 * {@code meter.hourly_profile_visible_to_customer} governa só um deles:
 *
 * <ul>
 *   <li><b>exibição ao usuário</b>: a curva aparece para o consumidor somente
 *       com o parâmetro ligado;</li>
 *   <li><b>prova na revisão por vazamento oculto</b>: a curva é prova na
 *       revisão da conta qualquer que seja o parâmetro — desligar a exibição
 *       não apaga a prova que a operação usa para decidir a revisão.</li>
 * </ul>
 */
public final class HourlyProfileAccessRule {

    private HourlyProfileAccessRule() {
    }

    public static boolean visibleToCustomer(boolean hourlyProfileVisibleToCustomer) {
        return hourlyProfileVisibleToCustomer;
    }

    /** A curva horária serve de prova na revisão por vazamento oculto, com ou sem exibição ao usuário. */
    public static boolean usableAsLeakReviewEvidence(boolean hourlyProfileVisibleToCustomer) {
        return true;
    }
}

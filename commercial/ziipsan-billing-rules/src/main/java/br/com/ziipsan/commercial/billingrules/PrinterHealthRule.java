package br.com.ziipsan.commercial.billingrules;

/**
 * Regra do teste de impressora antes da rota (ED-19: "teste de cabecote e
 * bateria"): o aparelho le o nivel de bateria e o resultado do autotest do
 * cabecote (comandos da Zebra ZQ) e ESTA regra decide se a rota pode sair
 * imprimindo. A leitura fisica (Bluetooth, resposta da impressora) e do
 * plugin Android e o ensaio com a impressora real e pendencia de hardware —
 * aqui vive so o veredito, igual em qualquer JVM.
 */
public final class PrinterHealthRule {

    /** Desfecho do teste pre-rota. */
    public enum Verdict {
        /** Cabecote ok e bateria acima do minimo: pode imprimir. */
        READY,
        /** Bateria abaixo do minimo configurado: trocar/carregar antes. */
        LOW_BATTERY,
        /** Autotest do cabecote falhou: nao imprimir conta fiscal neste aparelho. */
        HEAD_FAILED,
        /** Sem leitura da impressora (desconectada/sem resposta). */
        NO_READING
    }

    private PrinterHealthRule() {
    }

    /**
     * @param headTestPassed {@code null} = sem leitura do autotest
     * @param batteryPct     {@code null} = sem leitura de bateria
     * @param minBatteryPct  minimo configurado pelo tenant para sair em rota
     */
    public static Verdict evaluate(Boolean headTestPassed, Integer batteryPct, int minBatteryPct) {
        if (minBatteryPct < 0 || minBatteryPct > 100) {
            throw new IllegalArgumentException("Minimo de bateria fora de [0, 100]: " + minBatteryPct);
        }
        if (headTestPassed == null || batteryPct == null) {
            return Verdict.NO_READING;
        }
        if (batteryPct < 0 || batteryPct > 100) {
            throw new IllegalArgumentException("Nivel de bateria fora de [0, 100]: " + batteryPct);
        }
        if (!headTestPassed) {
            return Verdict.HEAD_FAILED;
        }
        if (batteryPct < minBatteryPct) {
            return Verdict.LOW_BATTERY;
        }
        return Verdict.READY;
    }
}

package br.com.ziipsan.intelligence.gis.export;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Devolução da base ao fim do contrato (§9.4; RNF-42; ADR-0025):
 * cadastro em GeoPackage e Shapefile, catálogo, listas de valores,
 * histórico, anexos, dicionário de dados gerado do schema e
 * metadados MGB, em 10 dias úteis no nível Setor Público. O ENSAIO
 * de saída (P3.16) usa este manifesto para conferir completude: item
 * faltando = devolução incompleta, sem exceção silenciosa.
 */
public final class GisReturnOfBase {

    public enum Item { GPKG, SHP, CATALOG, VALUE_LISTS, HISTORY, ATTACHMENTS, DATA_DICTIONARY, MGB_METADATA }

    private GisReturnOfBase(){}

    public static Set<Item> required(){ return EnumSet.allOf(Item.class); }

    public static Set<Item> missing(Set<Item> delivered){
        Set<Item> m = EnumSet.allOf(Item.class); m.removeAll(delivered); return m;
    }

    public static boolean complete(Set<Item> delivered){ return missing(delivered).isEmpty(); }

    /** Prazo RNF-42: 10 dias úteis a partir da data de referência (sábado/domingo fora; feriados são da integração). */
    public static LocalDate deadline(LocalDate start){
        LocalDate d = start; int counted = 0;
        while (counted < 10) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) counted++;
        }
        return d;
    }

    public static boolean withinDeadline(LocalDate start, LocalDate deliveredOn){
        return !deliveredOn.isAfter(deadline(start));
    }
}

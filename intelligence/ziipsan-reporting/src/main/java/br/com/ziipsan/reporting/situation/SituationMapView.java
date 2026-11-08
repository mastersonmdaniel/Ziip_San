package br.com.ziipsan.reporting.situation;

import java.util.List;
import java.util.Objects;

/**
 * ED-93: o modelo do MAPA que a tela do console consome. Marcador so
 * nasce de linha COM coordenadas na tabela de estado; linha sem
 * posicao e contada em {@code unpositionedCount} (e continua visivel
 * nas listas do retrato) — nunca e colocada em coordenada presumida
 * (0,0 incluido). A renderizacao web do mapa e da integracao
 * (pendencia P-ED93-01); este e o contrato de dados dela.
 */
public record SituationMapView(List<Marker> markers, int unpositionedCount) {

    public SituationMapView {
        markers = List.copyOf(markers);
        if (unpositionedCount < 0) {
            throw new IllegalArgumentException("contagem de nao posicionados negativa");
        }
    }

    public record Marker(SituationItemKind kind, String itemRef, String title, String statusLabel,
                         boolean attention, double latitude, double longitude) {
    }

    public static SituationMapView fromSnapshot(SituationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "retrato obrigatorio");
        List<Marker> markers = new java.util.ArrayList<>();
        int unpositioned = 0;
        for (SituationStateRow row : snapshot.allRows()) {
            if (row.positioned()) {
                markers.add(new Marker(row.kind(), row.itemRef(), row.title(), row.statusLabel(),
                        row.attention(), row.latitudeOrNull(), row.longitudeOrNull()));
            } else {
                unpositioned++;
            }
        }
        return new SituationMapView(markers, unpositioned);
    }
}

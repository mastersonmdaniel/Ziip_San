package br.com.ziipsan.intelligence.gis.export;

import java.util.List;
import java.util.Map;

/**
 * Feição neutra de exportação/OGC da ED-67 (GIS-16/GIS-17): o que os
 * serializadores e os serviços OGC consomem, sem depender de uma
 * tabela física. A geometria trafega em coordenadas de intercâmbio
 * (lon/lat, RFC 7946) já convertidas pelo serviço de leitura do
 * tenant (mesma fonte dos tiles da GIS-06); {@code geometryType} é
 * {@code POINT}, {@code LINESTRING} ou {@code POLYGON} e
 * {@code coordinates} é a lista de posições [lon, lat] (anel único
 * no polígono — limitação declarada no relatório).
 *
 * <p>Os marcadores são os do §12 do GIS-CADASTRO-TECNICO:
 * {@code personal} (imóvel/ligação, §12 itens 2/3/5),
 * {@code criticalInfrastructure} (§12 item 7) e
 * {@code sensitiveMarker} (marcador de eletrodependente, §12 item 4:
 * nunca em exportação aberta). {@code cellKey} é a quadra ou o setor
 * da agregação externa (§12 item 3).
 */
public record GisExportFeature(
        String id,
        String layerKey,
        String typeCode,
        String geometryType,
        List<List<Double>> coordinates,
        Map<String, Object> properties,
        boolean personal,
        boolean criticalInfrastructure,
        boolean sensitiveMarker,
        String cellKey,
        String lifecycleStatus) {

    public GisExportFeature {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("feição sem id");
        if (layerKey == null || layerKey.isBlank()) throw new IllegalArgumentException("feição sem camada");
        if (!List.of("POINT", "LINESTRING", "POLYGON").contains(geometryType))
            throw new IllegalArgumentException("geometryType não suportado: " + geometryType);
        if (coordinates == null || coordinates.isEmpty())
            throw new IllegalArgumentException("feição sem coordenadas: " + id);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        coordinates = List.copyOf(coordinates);
    }
}

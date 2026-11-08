package br.com.ziipsan.intelligence.gis.export;

/**
 * Formatos da GIS-16 (§9.3). {@code binary} marca os formatos cuja
 * escrita real é binária e depende de GDAL (GeoPackage 1.4 e
 * Shapefile): eles saem pela porta {@code GisBinaryExportPort} da
 * integração — esta entrega NÃO finge um GPKG/SHP escrevendo texto
 * com a extensão trocada.
 */
public enum GisExportFormats {
    GPKG("application/geopackage+sqlite3", "gpkg", true),
    SHP("application/x-esri-shape", "zip", true),
    DXF("application/dxf", "dxf", false),
    KML("application/vnd.google-earth.kml+xml", "kml", false),
    GEOJSON("application/geo+json", "geojson", false),
    CSV("text/csv", "csv", false);

    private final String contentType; private final String extension; private final boolean binary;
    GisExportFormats(String c, String e, boolean b){contentType=c;extension=e;binary=b;}
    public String contentType(){return contentType;}
    public String extension(){return extension;}
    public boolean binary(){return binary;}
}

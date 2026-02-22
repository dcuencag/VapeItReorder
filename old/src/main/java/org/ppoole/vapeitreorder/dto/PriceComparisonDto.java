package org.ppoole.vapeitreorder.dto;

import java.util.List;

public class PriceComparisonDto {

    private String sku;
    private String nombre;
    private int cantidadAPedir;
    private List<PriceOptionDto> opciones;

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getCantidadAPedir() {
        return cantidadAPedir;
    }

    public void setCantidadAPedir(int cantidadAPedir) {
        this.cantidadAPedir = cantidadAPedir;
    }

    public List<PriceOptionDto> getOpciones() {
        return opciones;
    }

    public void setOpciones(List<PriceOptionDto> opciones) {
        this.opciones = opciones;
    }
}

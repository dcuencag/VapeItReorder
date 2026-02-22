package org.ppoole.vapeitreorder.playtest.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemDto {

    private String sku;
    private int unidadesActuales;
    private int minimoUnidades;
    private int maximoUnidades;

    public boolean needsReorder() {
        return unidadesActuales < minimoUnidades;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getUnidadesActuales() {
        return unidadesActuales;
    }

    public void setUnidadesActuales(int unidadesActuales) {
        this.unidadesActuales = unidadesActuales;
    }

    public int getMinimoUnidades() {
        return minimoUnidades;
    }

    public void setMinimoUnidades(int minimoUnidades) {
        this.minimoUnidades = minimoUnidades;
    }

    public int getMaximoUnidades() {
        return maximoUnidades;
    }

    public void setMaximoUnidades(int maximoUnidades) {
        this.maximoUnidades = maximoUnidades;
    }
}

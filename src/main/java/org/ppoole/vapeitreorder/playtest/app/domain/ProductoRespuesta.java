package org.ppoole.vapeitreorder.playtest.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;

public class ProductoRespuesta {

    public ProductoRespuesta(String sku, String nombre, Double precio, String url, String distribuidora) {
        this.sku = sku;
        this.nombre = nombre;
        this.precio = precio;
        this.url = url;
        this.distribuidora = distribuidora;
    }



    private String sku;

    private String nombre;

    private Double precio;

    private String url;

    private String distribuidora;


    public void setSku(String sku) {
        this.sku = sku;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getString() {
        return distribuidora;
    }

    public void setString(String distribuidora) {
        this.distribuidora = distribuidora;
    }

    public String getSku() {
            return sku;
        }

    public String getNombre() {
            return nombre;
        }

    public void setNombre(String nombre) {
            this.nombre = nombre;
        }

}

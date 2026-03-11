package org.ppoole.vapeitreorder.playtest.app.domain;

import java.util.ArrayList;
import java.util.List;

public class ProductoPrioridades {

    private String sku;
    private String nombre;
    private int cantidadComprar;
    private List<String> urls;
    private List<String> distribuidoras;

    public ProductoPrioridades() {
        this.urls = new ArrayList<>();
        this.distribuidoras = new ArrayList<>();
    }

    public ProductoPrioridades(String sku, String nombre, int cantidadComprar, List<String> urls, List<String> distribuidoras) {
        this.sku = sku;
        this.nombre = nombre;
        this.cantidadComprar = cantidadComprar;
        this.urls = urls == null ? new ArrayList<>() : new ArrayList<>(urls);
        this.distribuidoras = distribuidoras == null ? new ArrayList<>() : new ArrayList<>(distribuidoras);
    }

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

    public int getCantidadComprar() {
        return cantidadComprar;
    }

    public void setCantidadComprar(int cantidadComprar) {
        this.cantidadComprar = cantidadComprar;
    }

    public List<String> getUrls() {
        return new ArrayList<>(urls);
    }

    public void setUrls(List<String> urls) {
        this.urls = urls == null ? new ArrayList<>() : new ArrayList<>(urls);
    }

    public void addUrl(String url) {
        this.urls.add(url);
    }

    public List<String> getDistribuidoras() {
        return new ArrayList<>(distribuidoras);
    }

    public void setDistribuidoras(List<String> distribuidoras) {
        this.distribuidoras = distribuidoras == null ? new ArrayList<>() : new ArrayList<>(distribuidoras);
    }

    public void addDistribuidora(String distribuidora) {
        this.distribuidoras.add(distribuidora);
    }

    @Override
    public String toString() {
        return "SKU: " + sku + " | " + nombre + " | Cantidad: " + cantidadComprar + " | URLs: " + urls + " | Distribuidoras: " + distribuidoras;
    }
}

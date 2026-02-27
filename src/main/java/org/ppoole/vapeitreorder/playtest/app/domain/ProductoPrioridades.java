package org.ppoole.vapeitreorder.playtest.app.domain;

import java.util.ArrayList;
import java.util.List;

public class ProductoPrioridades {

    private String sku;
    private String nombre;
    private List<String> urls;

    public ProductoPrioridades() {
        this.urls = new ArrayList<>();
    }

    public ProductoPrioridades(String sku, String nombre, List<String> urls) {
        this.sku = sku;
        this.nombre = nombre;
        this.urls = urls == null ? new ArrayList<>() : new ArrayList<>(urls);
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

    public List<String> getUrls() {
        return new ArrayList<>(urls);
    }

    public void setUrls(List<String> urls) {
        this.urls = urls == null ? new ArrayList<>() : new ArrayList<>(urls);
    }

    public void addUrl(String url) {
        this.urls.add(url);
    }
}

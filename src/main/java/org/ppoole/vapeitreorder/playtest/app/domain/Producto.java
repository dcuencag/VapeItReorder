package org.ppoole.vapeitreorder.playtest.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "PRODUCTO")
public class Producto {

    @Id
    @Column(name = "SKU", nullable = false, updatable = false)
    private String sku;

    @Column(name = "NOMBRE", nullable = false)
    private String nombre;

    protected Producto() {
    }

    public Producto(String sku, String nombre) {
        this.sku = sku;
        this.nombre = nombre;
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

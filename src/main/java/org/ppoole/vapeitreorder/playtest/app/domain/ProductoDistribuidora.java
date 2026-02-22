package org.ppoole.vapeitreorder.playtest.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "PRODUCTO_DISTRIBUIDORA",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_PRODUCTO_DISTRIBUIDORA_SKU_DIST",
                columnNames = {"SKU", "ID_DISTRIBUIDORA"}
        )
)
public class ProductoDistribuidora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SKU", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_DISTRIBUIDORA", nullable = false)
    private Distribuidora distribuidora;

    @Column(name = "URL", nullable = false, length = 2048)
    private String url;

    protected ProductoDistribuidora() {
    }

    public ProductoDistribuidora(Producto producto, Distribuidora distribuidora, String url) {
        this.producto = producto;
        this.distribuidora = distribuidora;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public Distribuidora getDistribuidora() {
        return distribuidora;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

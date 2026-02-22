package org.ppoole.vapeitreorder.dto;

public class OrderSelectionDto {

    private String sku;
    private String distribuidor;
    private Integer cantidadAPedir;

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getDistribuidor() {
        return distribuidor;
    }

    public void setDistribuidor(String distribuidor) {
        this.distribuidor = distribuidor;
    }

    public Integer getCantidadAPedir() {
        return cantidadAPedir;
    }

    public void setCantidadAPedir(Integer cantidadAPedir) {
        this.cantidadAPedir = cantidadAPedir;
    }
}

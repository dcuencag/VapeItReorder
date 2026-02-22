package dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemDto {

    private Long id;
    private String sku;
    private String nombre;
    private Integer minimoUnidades;
    private Integer maximoUnidades;
    private Integer currentStock;
    private String supplierUrl;
    private String distribuidor;
    private String urlProducto;
    private Integer cantidadAPedir;

    public boolean needsReorder() {
        return currentStock != null && minimoUnidades != null && currentStock < minimoUnidades;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getMinimoUnidades() {
        return minimoUnidades;
    }

    public void setMinimoUnidades(Integer minimoUnidades) {
        this.minimoUnidades = minimoUnidades;
    }

    public Integer getMaximoUnidades() {
        return maximoUnidades;
    }

    public void setMaximoUnidades(Integer maximoUnidades) {
        this.maximoUnidades = maximoUnidades;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(Integer currentStock) {
        this.currentStock = currentStock;
    }

    public String getSupplierUrl() {
        return supplierUrl;
    }

    public void setSupplierUrl(String supplierUrl) {
        this.supplierUrl = supplierUrl;
    }

    public String getDistribuidor() {
        return distribuidor;
    }

    public void setDistribuidor(String distribuidor) {
        this.distribuidor = distribuidor;
    }

    public String getUrlProducto() {
        return urlProducto;
    }

    public void setUrlProducto(String urlProducto) {
        this.urlProducto = urlProducto;
    }

    public Integer getCantidadAPedir() {
        return cantidadAPedir;
    }

    public void setCantidadAPedir(Integer cantidadAPedir) {
        this.cantidadAPedir = cantidadAPedir;
    }

    @Override
    public String toString() {
        return "ItemDto{sku='" + sku + "', nombre='" + nombre + "', currentStock=" + currentStock +
                ", minimoUnidades=" + minimoUnidades + ", maximoUnidades=" + maximoUnidades + "}";
    }
}

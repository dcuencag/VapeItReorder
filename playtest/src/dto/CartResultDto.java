package dto;

import java.util.List;

public class CartResultDto {

    private String distribuidor;
    private String timestamp;
    private String status;
    private List<CartItemDto> carrito;
    private List<String> errores;

    public String getDistribuidor() {
        return distribuidor;
    }

    public void setDistribuidor(String distribuidor) {
        this.distribuidor = distribuidor;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<CartItemDto> getCarrito() {
        return carrito;
    }

    public void setCarrito(List<CartItemDto> carrito) {
        this.carrito = carrito;
    }

    public List<String> getErrores() {
        return errores;
    }

    public void setErrores(List<String> errores) {
        this.errores = errores;
    }
}

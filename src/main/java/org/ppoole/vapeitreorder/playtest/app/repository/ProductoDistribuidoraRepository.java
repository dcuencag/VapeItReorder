package org.ppoole.vapeitreorder.playtest.app.repository;

import org.ppoole.vapeitreorder.playtest.app.domain.ProductoDistribuidora;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoDistribuidoraRepository extends JpaRepository<ProductoDistribuidora, Long> {

    List<ProductoDistribuidora> findByProductoSku(String sku);

    List<ProductoDistribuidora> findByDistribuidoraId(Long distribuidoraId);
}

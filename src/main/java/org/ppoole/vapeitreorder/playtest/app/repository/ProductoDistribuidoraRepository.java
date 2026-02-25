package org.ppoole.vapeitreorder.playtest.app.repository;

import org.ppoole.vapeitreorder.playtest.app.domain.ProductoDistribuidora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductoDistribuidoraRepository extends JpaRepository<ProductoDistribuidora, Long> {

    interface SkuUrlDistribuidoraTrio {
        String getSku();

        String getUrl();

        String getDistribuidoraName();
    }

    List<ProductoDistribuidora> findByProductoSku(String sku);

    List<ProductoDistribuidora> findByDistribuidoraId(Long distribuidoraId);

    @Query("""
            select pd.producto.sku as sku, pd.url as url, pd.distribuidora.name as distribuidoraName
            from ProductoDistribuidora pd
            where pd.producto.sku in :skus
            """)
    List<SkuUrlDistribuidoraTrio> findSkuUrlDistribuidoraTriosBySkuIn(@Param("skus") Collection<String> skus);
}

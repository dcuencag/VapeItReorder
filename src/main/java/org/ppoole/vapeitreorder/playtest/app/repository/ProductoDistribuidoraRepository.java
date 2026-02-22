package org.ppoole.vapeitreorder.playtest.app.repository;

import org.ppoole.vapeitreorder.playtest.app.domain.ProductoDistribuidora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductoDistribuidoraRepository extends JpaRepository<ProductoDistribuidora, Long> {

    interface SkuUrlPair {
        String getSku();

        String getUrl();
    }

    List<ProductoDistribuidora> findByProductoSku(String sku);

    List<ProductoDistribuidora> findByDistribuidoraId(Long distribuidoraId);

    @Query("""
            select pd.producto.sku as sku, pd.url as url
            from ProductoDistribuidora pd
            where pd.producto.sku in :skus
            """)
    List<SkuUrlPair> findSkuUrlPairsBySkuIn(@Param("skus") Collection<String> skus);
}

package org.ppoole.vapeitreorder.playtest.app.repository;

import org.ppoole.vapeitreorder.playtest.app.domain.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, String> {
}

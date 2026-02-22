package org.ppoole.vapeitreorder.playtest.app.repository;

import org.ppoole.vapeitreorder.playtest.app.domain.Distribuidora;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DistribuidoraRepository extends JpaRepository<Distribuidora, Long> {

    Optional<Distribuidora> findByName(String name);
}

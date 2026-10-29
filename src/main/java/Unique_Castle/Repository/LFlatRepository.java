package Unique_Castle.Repository;

import Unique_Castle.Entity.L_Flat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface LFlatRepository extends JpaRepository <L_Flat, Long> {
}

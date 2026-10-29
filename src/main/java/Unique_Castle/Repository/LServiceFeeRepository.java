package Unique_Castle.Repository;

import Unique_Castle.Entity.L_ServiceFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface LServiceFeeRepository extends JpaRepository <L_ServiceFee, Long> {

    Optional<L_ServiceFee> findByMonth(LocalDate month);

    boolean existsByMonth(LocalDate month);

}


package Unique_Castle.Repository;

import Unique_Castle.Entity.ServiceFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ServiceFeeRepository extends JpaRepository<ServiceFee, Long> {
    boolean existsByMonth(LocalDate month);

    Optional<ServiceFee> findByMonth(LocalDate month);

    @Query("""
        SELECT s
        FROM ServiceFee s
        WHERE s.month >= :monthStart
          AND s.month <  :monthEnd
    """)
    Optional<ServiceFee> findByCurrentMonth(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd
    );

    @Query("""
        SELECT s
        FROM ServiceFee s
        WHERE s.month >= :monthStart
          AND s.month <  :monthEnd
          AND (s.status = 'PUBLISHED' OR s.status IS NULL)
    """)
    Optional<ServiceFee> findPublishedByMonth(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd
    );

    List<ServiceFee> findAllByOrderByMonthDesc();
}

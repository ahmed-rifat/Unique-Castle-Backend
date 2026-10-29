package Unique_Castle.Repository;

import Unique_Castle.Entity.Flat;
import Unique_Castle.Entity.ServiceFee;
import Unique_Castle.Entity.ServiceFeePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ServiceFeePaymentRepository extends JpaRepository<ServiceFeePayment, Long> {

    @Query("""
        SELECT p
        FROM ServiceFeePayment p
        WHERE p.flat.flatId = :flatId
          AND p.paidDate >= :startDate
          AND p.paidDate <  :endDate
    """)
    Optional<ServiceFeePayment> findPaymentsInMonth(
            @Param("flatId") Long flatId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT p
        FROM ServiceFeePayment p
        WHERE TRIM(LOWER(p.transactionId)) = TRIM(LOWER(:TransactionId))
          AND (
            :PhoneNumber IS NULL 
            OR TRIM(:PhoneNumber) = '' 
            OR TRIM(p.phoneNumber) = TRIM(:PhoneNumber)
            OR TRIM(p.flat.contact) = TRIM(:PhoneNumber)
          )
    """)
    Optional<ServiceFeePayment> PaymentVerification(
            @Param("PhoneNumber") String PhoneNumber,
            @Param("TransactionId") String TransactionId
    );

    Optional<ServiceFeePayment> findFirstByTransactionIdIgnoreCase(String transactionId);

    List<ServiceFeePayment> findByFlat_FlatId(Long flatId);

    List<ServiceFeePayment> findByFlat(Flat flat);

    Optional<ServiceFeePayment> findByFlatAndServiceFee(Flat flat, ServiceFee serviceFee);

    Optional<ServiceFeePayment> findByFlat_FlatIdAndServiceFee_ServiceFeeId(Long flatId, Long serviceFeeId);

    List<ServiceFeePayment> findByServiceFee(ServiceFee serviceFee);

    List<ServiceFeePayment> findByFlat_FlatNumber(String flatNumber);

    Optional<ServiceFeePayment> findFirstByFlat_FlatNumberOrderByServiceFeePaymentIdDesc(String flatNumber);

    Optional<ServiceFeePayment> findFirstByFlat_FlatIdOrderByServiceFeePaymentIdDesc(Long flatId);
}

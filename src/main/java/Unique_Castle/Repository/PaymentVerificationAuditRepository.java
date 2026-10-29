package Unique_Castle.Repository;

import Unique_Castle.Entity.PaymentVerificationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentVerificationAuditRepository extends JpaRepository<PaymentVerificationAudit, Long> {

    List<PaymentVerificationAudit> findByServiceFeePaymentIdOrderByCreatedAtDesc(Long serviceFeePaymentId);

    List<PaymentVerificationAudit> findByFlatNumberOrderByCreatedAtDesc(String flatNumber);

    List<PaymentVerificationAudit> findByFlatIdOrderByCreatedAtDesc(Long flatId);

    List<PaymentVerificationAudit> findByPhoneNumber(String phoneNumber);

    List<PaymentVerificationAudit> findByFlatNumberAndBillingMonthOrderByCreatedAtDesc(String flatNumber, java.time.LocalDate billingMonth);
}

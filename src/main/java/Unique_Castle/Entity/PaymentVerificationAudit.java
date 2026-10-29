package Unique_Castle.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "PAYMENT_VERIFICATION_AUDIT")
public class PaymentVerificationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    private Long serviceFeePaymentId;

    private Long flatId;

    private String flatNumber;

    private Long serviceFeeId;

    private LocalDate billingMonth;

    @Column(nullable = false)
    private String action; // SUBMISSION, REJECTION, RESUBMISSION, APPROVAL

    @Column(nullable = false)
    private String status; // PENDING, REJECTED, PAID

    private String transactionId;

    private String phoneNumber;

    private String paymentMethod;

    @Column(length = 500)
    private String rejectionComment;

    private LocalDate actionDate;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.actionDate == null) {
            this.actionDate = LocalDate.now();
        }
    }
}

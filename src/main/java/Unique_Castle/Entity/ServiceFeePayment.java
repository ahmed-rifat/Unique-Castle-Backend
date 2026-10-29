package Unique_Castle.Entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "SERVICE_FEE_PAYMENT",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"flatId", "serviceFeeId"})
        }
)
public class ServiceFeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long serviceFeePaymentId;

    // Which flat
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flatId", nullable = false)
    private Flat flat;

    // Which month fee
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serviceFeeId", nullable = false)
    private ServiceFee serviceFee;

    // Payment info
    private String paid;   // P = pending, N = not paid, Y = paid

    private LocalDate paidDate; // null if not paid
    @Column(nullable = false)
    private String transactionId; // null if CASH
    @Column(nullable = false)
    private String paymentMethod; // CASH / BKASH / BANK
    @Column(nullable = false)
    private String phoneNumber;
    @Column(nullable = true)
    private LocalDate verificationDate; // null if not verified

    @Column(name = "rejection_comment", length = 500)
    private String rejectionComment; // Admin's rejection reason/comment

    @Column(name = "previous_rejection_comment", length = 500)
    private String previousRejectionComment; // Previous rejection reason preserved upon resubmission

    @Column(name = "previous_transaction_id")
    private String previousTransactionId; // Previous TrxID preserved upon resubmission

    @Column(name = "submission_count")
    private Integer submissionCount; // Number of submissions/resubmissions for this fee

    @Column(name = "rejection_date")
    private LocalDate rejectionDate; // Date when rejection occurred
}


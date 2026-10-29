package Unique_Castle.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "SERVICE_FEE",
        uniqueConstraints = @UniqueConstraint(columnNames = "month")
)
public class ServiceFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long serviceFeeId;

    // Store as first day of the month
    @Column(nullable = false, unique = true)
    private LocalDate month;

    @Column(nullable = false)
    private LocalDate DueDate;

    @Column(nullable = false)
    private BigDecimal caretakerFee;

    @Column(nullable = false)
    private BigDecimal wasteFee;

    @Column(nullable = false)
    private BigDecimal liftFee;

    @Column(nullable = false)
    private BigDecimal waterFee;

    @Column(nullable = false)
    private BigDecimal electricityFee;

    @Column(nullable = false)
    private BigDecimal otherFee;

    @Column(nullable = false)
    private BigDecimal totalFee;

    @Column
    private String status; // "PUBLISHED", "DRAFT"

    @Column
    private LocalDate publishedDate;

    // Automatically calculate total before saving
    @PrePersist
    @PreUpdate
    private void calculateTotalFee() {
        if (caretakerFee == null) caretakerFee = BigDecimal.ZERO;
        if (liftFee == null) liftFee = BigDecimal.ZERO;
        if (wasteFee == null) wasteFee = BigDecimal.ZERO;
        if (waterFee == null) waterFee = BigDecimal.ZERO;
        if (electricityFee == null) electricityFee = BigDecimal.ZERO;
        if (otherFee == null) otherFee = BigDecimal.ZERO;

        BigDecimal sum = caretakerFee
                .add(liftFee)
                .add(wasteFee)
                .add(waterFee)
                .add(electricityFee)
                .add(otherFee);

        if (sum.compareTo(BigDecimal.ZERO) > 0) {
            this.totalFee = sum;
        } else if (this.totalFee == null) {
            this.totalFee = BigDecimal.valueOf(2500);
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "PUBLISHED";
        }
        if ("PUBLISHED".equalsIgnoreCase(this.status) && this.publishedDate == null) {
            this.publishedDate = LocalDate.now();
        }
    }

    @OneToMany(mappedBy = "serviceFee")
    private List<ServiceFeePayment> serviceFeePayments;
}

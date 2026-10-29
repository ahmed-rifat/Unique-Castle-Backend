package Unique_Castle.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "FLATS")
public class Flat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long flatId;

    private String flatNumber;

    private String contact;

    private String residents;

    @Builder.Default
    private String buildingName = "Unique Castle";

    @Builder.Default
    private String occupancyStatus = "Moved In"; // "Moved In", "Not Moved In", "Vacated"

    @Builder.Default
    private String billingEligibility = "Eligible"; // "Eligible", "Ineligible"

    private LocalDate moveInDate;

    @Builder.Default
    private Boolean isArchived = false;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(name = "owner_id")
    private Long ownerId;

    @OneToMany(mappedBy = "flat")
    private List<ServiceFeePayment> serviceFeePayments;

    @PrePersist
    @PreUpdate
    public void syncOwnerId() {
        if (owner != null && owner.getUserId() != null) {
            this.ownerId = owner.getUserId();
        }
    }
}

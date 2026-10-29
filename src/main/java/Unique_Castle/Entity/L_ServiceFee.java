package Unique_Castle.Entity;

import jakarta.persistence.*;
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
@Table(name = "L_SERVICE_FEE")
public class L_ServiceFee {

    @Id
    private Long serviceFeeId;

    // Store as first day of the month
    @Column(nullable = false, unique = true)
    private LocalDate month;

}

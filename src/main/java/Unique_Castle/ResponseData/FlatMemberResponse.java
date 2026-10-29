package Unique_Castle.ResponseData;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlatMemberResponse {
    private Long id;
    private Long userId;
    private Long flatId;
    private String name;
    private String username;
    private String phoneNumber;
    private String email;
    private String flatNumber;
    private String buildingName;
    private String role;
    private String occupancyStatus;
    private String accountStatus;
    private String billingEligibility;
    private LocalDate moveInDate;
    private Boolean isArchived;
    private String notes;
    private Boolean hasFinancialHistory;
}

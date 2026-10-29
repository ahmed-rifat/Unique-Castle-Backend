package Unique_Castle.RequestData;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlatMemberRequest {
    private Long id; // User ID or Member identifier
    private Long userId;
    private Long flatId;

    @NotBlank(message = "Name cannot be empty")
    private String name;

    @NotBlank(message = "Username cannot be empty")
    private String username;

    @NotBlank(message = "Phone number cannot be empty")
    private String phoneNumber;

    private String email;

    @NotBlank(message = "Flat number cannot be empty")
    private String flatNumber;

    private String buildingName;

    private String password;
    private String confirmPassword;

    private String role; // "OWNER", "ADMIN"

    private String occupancyStatus; // "Moved In", "Not Moved In", "Vacated"
    private String accountStatus;   // "Active", "Inactive"
    private String billingEligibility; // "Eligible", "Ineligible"
    private LocalDate moveInDate;

    private String notes;
}

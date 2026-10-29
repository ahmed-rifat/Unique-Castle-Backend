package Unique_Castle.RequestData;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class FlatRequest {

    private Long flatId;

    @NotBlank(message = "Flat number cannot be empty")
    @Size(max = 10, message = "Flat number cannot exceed 10 characters")
    @Column(unique = true, nullable = false)
    private String flatNumber;

    @Pattern(
            regexp = "^[0-9]{11}$",
            message = "Contact must be exactly 11 digits"
    )
    private String contact;

    @NotBlank(message = "Residents count is required")
    @Pattern(regexp = "^[1-9]$|1[0-9]|20", message = "Residents must be between 1 and 20")
    private String residents;
    private Long ownerId; // link to user table
}

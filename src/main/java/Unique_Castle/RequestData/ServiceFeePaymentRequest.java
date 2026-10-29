package Unique_Castle.RequestData;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ServiceFeePaymentRequest {
    private Long serviceFeePaymentId; // null for new, existing id for update
    private String paid; // "Yes" or "No"
    @NotBlank(message = "Payment Method cannot be Empty")
    private String paymentMethod; // "Cash", "Card", "Online"
    private Long flatId;
    private Long serviceFeeId;
    @NotBlank(message = "Transaction ID cannot be Empty")
    private String transactionId; // asdaswe4a
    @NotBlank(message = "Phone Number cannot be Empty")
    @Pattern(
            regexp = "^[0-9]{11}$",
            message = "Contact must be exactly 11 digits"
    )
    private String phoneNumber;
    private String rejectionComment;
    private String comment;
}

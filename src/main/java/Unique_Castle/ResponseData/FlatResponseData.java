package Unique_Castle.ResponseData;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlatResponseData {
    private Long flatId;
    private Long serviceFeeId;
    private LocalDate serviceFeeMonth;
    private String flatNumber;
    private String contact;
    private String residents;
    private Long ownerId;
    private String ownerName;
    private LocalDate paidDate;
    private String paid;
    private String status; // Display status: "Rejected", "Paid", "Pending Verification", "Not Paid"
    private String rejectionComment; // Admin rejection comment
    private String comment;          // Alias for rejectionComment
    private String previousRejectionComment;
    private String previousTransactionId;
    private Integer submissionCount;
    private LocalDate rejectionDate;
    private Boolean canResubmit;
    private String paymentMethod;
    private String transactionId;
    private BigDecimal totalAmount;

    public FlatResponseData(Long flatId, String flatNumber, String contact, String residents, Long userId, String username) {
        this.flatId = flatId;
        this.flatNumber = flatNumber;
        this.contact = contact;
        this.residents = residents;
        this.ownerId = userId;
        this.ownerName = username;
    }
}


package Unique_Castle.ResponseData;

import Unique_Castle.Entity.Flat;
import Unique_Castle.Entity.ServiceFee;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PaymentResponseData {
    private Long serviceFeePaymentId;
    private Long flatId;
    private Long serviceFeeId;
    private String paid;
    private LocalDate paidDate;
    private String transactionId;
    private String paymentMethod;
    private String rejectionComment;
    private String comment;
    private String status;
    private String previousRejectionComment;
    private String previousTransactionId;
    private Integer submissionCount;
    private LocalDate rejectionDate;
    private Boolean canResubmit;
}

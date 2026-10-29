package Unique_Castle.ResponseData;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ServiceFeeMonthResponseData {
    private Long serviceFeeId;
    private LocalDate month;
    private LocalDate dueDate;
    private BigDecimal totalFee;
    private BigDecimal caretakerFee;
    private BigDecimal wasteFee;
    private BigDecimal liftFee;
    private BigDecimal waterFee;
    private BigDecimal electricityFee;
    private BigDecimal otherFee;
    private String status; // "PUBLISHED", "DRAFT"
    private LocalDate publishedDate;
    private boolean hasPayments;
    private int paymentCount;
}

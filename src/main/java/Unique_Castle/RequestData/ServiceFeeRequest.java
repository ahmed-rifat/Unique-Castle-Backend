package Unique_Castle.RequestData;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ServiceFeeRequest {
    private Long serviceFeeId; // null for new, existing id for update
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate month; // 2026-11-01

    @JsonProperty("billingMonth")
    private String billingMonth; // e.g. "November" or "11"

    @JsonProperty("billingYear")
    private Integer billingYear; // e.g. 2026

    @JsonProperty("year")
    private Integer year;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate; // 2026-11-10

    private BigDecimal caretakerFee;
    private BigDecimal wasteFee;
    private BigDecimal liftFee;
    private BigDecimal waterFee;
    private BigDecimal electricityFee;
    private BigDecimal otherFee;
    private BigDecimal totalFee;
    private String status; // "PUBLISHED", "DRAFT"

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishedDate;

    public LocalDate getResolvedMonth() {
        if (this.month != null) {
            return this.month.withDayOfMonth(1);
        }
        int y = (this.year != null && this.year > 0)
                ? this.year
                : ((this.billingYear != null && this.billingYear > 0) ? this.billingYear : LocalDate.now().getYear());
        int m = LocalDate.now().getMonthValue();
        if (this.billingMonth != null && !this.billingMonth.isBlank()) {
            try {
                m = Integer.parseInt(this.billingMonth.trim());
            } catch (NumberFormatException e) {
                String[] monthNames = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
                for (int i = 0; i < monthNames.length; i++) {
                    if (monthNames[i].equalsIgnoreCase(this.billingMonth.trim())) {
                        m = i + 1;
                        break;
                    }
                }
            }
        }
        return LocalDate.of(y, m, 1);
    }
}


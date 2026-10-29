package Unique_Castle.Controller;

import Unique_Castle.Entity.ServiceFee;
import Unique_Castle.RequestData.ServiceFeeRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.ApiResponseData;
import Unique_Castle.Service.ServiceFeeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class ServiceFeeController {

    @Autowired
    private ServiceFeeService serviceFeeService;

    @Autowired
    private Unique_Castle.Repository.FlatRepository flatRepository;

    @Autowired
    private Unique_Castle.Repository.ServiceFeeRepository serviceFeeRepository;

    @Autowired
    private Unique_Castle.Repository.ServiceFeePaymentRepository serviceFeePaymentRepository;

    @PostMapping("/save-update-serviceFee")
    public ApiResponseData saveAndUpdateServiceFee(@Valid @RequestBody ServiceFeeRequest serviceFeeRequest, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errorMessages = bindingResult.getAllErrors()
                    .stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return new ApiResponseData<>("false", errorMessages);
        }
        return serviceFeeService.saveAndUpdateServiceFee(serviceFeeRequest);
    }

    @GetMapping("/get-all-serviceFees")
    public ApiGetResponseData getAllServiceFees() {
        return serviceFeeService.getAllServiceFees();
    }

    @PostMapping("/service-fees/publish/{serviceFeeId}")
    public ApiResponseData publishServiceFee(@PathVariable Long serviceFeeId) {
        ServiceFee fee = serviceFeeRepository.findById(serviceFeeId).orElse(null);
        if (fee == null) {
            return new ApiResponseData<>("error", "Service Fee record not found");
        }
        fee.setStatus("PUBLISHED");
        fee.setPublishedDate(LocalDate.now());
        serviceFeeRepository.save(fee);
        return new ApiResponseData<>("success", "Service Fee for " + fee.getMonth().getMonth() + " " + fee.getMonth().getYear() + " published successfully! Now visible to all residents.");
    }

    @GetMapping("/service-fees/breakdown")
    public ResponseEntity<?> getServiceFeeBreakdown(
            @RequestParam(required = false) String flatNumber,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {

        String flatNum = (flatNumber != null && !flatNumber.isBlank()) ? flatNumber.trim().replaceFirst("(?i)^Flat\\s*", "") : "4B";
        LocalDate now = LocalDate.now();
        int y = (year != null && year > 0) ? year : now.getYear();
        int m = now.getMonthValue();
        String[] monthNames = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        if (month != null && !month.isBlank()) {
            try {
                m = Integer.parseInt(month.trim());
            } catch (NumberFormatException e) {
                for (int i = 0; i < monthNames.length; i++) {
                    if (monthNames[i].equalsIgnoreCase(month.trim())) {
                        m = i + 1;
                        break;
                    }
                }
            }
        }
        LocalDate targetMonth = LocalDate.of(y, m, 1);
        LocalDate currentMonth = now.withDayOfMonth(1);

        // While current month is active, future month charges must not be displayed to residents before that month begins
        if (targetMonth.isAfter(currentMonth)) {
            Map<String, Object> unpub = new HashMap<>();
            unpub.put("flatNumber", flatNum);
            unpub.put("billingMonth", monthNames[m - 1]);
            unpub.put("billingYear", y);
            unpub.put("isPublished", false);
            unpub.put("status", "Unpublished");
            unpub.put("message", "Service charge for " + monthNames[m - 1] + " " + y + " will be visible once the billing month begins.");
            unpub.put("totalCharge", 0.0);
            return ResponseEntity.ok(unpub);
        }

        ServiceFee sf = serviceFeeRepository.findByMonth(targetMonth)
                .or(() -> serviceFeeRepository.findByCurrentMonth(targetMonth, targetMonth.plusMonths(1)))
                .orElse(null);

        if (sf == null) {
            // Default 2500 only for October 2026 baseline if not yet saved in DB
            if (y == 2026 && m == 10) {
                sf = ServiceFee.builder()
                        .month(targetMonth)
                        .DueDate(targetMonth.plusDays(9))
                        .caretakerFee(BigDecimal.valueOf(800))
                        .wasteFee(BigDecimal.valueOf(200))
                        .liftFee(BigDecimal.valueOf(500))
                        .waterFee(BigDecimal.ZERO)
                        .electricityFee(BigDecimal.ZERO)
                        .otherFee(BigDecimal.valueOf(1000))
                        .totalFee(BigDecimal.valueOf(2500))
                        .status("PUBLISHED")
                        .publishedDate(targetMonth)
                        .build();
                sf = serviceFeeRepository.save(sf);
            }
        }

        boolean isPublished = (sf != null && ("PUBLISHED".equalsIgnoreCase(sf.getStatus()) || sf.getStatus() == null));

        if (sf == null || !isPublished) {
            Map<String, Object> unpub = new HashMap<>();
            unpub.put("flatNumber", flatNum);
            unpub.put("billingMonth", monthNames[m - 1]);
            unpub.put("billingYear", y);
            unpub.put("isPublished", false);
            unpub.put("status", "Unpublished");
            unpub.put("message", "Service charge for " + monthNames[m - 1] + " " + y + " has not been published yet.");
            unpub.put("totalCharge", sf != null && sf.getTotalFee() != null ? sf.getTotalFee().doubleValue() : 0.0);
            return ResponseEntity.ok(unpub);
        }

        double total = sf.getTotalFee() != null ? sf.getTotalFee().doubleValue() : 2500.0;
        double paid = 0.0;
        String status = "Not Paid";

        Unique_Castle.Entity.Flat flat = flatRepository.findByFlatNumberIgnoreCase(flatNum).orElse(null);
        if (flat == null) {
            flat = flatRepository.findByFlatNumber(flatNum).orElse(null);
        }

        String rejectionComment = null;
        String prevRejectionComment = null;
        Integer submissionCount = 0;
        if (flat != null) {
            Optional<Unique_Castle.Entity.ServiceFeePayment> paymentOpt = Optional.empty();
            if (sf.getServiceFeeId() != null) {
                paymentOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, sf);
            }
            if (paymentOpt.isEmpty()) {
                paymentOpt = serviceFeePaymentRepository.findPaymentsInMonth(flat.getFlatId(), targetMonth, targetMonth.plusMonths(1));
            }
            if (paymentOpt.isPresent()) {
                Unique_Castle.Entity.ServiceFeePayment p = paymentOpt.get();
                rejectionComment = p.getRejectionComment();
                prevRejectionComment = p.getPreviousRejectionComment();
                submissionCount = p.getSubmissionCount() != null ? p.getSubmissionCount() : 0;
                if ("Y".equalsIgnoreCase(p.getPaid()) || "PAID".equalsIgnoreCase(p.getPaid())) {
                    status = "Paid";
                    paid = total;
                } else if ("P".equalsIgnoreCase(p.getPaid()) || "PENDING".equalsIgnoreCase(p.getPaid())) {
                    status = "Pending Verification";
                    paid = total;
                } else if ("REJECTED".equalsIgnoreCase(p.getPaid()) || "R".equalsIgnoreCase(p.getPaid())) {
                    status = "Rejected";
                }
            }
        }

        double outstanding = Math.max(0, total - paid);

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("caretaker", sf.getCaretakerFee() != null ? sf.getCaretakerFee().doubleValue() : 800.0);
        breakdown.put("lift", sf.getLiftFee() != null ? sf.getLiftFee().doubleValue() : 500.0);
        breakdown.put("waste", sf.getWasteFee() != null ? sf.getWasteFee().doubleValue() : 200.0);
        breakdown.put("water", sf.getWaterFee() != null ? sf.getWaterFee().doubleValue() : 0.0);
        breakdown.put("electricity", sf.getElectricityFee() != null ? sf.getElectricityFee().doubleValue() : 0.0);
        breakdown.put("other", sf.getOtherFee() != null ? sf.getOtherFee().doubleValue() : 1000.0);

        Map<String, Object> resp = new HashMap<>();
        resp.put("serviceFeeId", sf.getServiceFeeId());
        resp.put("flatNumber", flatNum);
        resp.put("billingMonth", monthNames[m - 1]);
        resp.put("billingYear", y);
        resp.put("dueDate", sf.getDueDate());
        resp.put("totalCharge", total);
        resp.put("paidAmount", paid);
        resp.put("outstandingAmount", outstanding);
        resp.put("status", status);
        resp.put("rejectionComment", rejectionComment);
        resp.put("comment", rejectionComment);
        resp.put("previousRejectionComment", prevRejectionComment);
        resp.put("submissionCount", submissionCount);
        resp.put("canResubmit", "Rejected".equalsIgnoreCase(status) || "Not Paid".equalsIgnoreCase(status));
        resp.put("isPublished", true);
        resp.put("publishedDate", sf.getPublishedDate());
        resp.put("breakdown", breakdown);
        resp.put("bkashNumber", "01712345678");

        return ResponseEntity.ok(resp);
    }
}

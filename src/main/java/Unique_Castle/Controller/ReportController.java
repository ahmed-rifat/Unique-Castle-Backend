package Unique_Castle.Controller;

import Unique_Castle.Entity.Flat;
import Unique_Castle.Entity.ServiceFee;
import Unique_Castle.Entity.ServiceFeePayment;
import Unique_Castle.Entity.User;
import Unique_Castle.Repository.FlatRepository;
import Unique_Castle.Repository.ServiceFeePaymentRepository;
import Unique_Castle.Repository.ServiceFeeRepository;
import Unique_Castle.Repository.UserRepository;
import Unique_Castle.ServiceImpl.FlatServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/reports")
public class ReportController {

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceFeeRepository serviceFeeRepository;

    @Autowired
    private ServiceFeePaymentRepository serviceFeePaymentRepository;

    private User resolveOwner(Flat flat) {
        if (flat == null) return null;
        User safeOwner = FlatServiceImpl.getSafeOwner(flat);
        if (safeOwner != null) return safeOwner;

        if (flat.getOwnerId() != null && flat.getOwnerId() > 0) {
            Optional<User> uOpt = userRepository.findById(flat.getOwnerId());
            if (uOpt.isPresent()) return uOpt.get();
        }

        if (flat.getContact() != null && !flat.getContact().isBlank()) {
            Optional<User> uOpt = userRepository.findByPhoneNumber(flat.getContact());
            if (uOpt.isPresent()) return uOpt.get();
        }

        return null;
    }

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private LocalDate parseMonthYear(String monthName, Integer year) {
        LocalDate now = LocalDate.now();
        int y = (year != null && year > 0) ? year : now.getYear();
        int m = now.getMonthValue();
        if (monthName != null && !monthName.isBlank()) {
            try {
                m = Integer.parseInt(monthName.trim());
            } catch (NumberFormatException e) {
                for (int i = 0; i < MONTH_NAMES.length; i++) {
                    if (MONTH_NAMES[i].equalsIgnoreCase(monthName.trim())) {
                        m = i + 1;
                        break;
                    }
                }
            }
        }
        return LocalDate.of(y, m, 1);
    }

    private ServiceFee getOrCreateServiceFee(LocalDate targetMonth) {
        return serviceFeeRepository.findByMonth(targetMonth)
                .or(() -> serviceFeeRepository.findByCurrentMonth(targetMonth, targetMonth.plusMonths(1)))
                .orElseGet(() -> {
                    if (targetMonth.getYear() == 2026 && targetMonth.getMonthValue() == 10) {
                        ServiceFee sf = ServiceFee.builder()
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
                        return serviceFeeRepository.save(sf);
                    }
                    return ServiceFee.builder()
                            .month(targetMonth)
                            .DueDate(targetMonth.plusDays(9))
                            .caretakerFee(BigDecimal.valueOf(800))
                            .wasteFee(BigDecimal.valueOf(200))
                            .liftFee(BigDecimal.valueOf(500))
                            .waterFee(BigDecimal.ZERO)
                            .electricityFee(BigDecimal.ZERO)
                            .otherFee(BigDecimal.valueOf(1000))
                            .totalFee(BigDecimal.valueOf(2500))
                            .status("DRAFT")
                            .build();
                });
    }

    @GetMapping("/admin/monthly")
    public ResponseEntity<?> getAdminMonthlyReport(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {

        LocalDate targetMonth = parseMonthYear(month, year);
        ServiceFee serviceFee = getOrCreateServiceFee(targetMonth);
        double feeVal = serviceFee.getTotalFee() != null ? serviceFee.getTotalFee().doubleValue() : 2500.0;

        List<Flat> flats = flatRepository.findAll();
        List<Map<String, Object>> flatsList = new ArrayList<>();
        int paidFlats = 0;
        int pendingFlats = 0;
        double totalCollected = 0.0;

        for (Flat flat : flats) {
            String flatNum = flat.getFlatNumber();
            User safeOwner = resolveOwner(flat);
            String ownerName = (safeOwner != null && safeOwner.getName() != null && !safeOwner.getName().isBlank())
                    ? safeOwner.getName()
                    : (safeOwner != null ? safeOwner.getUsername() : "Apartment Resident");
            String phone = (safeOwner != null && safeOwner.getPhoneNumber() != null && !safeOwner.getPhoneNumber().isBlank())
                    ? safeOwner.getPhoneNumber()
                    : (flat.getContact() != null ? flat.getContact() : "");
            String email = (safeOwner != null && safeOwner.getEmail() != null)
                    ? safeOwner.getEmail()
                    : "";

            Optional<ServiceFeePayment> paymentOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat,
                    serviceFee);
            if (paymentOpt.isEmpty()) {
                paymentOpt = serviceFeePaymentRepository.findPaymentsInMonth(
                        flat.getFlatId(), targetMonth, targetMonth.plusMonths(1));
            }

            String currentStatus = "Not Paid";
            double paidAmount = 0.0;
            String trxId = null;
            String paymentDate = null;
            String paymentMethod = "bKash";

            if (paymentOpt.isPresent()) {
                ServiceFeePayment p = paymentOpt.get();
                trxId = p.getTransactionId();
                if (p.getPaidDate() != null)
                    paymentDate = p.getPaidDate().toString() + " 12:00:00";
                if (p.getPaymentMethod() != null)
                    paymentMethod = p.getPaymentMethod();
                if ("Y".equalsIgnoreCase(p.getPaid())) {
                    currentStatus = "Paid";
                    paidAmount = feeVal;
                    paidFlats++;
                    totalCollected += feeVal;
                } else if ("P".equalsIgnoreCase(p.getPaid()) || "PENDING".equalsIgnoreCase(p.getPaid())) {
                    currentStatus = "Pending Verification";
                    paidAmount = feeVal;
                    pendingFlats++;
                } else if ("R".equalsIgnoreCase(p.getPaid()) || "REJECTED".equalsIgnoreCase(p.getPaid())) {
                    currentStatus = "Rejected";
                    paidAmount = 0.0;
                }
            }

            Map<String, Object> breakdown = new HashMap<>();
            breakdown.put("caretaker",
                    serviceFee != null && serviceFee.getCaretakerFee() != null
                            ? serviceFee.getCaretakerFee().doubleValue()
                            : 800.0);
            breakdown.put("lift",
                    serviceFee != null && serviceFee.getLiftFee() != null ? serviceFee.getLiftFee().doubleValue()
                            : 500.0);
            breakdown.put("waste",
                    serviceFee != null && serviceFee.getWasteFee() != null ? serviceFee.getWasteFee().doubleValue()
                            : 200.0);
            breakdown.put("water",
                    serviceFee != null && serviceFee.getWaterFee() != null ? serviceFee.getWaterFee().doubleValue()
                            : 0.0);
            breakdown.put("electricity",
                    serviceFee != null && serviceFee.getElectricityFee() != null
                            ? serviceFee.getElectricityFee().doubleValue()
                            : 0.0);
            breakdown.put("other",
                    serviceFee != null && serviceFee.getOtherFee() != null ? serviceFee.getOtherFee().doubleValue()
                            : 1000.0);

            Map<String, Object> row = new HashMap<>();
            row.put("flatNumber", flatNum);
            row.put("ownerName", ownerName);
            row.put("phoneNumber", phone);
            row.put("email", email);
            row.put("totalCharge", feeVal);
            row.put("paidAmount", paidAmount);
            row.put("status", currentStatus);
            row.put("paid", paymentOpt.map(ServiceFeePayment::getPaid).orElse("N"));
            row.put("rejectionComment", paymentOpt.map(ServiceFeePayment::getRejectionComment).orElse(null));
            row.put("comment", paymentOpt.map(ServiceFeePayment::getRejectionComment).orElse(null));
            row.put("previousRejectionComment",
                    paymentOpt.map(ServiceFeePayment::getPreviousRejectionComment).orElse(null));
            row.put("canResubmit",
                    "Rejected".equalsIgnoreCase(currentStatus) || "Not Paid".equalsIgnoreCase(currentStatus));
            row.put("transactionId", trxId);
            row.put("paymentDate", paymentDate);
            row.put("paymentMethod", paymentMethod);
            row.put("breakdown", breakdown);

            flatsList.add(row);
        }

        int totalFlats = flats.size();
        int notPaidFlats = Math.max(0, totalFlats - paidFlats - pendingFlats);
        double totalBilled = feeVal * totalFlats;
        double totalOutstanding = Math.max(0, totalBilled - totalCollected);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalFlats", totalFlats);
        summary.put("paidFlats", paidFlats);
        summary.put("pendingFlats", pendingFlats);
        summary.put("notPaidFlats", notPaidFlats);
        summary.put("totalBilled", totalBilled);
        summary.put("totalCollected", totalCollected);
        summary.put("totalOutstanding", totalOutstanding);

        Map<String, Object> resp = new HashMap<>();
        resp.put("month", MONTH_NAMES[targetMonth.getMonthValue() - 1]);
        resp.put("year", targetMonth.getYear());
        resp.put("flats", flatsList);
        resp.put("summary", summary);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/owner/monthly")
    public ResponseEntity<?> getOwnerMonthlyReport(
            @RequestParam(required = false) String flatNumber,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {

        String flatNum = (flatNumber != null && !flatNumber.isBlank()) ? flatNumber : "4B";
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate targetMonth = parseMonthYear(month, year);
        if (targetMonth.isAfter(currentMonth)) {
            targetMonth = currentMonth;
        }
        ServiceFee sf = getOrCreateServiceFee(targetMonth);
        double feeVal = sf.getTotalFee() != null ? sf.getTotalFee().doubleValue() : 2500.0;

        Flat flat = flatRepository.findByFlatNumberIgnoreCase(flatNum).orElse(null);
        User safeOwner = resolveOwner(flat);
        String ownerName = (safeOwner != null && safeOwner.getName() != null && !safeOwner.getName().isBlank())
                ? safeOwner.getName()
                : (safeOwner != null ? safeOwner.getUsername() : "Apartment Resident");
        String phone = (safeOwner != null && safeOwner.getPhoneNumber() != null && !safeOwner.getPhoneNumber().isBlank())
                ? safeOwner.getPhoneNumber()
                : ((flat != null && flat.getContact() != null) ? flat.getContact() : "");
        String email = (safeOwner != null && safeOwner.getEmail() != null)
                ? safeOwner.getEmail()
                : "";

        String status = "Not Paid";
        double paidAmount = 0.0;
        String trx = null;
        String verifiedAt = null;
        Optional<ServiceFeePayment> paymentOpt = Optional.empty();

        if (flat != null) {
            if (sf.getServiceFeeId() != null) {
                paymentOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, sf);
            }
            if (paymentOpt.isEmpty()) {
                paymentOpt = serviceFeePaymentRepository.findPaymentsInMonth(flat.getFlatId(), targetMonth,
                        targetMonth.plusMonths(1));
            }
            if (paymentOpt.isPresent()) {
                ServiceFeePayment p = paymentOpt.get();
                trx = p.getTransactionId();
                if (p.getPaidDate() != null)
                    verifiedAt = p.getPaidDate().toString() + " 14:00:00";
                if ("Y".equalsIgnoreCase(p.getPaid()) || "PAID".equalsIgnoreCase(p.getPaid())) {
                    status = "Paid";
                    paidAmount = feeVal;
                } else if ("P".equalsIgnoreCase(p.getPaid()) || "PENDING".equalsIgnoreCase(p.getPaid())) {
                    status = "Pending Verification";
                    paidAmount = feeVal;
                } else if ("R".equalsIgnoreCase(p.getPaid()) || "REJECTED".equalsIgnoreCase(p.getPaid())) {
                    status = "Rejected";
                    paidAmount = 0.0;
                }
            }
        }

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("caretaker",
                sf != null && sf.getCaretakerFee() != null ? sf.getCaretakerFee().doubleValue() : 800.0);
        breakdown.put("lift", sf != null && sf.getLiftFee() != null ? sf.getLiftFee().doubleValue() : 500.0);
        breakdown.put("waste", sf != null && sf.getWasteFee() != null ? sf.getWasteFee().doubleValue() : 200.0);
        breakdown.put("water", sf != null && sf.getWaterFee() != null ? sf.getWaterFee().doubleValue() : 0.0);
        breakdown.put("electricity",
                sf != null && sf.getElectricityFee() != null ? sf.getElectricityFee().doubleValue() : 0.0);
        breakdown.put("other", sf != null && sf.getOtherFee() != null ? sf.getOtherFee().doubleValue() : 1000.0);

        Map<String, Object> resp = new HashMap<>();
        resp.put("month", MONTH_NAMES[targetMonth.getMonthValue() - 1]);
        resp.put("year", targetMonth.getYear());
        resp.put("flatNumber", flatNum);

        resp.put("owner", Map.of(
                "name", ownerName,
                "phone", phone,
                "email", email,
                "flatNumber", flatNum));

        Map<String, Object> billMap = new HashMap<>();
        billMap.put("totalCharge", feeVal);
        billMap.put("paidAmount", paidAmount);
        billMap.put("status", status);
        billMap.put("breakdown", breakdown);
        if (paymentOpt != null && paymentOpt.isPresent()) {
            billMap.put("rejectionComment", paymentOpt.get().getRejectionComment());
            billMap.put("comment", paymentOpt.get().getRejectionComment());
        }
        resp.put("bill", billMap);

        resp.put("payment", Map.of(
                "phoneNumber", phone,
                "transactionId", trx != null ? trx : "",
                "paidAmount", paidAmount,
                "status", status,
                "verifiedAt", verifiedAt != null ? verifiedAt : ""));

        return ResponseEntity.ok(resp);
    }
}

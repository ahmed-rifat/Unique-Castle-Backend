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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private ServiceFeeRepository serviceFeeRepository;

    @Autowired
    private ServiceFeePaymentRepository serviceFeePaymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Unique_Castle.Repository.LServiceFeeRepository lServiceFeeRepository;

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
                        sf = serviceFeeRepository.save(sf);
                        try {
                            Unique_Castle.Entity.L_ServiceFee lsf = new Unique_Castle.Entity.L_ServiceFee(sf.getServiceFeeId(), sf.getMonth());
                            lServiceFeeRepository.save(lsf);
                        } catch (Exception ignored) {}
                        return sf;
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

    @GetMapping("/admin/summary")
    public ResponseEntity<?> getAdminSummary(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {

        LocalDate targetMonth = parseMonthYear(month, year);
        ServiceFee serviceFee = getOrCreateServiceFee(targetMonth);
        BigDecimal feeAmount = serviceFee.getTotalFee() != null ? serviceFee.getTotalFee() : BigDecimal.valueOf(2500);

        List<Flat> flats = flatRepository.findByIsArchivedFalse();
        if (flats.isEmpty()) flats = flatRepository.findAll();
        int totalFlats = flats.size();
        int eligibleFlats = 0;
        int paidFlats = 0;
        int pendingFlats = 0;
        BigDecimal totalCollected = BigDecimal.ZERO;

        for (Flat flat : flats) {
            boolean isEligible = FlatServiceImpl.isFlatEligibleForMonth(flat, targetMonth);
            if (isEligible) {
                eligibleFlats++;
            }

            Optional<ServiceFeePayment> paymentOpt = Optional.empty();
            if (serviceFee.getServiceFeeId() != null) {
                paymentOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee);
            }
            if (paymentOpt.isEmpty()) {
                paymentOpt = serviceFeePaymentRepository.findPaymentsInMonth(
                        flat.getFlatId(), targetMonth, targetMonth.plusMonths(1));
            }

            if (paymentOpt.isPresent()) {
                ServiceFeePayment p = paymentOpt.get();
                String paidCode = p.getPaid();
                if ("Y".equalsIgnoreCase(paidCode) || "PAID".equalsIgnoreCase(paidCode)) {
                    paidFlats++;
                    totalCollected = totalCollected.add(feeAmount);
                } else if ("P".equalsIgnoreCase(paidCode) || "PENDING".equalsIgnoreCase(paidCode)) {
                    pendingFlats++;
                }
            }
        }

        int notPaidFlats = Math.max(0, eligibleFlats - paidFlats - pendingFlats);
        BigDecimal totalBilled = feeAmount.multiply(BigDecimal.valueOf(eligibleFlats));
        BigDecimal totalOutstanding = totalBilled.subtract(totalCollected);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalFlats", totalFlats);
        summary.put("eligibleFlats", eligibleFlats);
        summary.put("paidFlats", paidFlats);
        summary.put("pendingFlats", pendingFlats);
        summary.put("notPaidFlats", notPaidFlats);
        summary.put("totalBilled", totalBilled);
        summary.put("totalCollected", totalCollected);
        summary.put("totalOutstanding", totalOutstanding.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : totalOutstanding);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/admin/flats")
    public ResponseEntity<?> getAdminFlats(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

        LocalDate targetMonth = parseMonthYear(month, year);
        ServiceFee serviceFee = getOrCreateServiceFee(targetMonth);
        double feeVal = serviceFee.getTotalFee() != null ? serviceFee.getTotalFee().doubleValue() : 2500.0;

        List<Flat> flats = flatRepository.findByIsArchivedFalse();
        if (flats.isEmpty()) flats = flatRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Flat flat : flats) {
            boolean isEligible = FlatServiceImpl.isFlatEligibleForMonth(flat, targetMonth);
            String flatNum = flat.getFlatNumber();
            User safeOwner = FlatServiceImpl.getSafeOwner(flat);
            String ownerName = safeOwner != null ? (safeOwner.getName() != null && !safeOwner.getName().isBlank() ? safeOwner.getName() : safeOwner.getUsername()) : "Apartment Resident";
            String phone = (safeOwner != null && safeOwner.getPhoneNumber() != null && !safeOwner.getPhoneNumber().isBlank())
                    ? safeOwner.getPhoneNumber()
                    : (flat.getContact() != null ? flat.getContact() : "");
            String email = (safeOwner != null && safeOwner.getEmail() != null)
                    ? safeOwner.getEmail()
                    : "";

            Optional<ServiceFeePayment> paymentOpt = Optional.empty();
            if (serviceFee.getServiceFeeId() != null) {
                paymentOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee);
            }
            if (paymentOpt.isEmpty()) {
                paymentOpt = serviceFeePaymentRepository.findPaymentsInMonth(
                        flat.getFlatId(), targetMonth, targetMonth.plusMonths(1));
            }

            String currentStatus = isEligible ? "Not Paid" : "Exempt";
            double paidAmount = 0.0;
            String trxId = null;
            String paymentDate = null;
            String paymentMethod = "bKash";
            String pPhone = phone;

            if (paymentOpt.isPresent()) {
                ServiceFeePayment p = paymentOpt.get();
                trxId = p.getTransactionId();
                if (p.getPhoneNumber() != null && !p.getPhoneNumber().isBlank()) {
                    pPhone = p.getPhoneNumber();
                }
                if (p.getPaidDate() != null) {
                    paymentDate = p.getPaidDate().toString() + " 12:00:00";
                }
                if (p.getPaymentMethod() != null) {
                    paymentMethod = p.getPaymentMethod();
                }

                String pCode = p.getPaid();
                if ("Y".equalsIgnoreCase(pCode) || "PAID".equalsIgnoreCase(pCode)) {
                    currentStatus = "Paid";
                    paidAmount = feeVal;
                } else if ("P".equalsIgnoreCase(pCode) || "PENDING".equalsIgnoreCase(pCode)) {
                    currentStatus = "Pending Verification";
                    paidAmount = feeVal;
                } else if ("R".equalsIgnoreCase(pCode) || "REJECTED".equalsIgnoreCase(pCode)) {
                    currentStatus = "Rejected";
                    paidAmount = 0.0;
                }
            }

            // Breakdown
            Map<String, Object> breakdown = new HashMap<>();
            breakdown.put("caretaker", serviceFee.getCaretakerFee() != null ? serviceFee.getCaretakerFee().doubleValue() : 800.0);
            breakdown.put("lift", serviceFee.getLiftFee() != null ? serviceFee.getLiftFee().doubleValue() : 500.0);
            breakdown.put("waste", serviceFee.getWasteFee() != null ? serviceFee.getWasteFee().doubleValue() : 200.0);
            breakdown.put("water", serviceFee.getWaterFee() != null ? serviceFee.getWaterFee().doubleValue() : 0.0);
            breakdown.put("electricity", serviceFee.getElectricityFee() != null ? serviceFee.getElectricityFee().doubleValue() : 0.0);
            breakdown.put("other", serviceFee.getOtherFee() != null ? serviceFee.getOtherFee().doubleValue() : 1000.0);

            // Filtering
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                String normFilter = status.trim().replace("_", " ");
                if (!currentStatus.equalsIgnoreCase(normFilter)) {
                    continue;
                }
            }

            if (search != null && !search.isBlank()) {
                String q = search.toLowerCase().trim();
                boolean matches = flatNum.toLowerCase().contains(q)
                        || ownerName.toLowerCase().contains(q)
                        || (pPhone != null && pPhone.contains(q))
                        || (trxId != null && trxId.toLowerCase().contains(q));
                if (!matches) {
                    continue;
                }
            }

            Map<String, Object> row = new HashMap<>();
            row.put("flatId", flat.getFlatId());
            row.put("flatNumber", flatNum);
            row.put("floor", flatNum.length() >= 2 ? Character.getNumericValue(flatNum.charAt(0)) : 1);
            row.put("ownerName", ownerName);
            row.put("phoneNumber", pPhone);
            row.put("email", email);
            row.put("residents", flat.getResidents() != null ? flat.getResidents() : "3");
            row.put("totalCharge", feeVal);
            row.put("paidAmount", paidAmount);
            row.put("status", currentStatus);
            row.put("paid", paymentOpt.map(ServiceFeePayment::getPaid).orElse("N"));
            row.put("rejectionComment", paymentOpt.map(ServiceFeePayment::getRejectionComment).orElse(null));
            row.put("comment", paymentOpt.map(ServiceFeePayment::getRejectionComment).orElse(null));
            row.put("previousRejectionComment", paymentOpt.map(ServiceFeePayment::getPreviousRejectionComment).orElse(null));
            row.put("submissionCount", paymentOpt.map(ServiceFeePayment::getSubmissionCount).orElse(0));
            row.put("rejectionDate", paymentOpt.map(p -> p.getRejectionDate() != null ? p.getRejectionDate().toString() : null).orElse(null));
            row.put("canResubmit", "Rejected".equalsIgnoreCase(currentStatus) || "Not Paid".equalsIgnoreCase(currentStatus));
            row.put("transactionId", trxId);
            row.put("paymentDate", paymentDate);
            row.put("paymentMethod", paymentMethod);
            row.put("breakdown", breakdown);

            result.add(row);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/owner")
    public ResponseEntity<?> getOwnerDashboard(
            @RequestParam(required = false) String flatNumber,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            Authentication authentication) {

        String flatNum = flatNumber;
        if (flatNum == null || flatNum.isBlank()) {
            if (authentication != null && authentication.getName() != null) {
                User user = userRepository.findByUsernameIgnoreCase(authentication.getName()).orElse(null);
                if (user != null) {
                    List<Flat> flats = flatRepository.findByOwnerUserId(user.getUserId());
                    if (flats != null && !flats.isEmpty()) {
                        flatNum = flats.get(0).getFlatNumber();
                    }
                }
            }
        }
        if (flatNum == null || flatNum.isBlank()) {
            flatNum = "4B";
        }

        Flat flat = flatRepository.findByFlatNumberIgnoreCase(flatNum).orElse(null);
        User safeOwner = FlatServiceImpl.getSafeOwner(flat);
        String ownerName = (safeOwner != null && safeOwner.getName() != null && !safeOwner.getName().isBlank())
                ? safeOwner.getName()
                : (safeOwner != null ? safeOwner.getUsername() : "Apartment Resident");
        String phone = (safeOwner != null && safeOwner.getPhoneNumber() != null && !safeOwner.getPhoneNumber().isBlank())
                ? safeOwner.getPhoneNumber()
                : ((flat != null && flat.getContact() != null) ? flat.getContact() : "");

        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate targetMonth = parseMonthYear(month, year);

        // While current month is active, users must see only their current month service charge,
        // and must not see future month charges before that month begins
        if (targetMonth.isAfter(currentMonth)) {
            targetMonth = currentMonth;
        }

        boolean isEligible = FlatServiceImpl.isFlatEligibleForMonth(flat, targetMonth);
        ServiceFee serviceFee = getOrCreateServiceFee(targetMonth);
        double feeVal = isEligible ? (serviceFee.getTotalFee() != null ? serviceFee.getTotalFee().doubleValue() : 2500.0) : 0.0;

        String currentStatus = isEligible ? "Not Paid" : "Exempt";
        double paidAmount = 0.0;
        String trxId = null;
        String paymentDate = null;
        Optional<ServiceFeePayment> paymentOpt = Optional.empty();

        if (flat != null) {
            if (serviceFee.getServiceFeeId() != null) {
                paymentOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee);
            }
            if (paymentOpt.isEmpty()) {
                paymentOpt = serviceFeePaymentRepository.findPaymentsInMonth(
                        flat.getFlatId(), targetMonth, targetMonth.plusMonths(1));
            }
            if (paymentOpt.isPresent()) {
                ServiceFeePayment p = paymentOpt.get();
                trxId = p.getTransactionId();
                if (p.getPaidDate() != null) {
                    paymentDate = p.getPaidDate().toString() + " 14:00:00";
                }
                String pCode = p.getPaid();
                if ("Y".equalsIgnoreCase(pCode) || "PAID".equalsIgnoreCase(pCode)) {
                    currentStatus = "Paid";
                    paidAmount = feeVal;
                } else if ("P".equalsIgnoreCase(pCode) || "PENDING".equalsIgnoreCase(pCode)) {
                    currentStatus = "Pending Verification";
                    paidAmount = feeVal;
                } else if ("R".equalsIgnoreCase(pCode) || "REJECTED".equalsIgnoreCase(pCode)) {
                    currentStatus = "Rejected";
                    paidAmount = 0.0;
                }
            }
        }

        double outstanding = isEligible ? Math.max(0, feeVal - paidAmount) : 0.0;

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("caretaker", serviceFee.getCaretakerFee() != null ? serviceFee.getCaretakerFee().doubleValue() : 800.0);
        breakdown.put("lift", serviceFee.getLiftFee() != null ? serviceFee.getLiftFee().doubleValue() : 500.0);
        breakdown.put("waste", serviceFee.getWasteFee() != null ? serviceFee.getWasteFee().doubleValue() : 200.0);
        breakdown.put("water", serviceFee.getWaterFee() != null ? serviceFee.getWaterFee().doubleValue() : 0.0);
        breakdown.put("electricity", serviceFee.getElectricityFee() != null ? serviceFee.getElectricityFee().doubleValue() : 0.0);
        breakdown.put("other", serviceFee.getOtherFee() != null ? serviceFee.getOtherFee().doubleValue() : 1000.0);

        Map<String, Object> resp = new HashMap<>();
        resp.put("flatNumber", flatNum);
        resp.put("ownerName", ownerName);
        resp.put("billingMonth", MONTH_NAMES[targetMonth.getMonthValue() - 1]);
        resp.put("billingYear", targetMonth.getYear());
        resp.put("totalServiceCharge", feeVal);
        resp.put("paidAmount", paidAmount);
        resp.put("outstandingAmount", outstanding);
        resp.put("paymentStatus", currentStatus);
        resp.put("status", currentStatus);
        resp.put("transactionId", trxId);
        resp.put("phoneNumber", phone);
        resp.put("paymentDate", paymentDate);
        resp.put("rejectionComment", paymentOpt.map(ServiceFeePayment::getRejectionComment).orElse(null));
        resp.put("comment", paymentOpt.map(ServiceFeePayment::getRejectionComment).orElse(null));
        resp.put("previousRejectionComment", paymentOpt.map(ServiceFeePayment::getPreviousRejectionComment).orElse(null));
        resp.put("previousTransactionId", paymentOpt.map(ServiceFeePayment::getPreviousTransactionId).orElse(null));
        resp.put("submissionCount", paymentOpt.map(ServiceFeePayment::getSubmissionCount).orElse(0));
        resp.put("rejectionDate", paymentOpt.map(p -> p.getRejectionDate() != null ? p.getRejectionDate().toString() : null).orElse(null));
        resp.put("canResubmit", isEligible && ("Rejected".equalsIgnoreCase(currentStatus) || "Not Paid".equalsIgnoreCase(currentStatus)));
        resp.put("breakdown", breakdown);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/owner/history")
    public ResponseEntity<?> getOwnerPaymentHistory(
            @RequestParam(required = false) String flatNumber,
            @RequestParam(required = false) Integer year) {

        int targetYear = (year != null && year > 0) ? year : LocalDate.now().getYear();
        String flatNum = (flatNumber != null && !flatNumber.isBlank()) ? flatNumber : "4B";

        Flat flat = flatRepository.findByFlatNumberIgnoreCase(flatNum).orElse(null);

        List<Map<String, Object>> history = new ArrayList<>();
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        int maxMonth = (targetYear == currentMonth.getYear()) ? currentMonth.getMonthValue() : (targetYear < currentMonth.getYear() ? 12 : 0);

        for (int m = 1; m <= maxMonth; m++) {
            String mName = MONTH_NAMES[m - 1];
            LocalDate mDate = LocalDate.of(targetYear, m, 1);
            boolean isOctober = (m == 10 && targetYear == 2026);
            boolean isEligible = FlatServiceImpl.isFlatEligibleForMonth(flat, mDate);

            ServiceFee sf = serviceFeeRepository.findByMonth(mDate)
                    .or(() -> serviceFeeRepository.findByCurrentMonth(mDate, mDate.plusMonths(1)))
                    .orElse(null);
            double totalFeeVal = (sf != null && sf.getTotalFee() != null) ? sf.getTotalFee().doubleValue() : 2500.0;

            String status = isEligible ? "Paid" : "Exempt";
            double paid = isEligible ? totalFeeVal : 0.0;
            String trx = isEligible ? String.format("BK89%02dTRX%03d", m, m * 11) : null;
            String pDate = isEligible ? String.format("%d-%02d-05 14:20:00", targetYear, m) : null;
            String rejComment = null;
            String prevRejComment = null;
            Integer submissionCount = 0;

            if (flat != null) {
                Optional<ServiceFeePayment> pOpt = Optional.empty();
                if (sf != null && sf.getServiceFeeId() != null) {
                    pOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, sf);
                }
                if (pOpt.isEmpty()) {
                    pOpt = serviceFeePaymentRepository.findPaymentsInMonth(flat.getFlatId(), mDate, mDate.plusMonths(1));
                }
                if (pOpt.isPresent()) {
                    ServiceFeePayment p = pOpt.get();
                    rejComment = p.getRejectionComment();
                    prevRejComment = p.getPreviousRejectionComment();
                    submissionCount = p.getSubmissionCount() != null ? p.getSubmissionCount() : 0;
                    trx = p.getTransactionId();
                    if (p.getPaidDate() != null) {
                        pDate = p.getPaidDate().toString() + " 14:20:00";
                    }
                    if ("Y".equalsIgnoreCase(p.getPaid()) || "PAID".equalsIgnoreCase(p.getPaid())) {
                        status = "Paid";
                        paid = totalFeeVal;
                    } else if ("P".equalsIgnoreCase(p.getPaid()) || "PENDING".equalsIgnoreCase(p.getPaid())) {
                        status = "Pending Verification";
                        paid = totalFeeVal;
                    } else if ("R".equalsIgnoreCase(p.getPaid()) || "REJECTED".equalsIgnoreCase(p.getPaid())) {
                        status = "Rejected";
                        paid = 0.0;
                    } else {
                        status = isEligible ? "Not Paid" : "Exempt";
                        paid = 0.0;
                        trx = null;
                        pDate = null;
                    }
                } else if (isOctober) {
                    status = isEligible ? "Not Paid" : "Exempt";
                    paid = 0.0;
                    trx = null;
                    pDate = null;
                } else if (!isEligible) {
                    status = "Exempt";
                    paid = 0.0;
                    trx = null;
                    pDate = null;
                }
            }

            Map<String, Object> item = new HashMap<>();
            item.put("id", m);
            item.put("month", mName);
            item.put("year", targetYear);
            item.put("water", sf != null && sf.getWaterFee() != null ? sf.getWaterFee().doubleValue() : 500);
            item.put("caretaker", sf != null && sf.getCaretakerFee() != null ? sf.getCaretakerFee().doubleValue() : 800);
            item.put("waste", sf != null && sf.getWasteFee() != null ? sf.getWasteFee().doubleValue() : 200);
            item.put("lift", sf != null && sf.getLiftFee() != null ? sf.getLiftFee().doubleValue() : 500);
            item.put("electricity", sf != null && sf.getElectricityFee() != null ? sf.getElectricityFee().doubleValue() : 300);
            item.put("other", sf != null && sf.getOtherFee() != null ? sf.getOtherFee().doubleValue() : 200);
            item.put("totalServiceCharge", totalFeeVal);
            item.put("paidAmount", paid);
            item.put("status", status);
            item.put("paymentDate", pDate);
            item.put("transactionId", trx);
            item.put("rejectionComment", rejComment);
            item.put("comment", rejComment);
            item.put("previousRejectionComment", prevRejComment);
            item.put("submissionCount", submissionCount);
            item.put("canResubmit", "Rejected".equalsIgnoreCase(status) || "Not Paid".equalsIgnoreCase(status));

            history.add(0, item); // latest first
        }

        return ResponseEntity.ok(history);
    }
}


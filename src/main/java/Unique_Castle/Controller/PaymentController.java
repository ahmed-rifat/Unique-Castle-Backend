package Unique_Castle.Controller;

import Unique_Castle.Entity.Flat;
import Unique_Castle.Entity.ServiceFee;
import Unique_Castle.Entity.ServiceFeePayment;
import Unique_Castle.Repository.FlatRepository;
import Unique_Castle.Repository.ServiceFeePaymentRepository;
import Unique_Castle.Repository.ServiceFeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private FlatRepository flatRepository;

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    @Autowired
    private ServiceFeeRepository serviceFeeRepository;

    @Autowired
    private ServiceFeePaymentRepository serviceFeePaymentRepository;

    @Autowired
    private Unique_Castle.Repository.PaymentVerificationAuditRepository paymentVerificationAuditRepository;

    @Autowired
    private Unique_Castle.Repository.LServiceFeeRepository lServiceFeeRepository;

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
                });
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitPayment(@RequestBody Map<String, Object> payload) {
        String flatNumber = (String) payload.get("flatNumber");
        if (flatNumber == null || flatNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Flat number is required"));
        }

        final Flat flat = flatRepository.findByFlatNumberIgnoreCase(flatNumber.trim()).orElse(null);
        if (flat == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Flat not found: " + flatNumber));
        }

        String monthStr = (String) payload.get("month");
        Integer yearInt = null;
        if (payload.get("year") != null) {
            try {
                yearInt = Integer.parseInt(String.valueOf(payload.get("year")));
            } catch (Exception ignored) {}
        }

        LocalDate targetMonth = parseMonthYear(monthStr, yearInt);
        final ServiceFee serviceFee = getOrCreateServiceFee(targetMonth);

        String phoneNumber = (String) payload.get("phoneNumber");
        String transactionId = (String) payload.get("transactionId");
        String paymentMethod = (String) payload.getOrDefault("paymentMethod", "bKash");

        if (transactionId == null || transactionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Transaction ID is required"));
        }

        if (!Unique_Castle.ServiceImpl.FlatServiceImpl.isFlatEligibleForMonth(flat, targetMonth)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Flat " + flat.getFlatNumber() + " is exempt from service charges for " + targetMonth.getMonth() + " " + targetMonth.getYear() + "."
            ));
        }

        ServiceFeePayment payment = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee)
                .orElseGet(() -> {
                    ServiceFeePayment p = new ServiceFeePayment();
                    p.setFlat(flat);
                    p.setServiceFee(serviceFee);
                    return p;
                });

        boolean isResubmission = false;
        String prevRejComment = null;

        if (payment.getServiceFeePaymentId() != null) {
            String currentPaid = payment.getPaid();
            // Rule 1: Approved payments must not be submitted again unnecessarily
            if ("Y".equalsIgnoreCase(currentPaid) || "PAID".equalsIgnoreCase(currentPaid)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Payment for Flat " + flat.getFlatNumber() + " is already approved and verified. Resubmission is restricted."
                ));
            }

            // Rule 2: Prevent duplicate active or pending verification submissions
            if ("P".equalsIgnoreCase(currentPaid) || "PENDING".equalsIgnoreCase(currentPaid)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Payment verification for Flat " + flat.getFlatNumber() + " is already pending Super Admin review. Duplicate submissions are not allowed."
                ));
            }

            // Rule 3: If previously Rejected or Not Paid, allow resubmission!
            if ("R".equalsIgnoreCase(currentPaid) || "REJECTED".equalsIgnoreCase(currentPaid)) {
                isResubmission = true;
                prevRejComment = payment.getRejectionComment();
                payment.setPreviousRejectionComment(prevRejComment);
                payment.setPreviousTransactionId(payment.getTransactionId());
                payment.setRejectionComment(null); // Clear active rejection comment
            }
        }

        payment.setPaid("P"); // Pending Verification
        payment.setPaidDate(LocalDate.now());
        payment.setPhoneNumber(phoneNumber != null && !phoneNumber.isBlank() ? phoneNumber.trim() : flat.getContact());
        payment.setTransactionId(transactionId.trim().toUpperCase());
        payment.setPaymentMethod(paymentMethod);
        payment.setVerificationDate(null);
        payment.setSubmissionCount((payment.getSubmissionCount() != null ? payment.getSubmissionCount() : 0) + 1);

        try {
            payment = serviceFeePaymentRepository.save(payment);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Defensive recovery against race conditions
            ServiceFeePayment existing = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee).orElse(payment);
            existing.setPaid("P");
            existing.setPaidDate(LocalDate.now());
            existing.setPhoneNumber(payment.getPhoneNumber());
            existing.setTransactionId(payment.getTransactionId());
            existing.setPaymentMethod(payment.getPaymentMethod());
            existing.setVerificationDate(null);
            payment = serviceFeePaymentRepository.save(existing);
        }

        // Save audit entry
        paymentVerificationAuditRepository.save(Unique_Castle.Entity.PaymentVerificationAudit.builder()
                .serviceFeePaymentId(payment.getServiceFeePaymentId())
                .flatId(flat.getFlatId())
                .flatNumber(flat.getFlatNumber())
                .serviceFeeId(serviceFee.getServiceFeeId())
                .billingMonth(serviceFee.getMonth())
                .action(isResubmission ? "RESUBMISSION" : "SUBMISSION")
                .status("PENDING")
                .transactionId(payment.getTransactionId())
                .phoneNumber(payment.getPhoneNumber())
                .paymentMethod(payment.getPaymentMethod())
                .rejectionComment(isResubmission ? ("Resubmitted after rejection: " + prevRejComment) : null)
                .actionDate(LocalDate.now())
                .createdAt(java.time.LocalDateTime.now())
                .build());

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", isResubmission
                ? "Payment verification resubmitted successfully! Awaiting Super Admin review."
                : "Payment details submitted successfully! Awaiting Super Admin verification.");
        resp.put("data", Map.of(
                "flatNumber", flat.getFlatNumber(),
                "status", "Pending Verification",
                "paid", "P",
                "transactionId", payment.getTransactionId(),
                "phoneNumber", payment.getPhoneNumber(),
                "submissionCount", payment.getSubmissionCount() != null ? payment.getSubmissionCount() : 1,
                "previousRejectionComment", payment.getPreviousRejectionComment() != null ? payment.getPreviousRejectionComment() : "",
                "canResubmit", false,
                "paidAmount", 2500,
                "paymentDate", LocalDate.now().toString()
        ));

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/verify/{flatNumber}")
    public ResponseEntity<?> verifyPayment(
            @PathVariable String flatNumber,
            @RequestBody(required = false) Map<String, Object> payload) {

        String cleanNum = flatNumber != null ? flatNumber.trim().replaceFirst("(?i)^Flat\\s*", "") : "";
        Flat flatFound = flatRepository.findByFlatNumberIgnoreCase(cleanNum).orElse(null);
        if (flatFound == null && cleanNum.length() > 0) {
            flatFound = flatRepository.findByFlatNumber(cleanNum).orElse(null);
        }
        if (flatFound == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Flat not found: " + flatNumber));
        }
        final Flat flat = flatFound;

        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        if (payload != null && payload.containsKey("month")) {
            Integer yVal = null;
            if (payload.get("year") != null) {
                try { yVal = Integer.parseInt(String.valueOf(payload.get("year"))); } catch (Exception ignored) {}
            }
            currentMonth = parseMonthYear(String.valueOf(payload.get("month")), yVal);
        }
        final ServiceFee serviceFee = getOrCreateServiceFee(currentMonth);

        ServiceFeePayment payment = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee)
                .orElseGet(() -> {
                    ServiceFeePayment p = new ServiceFeePayment();
                    p.setFlat(flat);
                    p.setServiceFee(serviceFee);
                    p.setPaidDate(LocalDate.now());
                    p.setPaymentMethod("bKash");
                    return p;
                });

        if (payload != null) {
            if (payload.containsKey("phoneNumber") && payload.get("phoneNumber") != null) {
                payment.setPhoneNumber(String.valueOf(payload.get("phoneNumber")).trim());
            }
            if (payload.containsKey("transactionId") && payload.get("transactionId") != null) {
                payment.setTransactionId(String.valueOf(payload.get("transactionId")).trim().toUpperCase());
            }
        }

        payment.setPaid("Y"); // Verified / Paid
        payment.setVerificationDate(LocalDate.now());
        if (payment.getPaidDate() == null) {
            payment.setPaidDate(LocalDate.now());
        }
        if (payment.getPhoneNumber() == null || payment.getPhoneNumber().isBlank()) {
            payment.setPhoneNumber(flat.getContact());
        }
        if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
            payment.setTransactionId("VERIFIED" + System.currentTimeMillis() % 100000);
        }
        if (payment.getPaymentMethod() == null) {
            payment.setPaymentMethod("bKash");
        }

        payment = serviceFeePaymentRepository.save(payment);

        paymentVerificationAuditRepository.save(Unique_Castle.Entity.PaymentVerificationAudit.builder()
                .serviceFeePaymentId(payment.getServiceFeePaymentId())
                .flatId(flat.getFlatId())
                .flatNumber(flat.getFlatNumber())
                .serviceFeeId(serviceFee.getServiceFeeId())
                .billingMonth(serviceFee.getMonth())
                .action("APPROVAL")
                .status("PAID")
                .transactionId(payment.getTransactionId())
                .phoneNumber(payment.getPhoneNumber())
                .paymentMethod(payment.getPaymentMethod())
                .actionDate(LocalDate.now())
                .createdAt(java.time.LocalDateTime.now())
                .build());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payment for Flat " + flat.getFlatNumber() + " has been verified successfully.",
                "data", Map.of(
                        "flatNumber", flat.getFlatNumber(),
                        "status", "Paid",
                        "paid", "Y",
                        "canResubmit", false,
                        "transactionId", payment.getTransactionId(),
                        "phoneNumber", payment.getPhoneNumber(),
                        "verificationDate", LocalDate.now().toString()
                )
        ));
    }

    @PostMapping("/reject/{flatNumber}")
    public ResponseEntity<?> rejectPayment(
            @PathVariable String flatNumber,
            @RequestBody(required = false) Map<String, Object> payload) {

        String cleanNum = flatNumber != null ? flatNumber.trim().replaceFirst("(?i)^Flat\\s*", "") : "";
        Flat flatFound = flatRepository.findByFlatNumberIgnoreCase(cleanNum).orElse(null);
        if (flatFound == null && cleanNum.length() > 0) {
            flatFound = flatRepository.findByFlatNumber(cleanNum).orElse(null);
        }
        final Flat flat = flatFound;

        Optional<ServiceFeePayment> paymentOpt = Optional.empty();

        // 1. Try finding payment by transactionId if provided
        if (payload != null && payload.containsKey("transactionId")) {
            String trx = String.valueOf(payload.get("transactionId")).trim();
            if (!trx.isBlank()) {
                paymentOpt = serviceFeePaymentRepository.findFirstByTransactionIdIgnoreCase(trx);
            }
        }

        // 2. Try finding payment by serviceFeePaymentId if provided
        if (paymentOpt.isEmpty() && payload != null && payload.containsKey("serviceFeePaymentId")) {
            try {
                Long pId = Long.parseLong(String.valueOf(payload.get("serviceFeePaymentId")));
                paymentOpt = serviceFeePaymentRepository.findById(pId);
            } catch (Exception ignored) {}
        }

        // 3. Try finding payment by serviceFeeId if provided
        if (paymentOpt.isEmpty() && flat != null && payload != null && payload.containsKey("serviceFeeId")) {
            try {
                Long sfId = Long.parseLong(String.valueOf(payload.get("serviceFeeId")));
                paymentOpt = serviceFeePaymentRepository.findByFlat_FlatIdAndServiceFee_ServiceFeeId(flat.getFlatId(), sfId);
            } catch (Exception ignored) {}
        }

        // 4. Try finding payment by flat and targetMonth
        LocalDate targetMonth = LocalDate.now().withDayOfMonth(1);
        if (payload != null && payload.containsKey("month")) {
            Integer yVal = null;
            if (payload.get("year") != null) {
                try { yVal = Integer.parseInt(String.valueOf(payload.get("year"))); } catch (Exception ignored) {}
            }
            targetMonth = parseMonthYear(String.valueOf(payload.get("month")), yVal);
        }
        final ServiceFee serviceFee = getOrCreateServiceFee(targetMonth);

        if (paymentOpt.isEmpty() && flat != null) {
            paymentOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee);
        }

        String comment = null;
        if (payload != null) {
            if (payload.get("comment") != null && !String.valueOf(payload.get("comment")).isBlank()) {
                comment = String.valueOf(payload.get("comment")).trim();
            } else if (payload.get("rejectionComment") != null && !String.valueOf(payload.get("rejectionComment")).isBlank()) {
                comment = String.valueOf(payload.get("rejectionComment")).trim();
            } else if (payload.get("reason") != null && !String.valueOf(payload.get("reason")).isBlank()) {
                comment = String.valueOf(payload.get("reason")).trim();
            }
        }

        if (comment == null || comment.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "A rejection comment is required when rejecting a payment."
            ));
        }

        ServiceFeePayment p;
        if (paymentOpt.isEmpty() && flat != null) {
            p = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee).orElseGet(() -> {
                ServiceFeePayment np = new ServiceFeePayment();
                np.setFlat(flat);
                np.setServiceFee(serviceFee);
                np.setPaidDate(LocalDate.now());
                np.setPaymentMethod("bKash");
                np.setPhoneNumber(flat.getContact());
                np.setTransactionId("REJ-" + System.currentTimeMillis() % 100000);
                return np;
            });
        } else if (paymentOpt.isPresent()) {
            p = paymentOpt.get();
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Flat or payment record not found."
            ));
        }

        p.setPaid("R");
        p.setRejectionComment(comment);
        p.setRejectionDate(LocalDate.now());
        p.setVerificationDate(null);
        serviceFeePaymentRepository.save(p);

        // Record audit
        paymentVerificationAuditRepository.save(Unique_Castle.Entity.PaymentVerificationAudit.builder()
                .serviceFeePaymentId(p.getServiceFeePaymentId())
                .flatId(flat != null ? flat.getFlatId() : (p.getFlat() != null ? p.getFlat().getFlatId() : null))
                .flatNumber(flat != null ? flat.getFlatNumber() : (p.getFlat() != null ? p.getFlat().getFlatNumber() : cleanNum))
                .serviceFeeId(p.getServiceFee() != null ? p.getServiceFee().getServiceFeeId() : serviceFee.getServiceFeeId())
                .billingMonth(p.getServiceFee() != null ? p.getServiceFee().getMonth() : targetMonth)
                .action("REJECTION")
                .status("REJECTED")
                .transactionId(p.getTransactionId())
                .phoneNumber(p.getPhoneNumber())
                .paymentMethod(p.getPaymentMethod())
                .rejectionComment(comment)
                .actionDate(LocalDate.now())
                .createdAt(java.time.LocalDateTime.now())
                .build());

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("flatNumber", flat != null ? flat.getFlatNumber() : cleanNum);
        dataMap.put("status", "Rejected");
        dataMap.put("paid", "R");
        dataMap.put("rejectionComment", comment);
        dataMap.put("comment", comment);
        dataMap.put("rejectionDate", LocalDate.now().toString());
        dataMap.put("canResubmit", true);
        dataMap.put("submissionCount", p.getSubmissionCount());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payment for Flat " + (flat != null ? flat.getFlatNumber() : cleanNum) + " has been rejected. Reason: " + comment,
                "data", dataMap
        ));
    }

    @GetMapping("/flat/{flatNumber}")
    public ResponseEntity<?> getPaymentByFlat(
            @PathVariable String flatNumber,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        Flat flat = flatRepository.findByFlatNumberIgnoreCase(flatNumber.trim()).orElse(null);
        if (flat == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Flat not found"));
        }

        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate targetMonth = parseMonthYear(month, year);
        if (targetMonth.isAfter(currentMonth)) {
            targetMonth = currentMonth;
        }

        ServiceFee serviceFee = getOrCreateServiceFee(targetMonth);
        Optional<ServiceFeePayment> paymentOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee);

        double totalFee = serviceFee.getTotalFee() != null ? serviceFee.getTotalFee().doubleValue() : 2500.0;
        String status = "Not Paid";
        String trx = null;
        String phone = flat.getContact();
        String rejectionComment = null;
        String previousRejectionComment = null;
        String previousTransactionId = null;
        Integer submissionCount = null;
        LocalDate rejectionDate = null;
        double paid = 0.0;
        boolean canResubmit = true;

        if (paymentOpt.isPresent()) {
            ServiceFeePayment p = paymentOpt.get();
            trx = p.getTransactionId();
            if (p.getPhoneNumber() != null) phone = p.getPhoneNumber();
            rejectionComment = p.getRejectionComment();
            previousRejectionComment = p.getPreviousRejectionComment();
            previousTransactionId = p.getPreviousTransactionId();
            submissionCount = p.getSubmissionCount();
            rejectionDate = p.getRejectionDate();

            if ("Y".equalsIgnoreCase(p.getPaid()) || "PAID".equalsIgnoreCase(p.getPaid())) {
                status = "Paid";
                paid = totalFee;
                canResubmit = false;
            } else if ("P".equalsIgnoreCase(p.getPaid()) || "PENDING".equalsIgnoreCase(p.getPaid())) {
                status = "Pending Verification";
                paid = totalFee;
                canResubmit = false;
            } else if ("R".equalsIgnoreCase(p.getPaid()) || "REJECTED".equalsIgnoreCase(p.getPaid())) {
                status = "Rejected";
                paid = 0.0;
                canResubmit = true;
            } else {
                status = "Not Paid";
                paid = 0.0;
                canResubmit = true;
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("flatNumber", flat.getFlatNumber());
        resp.put("status", status);
        resp.put("paid", paymentOpt.map(ServiceFeePayment::getPaid).orElse("N"));
        resp.put("rejectionComment", rejectionComment);
        resp.put("comment", rejectionComment);
        resp.put("previousRejectionComment", previousRejectionComment);
        resp.put("previousTransactionId", previousTransactionId);
        resp.put("submissionCount", submissionCount != null ? submissionCount : 0);
        resp.put("rejectionDate", rejectionDate != null ? rejectionDate.toString() : null);
        resp.put("canResubmit", canResubmit);
        resp.put("transactionId", trx);
        resp.put("phoneNumber", phone);
        resp.put("paidAmount", paid);
        resp.put("totalCharge", totalFee);

        return ResponseEntity.ok(resp);
    }


    @GetMapping("/audit/{flatNumber}")
    public ResponseEntity<?> getPaymentAuditsByFlat(@PathVariable String flatNumber) {
        String cleanNum = flatNumber != null ? flatNumber.trim().replaceFirst("(?i)^Flat\\s*", "") : "";
        List<Unique_Castle.Entity.PaymentVerificationAudit> audits =
                paymentVerificationAuditRepository.findByFlatNumberOrderByCreatedAtDesc(cleanNum);
        return ResponseEntity.ok(audits);
    }

    @GetMapping("/bkash-config")
    public ResponseEntity<?> getBkashConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("number", "01712345678");
        config.put("accountType", "Merchant / Personal");
        config.put("instructions", List.of(
                "Open your bKash app or dial *247#",
                "Choose 'Send Money' or 'Make Payment' to our official number: 01712345678",
                "Enter the exact monthly service charge amount (BDT 2,500)",
                "Enter your Flat Number as the Reference (e.g., 4B)",
                "Confirm with your bKash PIN and copy the Transaction ID (TrxID)",
                "Fill out the form with your bKash number and Transaction ID"
        ));
        return ResponseEntity.ok(config);
    }
}


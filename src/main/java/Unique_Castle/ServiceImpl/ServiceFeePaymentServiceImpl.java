package Unique_Castle.ServiceImpl;

import Unique_Castle.Entity.Flat;
import Unique_Castle.Entity.ServiceFee;
import Unique_Castle.Entity.ServiceFeePayment;
import Unique_Castle.Repository.FlatRepository;
import Unique_Castle.Repository.ServiceFeePaymentRepository;
import Unique_Castle.Repository.ServiceFeeRepository;
import Unique_Castle.RequestData.ServiceFeePaymentRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.ApiResponseData;
import Unique_Castle.ResponseData.PaymentResponseData;
import Unique_Castle.Service.ServiceFeePaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ServiceFeePaymentServiceImpl implements ServiceFeePaymentService {

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private ServiceFeeRepository serviceFeeRepository;
    @Autowired
    private ServiceFeePaymentRepository serviceFeePaymentRepository;

    @Autowired
    private Unique_Castle.Repository.PaymentVerificationAuditRepository paymentVerificationAuditRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ApiResponseData saveAndUpdateServiceFeePayment(
            ServiceFeePaymentRequest serviceFeePaymentRequest) {

        final Flat flat = (serviceFeePaymentRequest.getFlatId() != null)
                ? flatRepository.findById(serviceFeePaymentRequest.getFlatId()).orElse(null)
                : null;

        final ServiceFee serviceFee = (serviceFeePaymentRequest.getServiceFeeId() != null)
                ? serviceFeeRepository.findById(serviceFeePaymentRequest.getServiceFeeId()).orElse(null)
                : null;

        if (flat == null || serviceFee == null) {
            return new ApiResponseData<>("error", "Flat or Service Fee not found");
        }

        // 1. Check for existing payment record by ID, or by (flat, serviceFee) to prevent duplicate key errors
        ServiceFeePayment existing = null;
        if (serviceFeePaymentRequest.getServiceFeePaymentId() != null) {
            existing = serviceFeePaymentRepository.findById(serviceFeePaymentRequest.getServiceFeePaymentId()).orElse(null);
        }
        if (existing == null) {
            existing = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee)
                    .or(() -> serviceFeePaymentRepository.findByFlat_FlatIdAndServiceFee_ServiceFeeId(flat.getFlatId(), serviceFee.getServiceFeeId()))
                    .orElse(null);
        }

        String reqPaid = serviceFeePaymentRequest.getPaid();
        String comment = serviceFeePaymentRequest.getRejectionComment() != null
                ? serviceFeePaymentRequest.getRejectionComment()
                : serviceFeePaymentRequest.getComment();

        if (existing != null) {
            String currentPaid = existing.getPaid();

            // Rule 1: Approved payments must not be submitted again unnecessarily
            if ("Y".equalsIgnoreCase(currentPaid) || "PAID".equalsIgnoreCase(currentPaid)) {
                if (reqPaid == null || (!"Y".equalsIgnoreCase(reqPaid) && !"R".equalsIgnoreCase(reqPaid))) {
                    return new ApiResponseData<>("error", "Payment for Flat " + flat.getFlatNumber() + " is already approved and verified. Resubmission is restricted.");
                }
            }

            // Rule 2: Prevent duplicate active or pending verification submissions
            if ("P".equalsIgnoreCase(currentPaid) || "PENDING".equalsIgnoreCase(currentPaid)) {
                if (reqPaid == null || "P".equalsIgnoreCase(reqPaid) || "PENDING".equalsIgnoreCase(reqPaid)) {
                    return new ApiResponseData<>("error", "Payment verification for Flat " + flat.getFlatNumber() + " is already pending Super Admin review. Duplicate submissions are not allowed.");
                }
            }

            // Rule 3: Admin Rejection
            if ("R".equalsIgnoreCase(reqPaid) || "REJECTED".equalsIgnoreCase(reqPaid)) {
                existing.setPaid("R");
                if (comment != null && !comment.isBlank()) {
                    existing.setRejectionComment(comment.trim());
                }
                existing.setRejectionDate(LocalDate.now());
                existing.setVerificationDate(null);
                serviceFeePaymentRepository.save(existing);

                paymentVerificationAuditRepository.save(Unique_Castle.Entity.PaymentVerificationAudit.builder()
                        .serviceFeePaymentId(existing.getServiceFeePaymentId())
                        .flatId(flat.getFlatId())
                        .flatNumber(flat.getFlatNumber())
                        .serviceFeeId(serviceFee.getServiceFeeId())
                        .billingMonth(serviceFee.getMonth())
                        .action("REJECTION")
                        .status("REJECTED")
                        .transactionId(existing.getTransactionId())
                        .phoneNumber(existing.getPhoneNumber())
                        .paymentMethod(existing.getPaymentMethod())
                        .rejectionComment(existing.getRejectionComment())
                        .actionDate(LocalDate.now())
                        .createdAt(java.time.LocalDateTime.now())
                        .build());

                return new ApiResponseData<>("success", "Payment verification rejected successfully");
            }

            // Rule 4: Admin Verification / Approval
            if ("Y".equalsIgnoreCase(reqPaid) || "PAID".equalsIgnoreCase(reqPaid)) {
                existing.setPaid("Y");
                existing.setVerificationDate(LocalDate.now());
                if (existing.getPaidDate() == null) {
                    existing.setPaidDate(LocalDate.now());
                }
                serviceFeePaymentRepository.save(existing);

                paymentVerificationAuditRepository.save(Unique_Castle.Entity.PaymentVerificationAudit.builder()
                        .serviceFeePaymentId(existing.getServiceFeePaymentId())
                        .flatId(flat.getFlatId())
                        .flatNumber(flat.getFlatNumber())
                        .serviceFeeId(serviceFee.getServiceFeeId())
                        .billingMonth(serviceFee.getMonth())
                        .action("APPROVAL")
                        .status("PAID")
                        .transactionId(existing.getTransactionId())
                        .phoneNumber(existing.getPhoneNumber())
                        .paymentMethod(existing.getPaymentMethod())
                        .actionDate(LocalDate.now())
                        .createdAt(java.time.LocalDateTime.now())
                        .build());

                return new ApiResponseData<>("success", "Payment verified and approved successfully");
            }

            // Rule 5: User Resubmission after Rejection (or payment was Not Paid 'N')
            // Update the existing record instead of inserting new record -> Eliminates duplicate entry error
            // Preserve historical rejection details
            existing.setPreviousRejectionComment(existing.getRejectionComment());
            existing.setPreviousTransactionId(existing.getTransactionId());
            existing.setRejectionComment(null); // Clear active rejection
            existing.setTransactionId(serviceFeePaymentRequest.getTransactionId() != null ? serviceFeePaymentRequest.getTransactionId().trim().toUpperCase() : "");
            existing.setPhoneNumber(serviceFeePaymentRequest.getPhoneNumber() != null ? serviceFeePaymentRequest.getPhoneNumber().trim() : flat.getContact());
            existing.setPaymentMethod(serviceFeePaymentRequest.getPaymentMethod() != null ? serviceFeePaymentRequest.getPaymentMethod() : "bKash");
            existing.setPaid("P"); // Status: Pending
            existing.setPaidDate(LocalDate.now());
            existing.setVerificationDate(null);
            existing.setSubmissionCount((existing.getSubmissionCount() != null ? existing.getSubmissionCount() : 1) + 1);

            serviceFeePaymentRepository.save(existing);

            paymentVerificationAuditRepository.save(Unique_Castle.Entity.PaymentVerificationAudit.builder()
                    .serviceFeePaymentId(existing.getServiceFeePaymentId())
                    .flatId(flat.getFlatId())
                    .flatNumber(flat.getFlatNumber())
                    .serviceFeeId(serviceFee.getServiceFeeId())
                    .billingMonth(serviceFee.getMonth())
                    .action("RESUBMISSION")
                    .status("PENDING")
                    .transactionId(existing.getTransactionId())
                    .phoneNumber(existing.getPhoneNumber())
                    .paymentMethod(existing.getPaymentMethod())
                    .rejectionComment("Resubmitted after previous rejection: " + existing.getPreviousRejectionComment())
                    .actionDate(LocalDate.now())
                    .createdAt(java.time.LocalDateTime.now())
                    .build());

            return new ApiResponseData<>("success", "Payment verification resubmitted successfully! Awaiting Super Admin review.");

        } else {
            // Create new Service Fee Payment when none exists
            ServiceFeePayment newPayment = new ServiceFeePayment();
            newPayment.setFlat(flat);
            newPayment.setServiceFee(serviceFee);
            newPayment.setTransactionId(serviceFeePaymentRequest.getTransactionId() != null ? serviceFeePaymentRequest.getTransactionId().trim().toUpperCase() : "");
            newPayment.setPhoneNumber(serviceFeePaymentRequest.getPhoneNumber() != null ? serviceFeePaymentRequest.getPhoneNumber().trim() : flat.getContact());
            newPayment.setPaymentMethod(serviceFeePaymentRequest.getPaymentMethod() != null ? serviceFeePaymentRequest.getPaymentMethod() : "bKash");

            if (reqPaid != null && !reqPaid.isBlank()) {
                newPayment.setPaid(reqPaid);
            } else if (newPayment.getTransactionId() != null && !newPayment.getTransactionId().isBlank()) {
                newPayment.setPaid("P");
            } else {
                newPayment.setPaid("N");
            }
            newPayment.setPaidDate(LocalDate.now());
            newPayment.setSubmissionCount(1);
            if (comment != null) {
                newPayment.setRejectionComment(comment.trim());
            }

            try {
                serviceFeePaymentRepository.save(newPayment);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                // Defensive recovery against race conditions
                ServiceFeePayment raceExisting = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFee).orElse(null);
                if (raceExisting != null) {
                    raceExisting.setTransactionId(newPayment.getTransactionId());
                    raceExisting.setPhoneNumber(newPayment.getPhoneNumber());
                    raceExisting.setPaymentMethod(newPayment.getPaymentMethod());
                    raceExisting.setPaid("P");
                    raceExisting.setPaidDate(LocalDate.now());
                    raceExisting.setVerificationDate(null);
                    serviceFeePaymentRepository.save(raceExisting);
                    newPayment = raceExisting;
                }
            }

            paymentVerificationAuditRepository.save(Unique_Castle.Entity.PaymentVerificationAudit.builder()
                    .serviceFeePaymentId(newPayment.getServiceFeePaymentId())
                    .flatId(flat.getFlatId())
                    .flatNumber(flat.getFlatNumber())
                    .serviceFeeId(serviceFee.getServiceFeeId())
                    .billingMonth(serviceFee.getMonth())
                    .action("SUBMISSION")
                    .status("PENDING")
                    .transactionId(newPayment.getTransactionId())
                    .phoneNumber(newPayment.getPhoneNumber())
                    .paymentMethod(newPayment.getPaymentMethod())
                    .actionDate(LocalDate.now())
                    .createdAt(java.time.LocalDateTime.now())
                    .build());

            return new ApiResponseData<>("success", "Payment verification submitted successfully! Awaiting Super Admin review.");
        }
    }


    @Override
    public ApiGetResponseData getAllServiceFeePayments() {
        List <ServiceFeePayment> payments =
                serviceFeePaymentRepository.findAll();
        if (payments.isEmpty()) {
            return new ApiGetResponseData<>(
                    "error",
                    "No Service Fee Payments found",
                    null
            );
        }
        List <PaymentResponseData> responseData = payments.stream().map(payment -> {
            PaymentResponseData response = new PaymentResponseData();
            response.setServiceFeePaymentId(payment.getServiceFeePaymentId());
            response.setFlatId(payment.getFlat().getFlatId());
            response.setServiceFeeId(payment.getServiceFee().getServiceFeeId());
            response.setPaid(payment.getPaid());
            response.setPaidDate(payment.getPaidDate());
            response.setTransactionId(payment.getTransactionId());
            response.setPaymentMethod(payment.getPaymentMethod());
            response.setRejectionComment(payment.getRejectionComment());
            response.setComment(payment.getRejectionComment());
            response.setPreviousRejectionComment(payment.getPreviousRejectionComment());
            response.setPreviousTransactionId(payment.getPreviousTransactionId());
            response.setSubmissionCount(payment.getSubmissionCount());
            response.setRejectionDate(payment.getRejectionDate());

            String rawPaid = payment.getPaid();
            String displayStatus = "Not Paid";
            if ("Y".equalsIgnoreCase(rawPaid) || "PAID".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Paid";
                response.setCanResubmit(false);
            } else if ("P".equalsIgnoreCase(rawPaid) || "PENDING".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Pending Verification";
                response.setCanResubmit(false);
            } else if ("R".equalsIgnoreCase(rawPaid) || "REJECTED".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Rejected";
                response.setCanResubmit(true);
            } else {
                response.setCanResubmit(true);
            }
            response.setStatus(displayStatus);

            return response;
        }).toList();
        return new ApiGetResponseData<>(
                "success",
                "Service Fee Payments retrieved successfully",
                responseData
        );
    }

    @Override
    public ApiGetResponseData getServiceFeePaymentsByFlatId(Long flatId) {
        List <ServiceFeePayment> payments =
                serviceFeePaymentRepository.findByFlat_FlatId(flatId);
        if (payments.isEmpty()) {
            return new ApiGetResponseData<>(
                    "error",
                    "No Service Fee Payments found for the given Flat ID",
                    null
            );
        }
        List <PaymentResponseData> responseData = payments.stream().map(payment -> {
            PaymentResponseData response = new PaymentResponseData();
            response.setServiceFeePaymentId(payment.getServiceFeePaymentId());
            response.setFlatId(payment.getFlat().getFlatId());
            response.setServiceFeeId(payment.getServiceFee().getServiceFeeId());
            response.setPaid(payment.getPaid());
            response.setPaidDate(payment.getPaidDate());
            response.setTransactionId(payment.getTransactionId());
            response.setPaymentMethod(payment.getPaymentMethod());
            response.setRejectionComment(payment.getRejectionComment());
            response.setComment(payment.getRejectionComment());
            response.setPreviousRejectionComment(payment.getPreviousRejectionComment());
            response.setPreviousTransactionId(payment.getPreviousTransactionId());
            response.setSubmissionCount(payment.getSubmissionCount());
            response.setRejectionDate(payment.getRejectionDate());

            String rawPaid = payment.getPaid();
            String displayStatus = "Not Paid";
            if ("Y".equalsIgnoreCase(rawPaid) || "PAID".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Paid";
                response.setCanResubmit(false);
            } else if ("P".equalsIgnoreCase(rawPaid) || "PENDING".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Pending Verification";
                response.setCanResubmit(false);
            } else if ("R".equalsIgnoreCase(rawPaid) || "REJECTED".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Rejected";
                response.setCanResubmit(true);
            } else {
                response.setCanResubmit(true);
            }
            response.setStatus(displayStatus);

            return response;
        }).toList();
        return new ApiGetResponseData<>(
                "success",
                "Service Fee Payments for the given Flat ID retrieved successfully",
                responseData
        );
    }

    @Override
    public ApiGetResponseData PaymentVerification(String PhoneNumber, String TransactionId) {
            Optional<ServiceFeePayment> payments =
                    serviceFeePaymentRepository.PaymentVerification(PhoneNumber, TransactionId);

            if (payments.isEmpty()) {
                return new ApiGetResponseData<>(
                        "error",
                        "No Service Fee Payment found for the given details",
                        null
                );
            }
            else {
                ServiceFeePayment payment = payments.get();
                if ("Y".equalsIgnoreCase(payment.getPaid()) || "PAID".equalsIgnoreCase(payment.getPaid())) {
                    return new ApiGetResponseData<>(
                            "success",
                            "Service Fee Payment already verified",
                            null
                    );
                }
                payment.setPaid("Y");
                payment.setVerificationDate(LocalDate.now());
                if (payment.getPaidDate() == null) {
                    payment.setPaidDate(LocalDate.now());
                }
                serviceFeePaymentRepository.save(payment);

                paymentVerificationAuditRepository.save(Unique_Castle.Entity.PaymentVerificationAudit.builder()
                        .serviceFeePaymentId(payment.getServiceFeePaymentId())
                        .flatId(payment.getFlat() != null ? payment.getFlat().getFlatId() : null)
                        .flatNumber(payment.getFlat() != null ? payment.getFlat().getFlatNumber() : "")
                        .serviceFeeId(payment.getServiceFee() != null ? payment.getServiceFee().getServiceFeeId() : null)
                        .billingMonth(payment.getServiceFee() != null ? payment.getServiceFee().getMonth() : LocalDate.now())
                        .action("APPROVAL")
                        .status("PAID")
                        .transactionId(payment.getTransactionId())
                        .phoneNumber(payment.getPhoneNumber())
                        .paymentMethod(payment.getPaymentMethod())
                        .actionDate(LocalDate.now())
                        .createdAt(java.time.LocalDateTime.now())
                        .build());

                return new ApiGetResponseData<>(
                        "success",
                        "Service Fee Payment verification successful",
                        null
                );
            }

    }
}


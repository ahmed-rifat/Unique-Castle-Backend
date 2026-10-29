package Unique_Castle.ServiceImpl;

import Unique_Castle.Config.JwtUtil;
import Unique_Castle.Entity.*;
import Unique_Castle.Repository.*;
import Unique_Castle.RequestData.FlatRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.ApiResponseData;
import Unique_Castle.ResponseData.FlatResponseData;
import Unique_Castle.ResponseData.PageResponse;
import Unique_Castle.Service.FlatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class FlatServiceImpl implements FlatService {

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private LFlatRepository lFlatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceFeeRepository serviceFeeRepository;

    @Autowired
    private ServiceFeePaymentRepository serviceFeePaymentRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public ApiResponseData<Flat> saveAndUpdateFlat(FlatRequest flatRequest) {

        if (flatRequest.getFlatId() != null) {
            Flat flatExisting = flatRepository.findById(flatRequest.getFlatId())
                    .orElse(null);
            if (flatExisting == null) {
                return new ApiResponseData<>("error", "Flat not found");
            }
            User owner = userRepository.findById(flatRequest.getOwnerId())
                    .orElse(null);

            if (owner == null) {
                return new ApiResponseData<>("error", "Owner not found");
            }
            // Update fields
            flatExisting.setFlatNumber(flatRequest.getFlatNumber());
            flatExisting.setContact(flatRequest.getContact());
            flatExisting.setResidents(flatRequest.getResidents());
            flatExisting.setOwner(owner);
            // update and return
            Flat updatedFlat = flatRepository.save(flatExisting);
            return new ApiResponseData<>("success", "Flat updated successfully");

        } else {
            if (flatRepository.existsByFlatNumber(flatRequest.getFlatNumber())) {
                return new ApiResponseData<>("error", "Flat number already exists");
            }
            // Fetch owner
            User owner = userRepository.findById(flatRequest.getOwnerId())
                    .orElse(null);

            if (owner == null) {
                return new ApiResponseData<>("error", "Owner not found");
            }

            // Create Flat entity
            Flat flat = new Flat();
            flat.setFlatNumber(flatRequest.getFlatNumber());
            flat.setContact(flatRequest.getContact());
            flat.setResidents(flatRequest.getResidents());
            flat.setOwner(owner);

            // Save and return
            Flat savedFlat = flatRepository.save(flat);

            L_Flat lFlat = new L_Flat();
            lFlat.setFlatId(savedFlat.getFlatId());
            lFlat.setFlatNumber(savedFlat.getFlatNumber());
            lFlatRepository.save(lFlat);

            return new ApiResponseData<>("success", "Flat added successfully");
        }
    }

    @Override
    public ApiGetResponseData getAllFlats(Authentication authentication) {
        return getAllFlats(authentication, null, null);
    }

    @Override
    public ApiGetResponseData getAllFlats(Authentication authentication, Integer year, Integer month) {
        String username = authentication != null ? authentication.getName() : null;
        User user = (username != null) ? userRepository.findByUsername(username).orElse(null) : null;
        List<Flat> flats;
        if (user != null && "OWNER".equalsIgnoreCase(user.getRole().name())) {
            flats = flatRepository.findByOwnerUserId(user.getUserId());
            if (flats.isEmpty() && user.getPhoneNumber() != null) {
                flats = flatRepository.findByOwnerPhoneNumber(user.getPhoneNumber());
            }
        } else {
            flats = flatRepository.findAll();
        }

        if (flats.isEmpty()) {
            return new ApiGetResponseData<>("error", "No flats found", null);
        }

        LocalDate from;
        LocalDate to;
        boolean specificMonthRequested = false;
        if (year != null && month != null && year > 0 && month >= 1 && month <= 12) {
            from = LocalDate.of(year, month, 1);
            to = from.plusMonths(1);
            specificMonthRequested = true;
        } else {
            from = LocalDate.now().withDayOfMonth(1); // 1st of current month
            to = from.plusMonths(1); // 1st of next month
        }

        // Owners are only permitted to view up to the current active billing period
        if (user != null && "OWNER".equalsIgnoreCase(user.getRole().name())) {
            if (from.isAfter(LocalDate.now().withDayOfMonth(1))) {
                from = LocalDate.now().withDayOfMonth(1);
                to = from.plusMonths(1);
            }
        }

        final LocalDate searchFrom = from;
        final LocalDate searchTo = to;
        Optional<ServiceFee> serviceFeeOpt = serviceFeeRepository.findByMonth(searchFrom)
                .or(() -> serviceFeeRepository.findByCurrentMonth(searchFrom, searchTo));

        List<FlatResponseData> flatResponses = new ArrayList<>();

        for (Flat flat : flats) {
            FlatResponseData response = new FlatResponseData();

            response.setFlatId(flat.getFlatId());
            response.setFlatNumber(flat.getFlatNumber());
            response.setContact(flat.getContact());
            response.setResidents(flat.getResidents());

            response.setOwnerId(getSafeOwnerId(flat));
            response.setOwnerName(getSafeOwnerName(flat));

            populatePaymentInfo(response, flat, serviceFeeOpt, from, to, !specificMonthRequested);
            flatResponses.add(response);
        }

        return new ApiGetResponseData<>("success",
                "Flats retrieved successfully",
                flatResponses);
    }

    @Override
    public ApiGetResponseData getAllFlatsByMonth(int year, int month) {

        YearMonth yearMonth = YearMonth.of(year, month);

        LocalDate startDate = yearMonth.atDay(1); // e.g. 2026-02-01
        LocalDate nextMonthStart = startDate.plusMonths(1); // e.g. 2026-03-01

        Optional<ServiceFee> serviceFeeOpt = serviceFeeRepository.findByMonth(startDate)
                .or(() -> serviceFeeRepository.findByCurrentMonth(startDate, nextMonthStart));

        List<Flat> flats = flatRepository.findAll();

        if (flats.isEmpty()) {
            return new ApiGetResponseData<>("error", "No flats found", null);
        }

        List<FlatResponseData> flatResponses = flats.stream().map(flat -> {
            FlatResponseData response = new FlatResponseData();
            response.setFlatId(flat.getFlatId());
            response.setFlatNumber(flat.getFlatNumber());
            response.setContact(flat.getContact());
            response.setResidents(flat.getResidents());
            response.setOwnerId(getSafeOwnerId(flat));
            response.setOwnerName(getSafeOwnerName(flat));

            populatePaymentInfo(response, flat, serviceFeeOpt, startDate, nextMonthStart, false);
            return response;
        }).toList();

        return new ApiGetResponseData<>("success", "Flats retrieved successfully", flatResponses);
    }

    @Override
    public ApiGetResponseData getFlatsByOwnerId(Long ownerId) {
        List<Flat> flats = flatRepository.findByOwnerUserId(ownerId);
        LocalDate from = LocalDate.now().withDayOfMonth(1); // 1st of current month
        LocalDate to = from.plusMonths(1); // 1st of next month

        Optional<ServiceFee> serviceFeeOpt = serviceFeeRepository.findByMonth(from)
                .or(() -> serviceFeeRepository.findByCurrentMonth(from, to));

        if (flats.isEmpty()) {
            return new ApiGetResponseData<>("error", "No flats found for the given owner", null);
        }

        List<FlatResponseData> flatResponses = flats.stream().map(flat -> {
            FlatResponseData response = new FlatResponseData();
            response.setFlatId(flat.getFlatId());
            response.setFlatNumber(flat.getFlatNumber());
            response.setContact(flat.getContact());
            response.setResidents(flat.getResidents());
            response.setOwnerId(getSafeOwnerId(flat));
            response.setOwnerName(getSafeOwnerName(flat));

            populatePaymentInfo(response, flat, serviceFeeOpt, from, to, true);
            return response;
        }).toList();

        return new ApiGetResponseData<>("success", "Flats retrieved successfully", flatResponses);
    }

    private void populatePaymentInfo(FlatResponseData response, Flat flat, Optional<ServiceFee> serviceFeeOpt,
            LocalDate from, LocalDate to, boolean allowFallbackToLatest) {
        Optional<ServiceFeePayment> paymentOpt = Optional.empty();
        if (serviceFeeOpt.isPresent()) {
            paymentOpt = serviceFeePaymentRepository.findByFlatAndServiceFee(flat, serviceFeeOpt.get());
            if (paymentOpt.isEmpty()) {
                paymentOpt = serviceFeePaymentRepository.findByFlat_FlatIdAndServiceFee_ServiceFeeId(flat.getFlatId(),
                        serviceFeeOpt.get().getServiceFeeId());
            }
        }
        if (paymentOpt.isEmpty()) {
            paymentOpt = serviceFeePaymentRepository.findPaymentsInMonth(flat.getFlatId(), from, to);
        }

        // Fallback: If no payment found for this month and it is the current active
        // month, check latest persisted payment for this flat
        // This ensures the rejected status and admin rejection comments are accurately
        // returned for the current period
        boolean isCurrentMonth = from.equals(LocalDate.now().withDayOfMonth(1));
        if (paymentOpt.isEmpty() && allowFallbackToLatest && isCurrentMonth) {
            if (flat.getServiceFeePayments() != null && !flat.getServiceFeePayments().isEmpty()) {
                paymentOpt = flat.getServiceFeePayments().stream()
                        .sorted(Comparator.comparing(ServiceFeePayment::getServiceFeePaymentId).reversed())
                        .findFirst();
            }
            if (paymentOpt.isEmpty()) {
                paymentOpt = serviceFeePaymentRepository
                        .findFirstByFlat_FlatIdOrderByServiceFeePaymentIdDesc(flat.getFlatId());
            }
            if (paymentOpt.isEmpty()) {
                paymentOpt = serviceFeePaymentRepository
                        .findFirstByFlat_FlatNumberOrderByServiceFeePaymentIdDesc(flat.getFlatNumber());
            }
        }

        if (paymentOpt.isPresent()) {
            ServiceFeePayment payment = paymentOpt.get();
            String rawPaid = payment.getPaid();
            String displayStatus = "Not Paid";
            boolean canResubmit = true;
            if ("Y".equalsIgnoreCase(rawPaid) || "PAID".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Paid";
                canResubmit = false;
            } else if ("P".equalsIgnoreCase(rawPaid) || "PENDING".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Pending Verification";
                canResubmit = false;
            } else if ("R".equalsIgnoreCase(rawPaid) || "REJECTED".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Rejected";
                canResubmit = true;
            } else if ("N".equalsIgnoreCase(rawPaid)) {
                displayStatus = "Not Paid";
                canResubmit = true;
            }

            response.setPaid(rawPaid != null ? rawPaid : "N");
            response.setStatus(displayStatus);
            response.setCanResubmit(canResubmit);
            response.setRejectionComment(payment.getRejectionComment());
            response.setComment(payment.getRejectionComment());
            response.setPreviousRejectionComment(payment.getPreviousRejectionComment());
            response.setPreviousTransactionId(payment.getPreviousTransactionId());
            response.setSubmissionCount(payment.getSubmissionCount());
            response.setRejectionDate(payment.getRejectionDate());
            response.setPaidDate(payment.getPaidDate());
            response.setPaymentMethod(payment.getPaymentMethod());
            response.setTransactionId(payment.getTransactionId());

            ServiceFee sf = serviceFeeOpt.orElse(payment.getServiceFee());
            if (sf != null) {
                response.setServiceFeeId(sf.getServiceFeeId());
                response.setServiceFeeMonth(sf.getMonth());
                response.setTotalAmount(sf.getTotalFee());
            } else {
                response.setServiceFeeMonth(from);
            }
        } else {
            boolean isEligible = isFlatEligibleForMonth(flat, from);
            if (!isEligible) {
                response.setPaid("N");
                response.setStatus("Exempt");
                response.setCanResubmit(false);
                response.setRejectionComment(null);
                response.setComment(null);
                response.setPreviousRejectionComment(null);
                response.setPreviousTransactionId(null);
                response.setSubmissionCount(0);
                response.setRejectionDate(null);
                response.setPaidDate(null);
                response.setPaymentMethod(null);
                response.setTransactionId(null);
                response.setTotalAmount(BigDecimal.ZERO);
                if (serviceFeeOpt.isPresent()) {
                    response.setServiceFeeId(serviceFeeOpt.get().getServiceFeeId());
                    response.setServiceFeeMonth(serviceFeeOpt.get().getMonth());
                } else {
                    response.setServiceFeeMonth(from);
                }
            } else {
                response.setPaid("N");
                response.setStatus("Not Paid");
                response.setCanResubmit(true);
                response.setRejectionComment(null);
                response.setComment(null);
                response.setPreviousRejectionComment(null);
                response.setPreviousTransactionId(null);
                response.setSubmissionCount(0);
                response.setRejectionDate(null);
                response.setPaidDate(null);
                response.setPaymentMethod(null);
                response.setTransactionId(null);
                if (serviceFeeOpt.isPresent()) {
                    response.setServiceFeeId(serviceFeeOpt.get().getServiceFeeId());
                    response.setServiceFeeMonth(serviceFeeOpt.get().getMonth());
                    response.setTotalAmount(serviceFeeOpt.get().getTotalFee());
                } else {
                    response.setServiceFeeMonth(from);
                }
            }
        }
    }

    public static boolean isFlatEligibleForMonth(Flat flat, LocalDate targetMonth) {
        if (flat == null)
            return false;
        if (Boolean.TRUE.equals(flat.getIsArchived()))
            return false;

        if ("Ineligible".equalsIgnoreCase(flat.getBillingEligibility())) {
            return false;
        }

        String occupancy = flat.getOccupancyStatus();
        LocalDate monthEnd = targetMonth.withDayOfMonth(targetMonth.lengthOfMonth());

        if ("Not Moved In".equalsIgnoreCase(occupancy) || "Vacated".equalsIgnoreCase(occupancy)) {
            if (flat.getMoveInDate() != null) {
                return !flat.getMoveInDate().isAfter(monthEnd);
            }
            return false;
        }

        if (flat.getMoveInDate() != null && flat.getMoveInDate().isAfter(monthEnd)) {
            return false;
        }

        return true;
    }

    @Override
    public PageResponse<FlatResponseData> getFlatsWithPagination(int page, int size) {

        int pageIndex = Math.max(page, 1) - 1; // 1-based Ã¢â€ â€™ 0-based
        int pageSize = Math.max(size, 1);

        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by("flatId").ascending());
        Page<Flat> flatPage = flatRepository.findAll(pageable);

        // map Flat Ã¢â€ â€™ FlatDTO
        List<FlatResponseData> flatDTOList = new ArrayList<>();
        for (Flat flat : flatPage.getContent()) {
            FlatResponseData dto = new FlatResponseData(
                    flat.getFlatId(),
                    flat.getFlatNumber(),
                    flat.getContact(),
                    flat.getResidents(),
                    getSafeOwnerId(flat),
                    getSafeOwnerName(flat));
            flatDTOList.add(dto);
        }

        return new PageResponse<>(
                flatDTOList, // ArrayList
                page, // 1-based
                flatPage.getTotalPages(),
                flatPage.getTotalElements());
    }

    public static User getSafeOwner(Flat flat) {
        if (flat == null) {
            return null;
        }
        try {
            User owner = flat.getOwner();
            if (owner != null && owner.getUserId() != null && owner.getUserId() > 0) {
                // Access a property to ensure the Hibernate lazy proxy can be initialized
                owner.getUsername();
                return owner;
            }
        } catch (Exception ignored) {
            // Catches ObjectNotFoundException, EntityNotFoundException, etc.
        }
        return null;
    }

    public static Long getSafeOwnerId(Flat flat) {
        if (flat == null) {
            return null;
        }
        User owner = getSafeOwner(flat);
        if (owner != null) {
            return owner.getUserId();
        }
        if (flat.getOwnerId() != null && flat.getOwnerId() > 0) {
            return flat.getOwnerId();
        }
        return null;
    }

    public static String getSafeOwnerName(Flat flat) {
        if (flat == null) {
            return "Unassigned";
        }
        User owner = getSafeOwner(flat);
        if (owner != null) {
            if (owner.getName() != null && !owner.getName().isBlank()) {
                return owner.getName();
            }
            if (owner.getUsername() != null && !owner.getUsername().isBlank()) {
                return owner.getUsername();
            }
        }
        return "Unassigned";
    }

    public User getUser(HttpServletRequest request) {
        String requestTokenHeader = request.getHeader("Authorization").substring(7);
        String jwtToken = requestTokenHeader.substring(7);
        String username = jwtUtil.getUsername(jwtToken);
        User userValue = userRepository.findByUsername(username).get();
        return userValue;
    }
}

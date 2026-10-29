package Unique_Castle.ServiceImpl;

import Unique_Castle.Entity.*;
import Unique_Castle.Repository.*;
import Unique_Castle.RequestData.FlatMemberRequest;
import Unique_Castle.RequestData.ResetPasswordRequest;
import Unique_Castle.ResponseData.FlatMemberResponse;
import Unique_Castle.Service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class MemberServiceImpl implements MemberService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LUserRepository lUserRepository;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private LFlatRepository lFlatRepository;

    @Autowired
    private ServiceFeePaymentRepository serviceFeePaymentRepository;

    @Autowired
    private PaymentVerificationAuditRepository paymentVerificationAuditRepository;

    @Autowired
    private MemberAuditLogRepository memberAuditLogRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public List<FlatMemberResponse> getAllMembers() {
        List<Flat> flats = flatRepository.findAll();
        List<User> users = userRepository.findAll();
        List<FlatMemberResponse> responses = new ArrayList<>();
        Set<Long> processedUserIds = new HashSet<>();

        // 1. Process all flats and their assigned owners
        for (Flat flat : flats) {
            User owner = FlatServiceImpl.getSafeOwner(flat);
            if (owner == null && flat.getOwnerId() != null && flat.getOwnerId() > 0) {
                owner = userRepository.findById(flat.getOwnerId()).orElse(null);
            }
            if (owner == null && flat.getContact() != null && !flat.getContact().isBlank()) {
                owner = userRepository.findByPhoneNumber(flat.getContact()).orElse(null);
            }
            if (owner != null) {
                processedUserIds.add(owner.getUserId());
            }

            boolean hasFinancialHistory = checkFinancialHistory(flat, owner);

            FlatMemberResponse resp = FlatMemberResponse.builder()
                    .id(owner != null ? owner.getUserId() : flat.getFlatId())
                    .userId(owner != null ? owner.getUserId() : null)
                    .flatId(flat.getFlatId())
                    .name(owner != null && owner.getName() != null && !owner.getName().isBlank() ? owner.getName() : (owner != null ? owner.getUsername() : "Unassigned"))
                    .username(owner != null ? owner.getUsername() : "")
                    .phoneNumber(owner != null ? owner.getPhoneNumber() : flat.getContact())
                    .email(owner != null ? owner.getEmail() : "")
                    .flatNumber(flat.getFlatNumber())
                    .buildingName(flat.getBuildingName() != null ? flat.getBuildingName() : "Unique Castle")
                    .role(owner != null ? owner.getRole().name() : "OWNER")
                    .occupancyStatus(flat.getOccupancyStatus() != null ? flat.getOccupancyStatus() : "Moved In")
                    .accountStatus(owner != null && "INACTIVE".equalsIgnoreCase(owner.getStatus()) ? "Inactive" : "Active")
                    .billingEligibility(flat.getBillingEligibility() != null ? flat.getBillingEligibility() : "Eligible")
                    .moveInDate(flat.getMoveInDate())
                    .isArchived(Boolean.TRUE.equals(flat.getIsArchived()))
                    .notes(flat.getNotes())
                    .hasFinancialHistory(hasFinancialHistory)
                    .build();

            responses.add(resp);
        }

        // 2. Also include any users (e.g. Admin or owners without flat assigned)
        for (User user : users) {
            if (!processedUserIds.contains(user.getUserId())) {
                FlatMemberResponse resp = FlatMemberResponse.builder()
                        .id(user.getUserId())
                        .userId(user.getUserId())
                        .name(user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getUsername())
                        .username(user.getUsername())
                        .phoneNumber(user.getPhoneNumber())
                        .email(user.getEmail())
                        .flatNumber(user.getRole() == User.Role.ADMIN ? "Management Office" : "Unassigned")
                        .buildingName("Unique Castle")
                        .role(user.getRole().name())
                        .occupancyStatus("Moved In")
                        .accountStatus("INACTIVE".equalsIgnoreCase(user.getStatus()) ? "Inactive" : "Active")
                        .billingEligibility(user.getRole() == User.Role.ADMIN ? "Ineligible" : "Eligible")
                        .isArchived(false)
                        .hasFinancialHistory(false)
                        .build();

                responses.add(resp);
            }
        }

        return responses;
    }

    @Override
    public FlatMemberResponse getMemberById(Long id) {
        User user = userRepository.findById(id).orElse(null);
        Flat flat = null;
        if (user != null) {
            List<Flat> userFlats = flatRepository.findByOwnerUserId(user.getUserId());
            if (userFlats != null && !userFlats.isEmpty()) {
                flat = userFlats.get(0);
            }
        } else {
            flat = flatRepository.findById(id).orElse(null);
            if (flat != null) {
                user = FlatServiceImpl.getSafeOwner(flat);
            }
        }

        if (user == null && flat == null) {
            throw new RuntimeException("Member not found with id: " + id);
        }

        boolean hasFinancialHistory = checkFinancialHistory(flat, user);

        return FlatMemberResponse.builder()
                .id(user != null ? user.getUserId() : flat.getFlatId())
                .userId(user != null ? user.getUserId() : null)
                .flatId(flat != null ? flat.getFlatId() : null)
                .name(user != null && user.getName() != null && !user.getName().isBlank() ? user.getName() : (user != null ? user.getUsername() : "Unassigned"))
                .username(user != null ? user.getUsername() : "")
                .phoneNumber(user != null ? user.getPhoneNumber() : (flat != null ? flat.getContact() : ""))
                .email(user != null ? user.getEmail() : "")
                .flatNumber(flat != null ? flat.getFlatNumber() : "Management Office")
                .buildingName(flat != null && flat.getBuildingName() != null ? flat.getBuildingName() : "Unique Castle")
                .role(user != null ? user.getRole().name() : "OWNER")
                .occupancyStatus(flat != null && flat.getOccupancyStatus() != null ? flat.getOccupancyStatus() : "Moved In")
                .accountStatus(user != null && "INACTIVE".equalsIgnoreCase(user.getStatus()) ? "Inactive" : "Active")
                .billingEligibility(flat != null && flat.getBillingEligibility() != null ? flat.getBillingEligibility() : "Eligible")
                .moveInDate(flat != null ? flat.getMoveInDate() : null)
                .isArchived(flat != null && Boolean.TRUE.equals(flat.getIsArchived()))
                .notes(flat != null ? flat.getNotes() : null)
                .hasFinancialHistory(hasFinancialHistory)
                .build();
    }

    @Override
    public FlatMemberResponse createMember(FlatMemberRequest request, String adminUsername) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }
        if (request.getFlatNumber() == null || request.getFlatNumber().isBlank()) {
            throw new IllegalArgumentException("Flat number cannot be empty");
        }

        String username = request.getUsername().trim();
        String phone = request.getPhoneNumber().trim();
        String flatNum = request.getFlatNumber().trim().toUpperCase();

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username '" + username + "' already exists");
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new IllegalArgumentException("Phone number '" + phone + "' is already registered to another account");
        }

        String rawPassword = request.getPassword() != null && !request.getPassword().isBlank()
                ? request.getPassword()
                : "password123";

        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        User.Role role = User.Role.OWNER;
        if (request.getRole() != null && "ADMIN".equalsIgnoreCase(request.getRole())) {
            role = User.Role.ADMIN;
        }

        String accountStatus = "ACTIVE";
        if (request.getAccountStatus() != null && "Inactive".equalsIgnoreCase(request.getAccountStatus())) {
            accountStatus = "INACTIVE";
        }

        // 1. Create and save User
        User user = User.builder()
                .username(username)
                .name(request.getName() != null ? request.getName().trim() : username)
                .phoneNumber(phone)
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .status(accountStatus)
                .build();

        user = userRepository.save(user);

        try {
            L_User lUser = new L_User(user.getUserId(), user.getUsername());
            lUserRepository.save(lUser);
        } catch (Exception ignored) {}

        // 2. Assign / Create Flat
        Flat flat = flatRepository.findByFlatNumberIgnoreCase(flatNum).orElse(null);
        if (flat != null) {
            flat.setOwner(user);
            flat.setContact(phone);
            flat.setBuildingName(request.getBuildingName() != null ? request.getBuildingName() : "Unique Castle");
            flat.setOccupancyStatus(request.getOccupancyStatus() != null ? request.getOccupancyStatus() : "Moved In");
            flat.setBillingEligibility(request.getBillingEligibility() != null ? request.getBillingEligibility() : "Eligible");
            flat.setMoveInDate(request.getMoveInDate());
            flat.setIsArchived(false);
            flat.setNotes(request.getNotes());
            flat = flatRepository.save(flat);
        } else {
            flat = Flat.builder()
                    .flatNumber(flatNum)
                    .contact(phone)
                    .residents("1")
                    .buildingName(request.getBuildingName() != null ? request.getBuildingName() : "Unique Castle")
                    .occupancyStatus(request.getOccupancyStatus() != null ? request.getOccupancyStatus() : "Moved In")
                    .billingEligibility(request.getBillingEligibility() != null ? request.getBillingEligibility() : "Eligible")
                    .moveInDate(request.getMoveInDate())
                    .isArchived(false)
                    .notes(request.getNotes())
                    .owner(user)
                    .build();

            flat = flatRepository.save(flat);

            try {
                L_Flat lFlat = new L_Flat(flat.getFlatId(), flat.getFlatNumber());
                lFlatRepository.save(lFlat);
            } catch (Exception ignored) {}
        }

        // 3. Log Audit
        MemberAuditLog audit = MemberAuditLog.builder()
                .adminUsername(adminUsername != null ? adminUsername : "admin")
                .memberId(user.getUserId())
                .username(user.getUsername())
                .flatNumber(flat.getFlatNumber())
                .action("CREATE_MEMBER")
                .details(String.format("Created member '%s' (Flat %s, Occupancy: %s, Eligibility: %s)",
                        user.getName(), flat.getFlatNumber(), flat.getOccupancyStatus(), flat.getBillingEligibility()))
                .build();
        memberAuditLogRepository.save(audit);

        return getMemberById(user.getUserId());
    }

    @Override
    public FlatMemberResponse updateMember(Long id, FlatMemberRequest request, String adminUsername) {
        User user = userRepository.findById(id).orElse(null);
        Flat flat = null;
        if (user != null) {
            List<Flat> userFlats = flatRepository.findByOwnerUserId(user.getUserId());
            if (userFlats != null && !userFlats.isEmpty()) {
                flat = userFlats.get(0);
            }
        } else {
            flat = flatRepository.findById(id).orElse(null);
            if (flat != null) {
                user = FlatServiceImpl.getSafeOwner(flat);
            }
        }

        if (user == null) {
            throw new RuntimeException("Member not found with id: " + id);
        }

        // Check phone duplicate if changed
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().equals(user.getPhoneNumber())) {
            String newPhone = request.getPhoneNumber().trim();
            Optional<User> existing = userRepository.findByPhoneNumber(newPhone);
            if (existing.isPresent() && !existing.get().getUserId().equals(user.getUserId())) {
                throw new IllegalArgumentException("Phone number '" + newPhone + "' is already in use by another account");
            }
            user.setPhoneNumber(newPhone);
            if (flat != null) {
                flat.setContact(newPhone);
            }
        }

        if (request.getName() != null) {
            user.setName(request.getName().trim());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim());
        }
        if (request.getAccountStatus() != null) {
            user.setStatus("Inactive".equalsIgnoreCase(request.getAccountStatus()) ? "INACTIVE" : "ACTIVE");
        }

        userRepository.save(user);

        // Update flat attributes
        if (flat != null) {
            if (request.getOccupancyStatus() != null) {
                flat.setOccupancyStatus(request.getOccupancyStatus());
            }
            if (request.getBillingEligibility() != null) {
                flat.setBillingEligibility(request.getBillingEligibility());
            }
            if (request.getMoveInDate() != null) {
                flat.setMoveInDate(request.getMoveInDate());
            }
            if (request.getNotes() != null) {
                flat.setNotes(request.getNotes());
            }
            flatRepository.save(flat);
        }

        // Log Audit
        MemberAuditLog audit = MemberAuditLog.builder()
                .adminUsername(adminUsername != null ? adminUsername : "admin")
                .memberId(user.getUserId())
                .username(user.getUsername())
                .flatNumber(flat != null ? flat.getFlatNumber() : "N/A")
                .action("UPDATE_MEMBER")
                .details(String.format("Updated member details for '%s' (Occupancy: %s, Eligibility: %s, Status: %s)",
                        user.getName(),
                        flat != null ? flat.getOccupancyStatus() : "N/A",
                        flat != null ? flat.getBillingEligibility() : "N/A",
                        user.getStatus()))
                .build();
        memberAuditLogRepository.save(audit);

        return getMemberById(user.getUserId());
    }

    @Override
    public FlatMemberResponse toggleAccountStatus(Long id, String status, String adminUsername) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            Flat flat = flatRepository.findById(id).orElse(null);
            if (flat != null) {
                user = FlatServiceImpl.getSafeOwner(flat);
            }
        }
        if (user == null) {
            throw new RuntimeException("Member not found with id: " + id);
        }

        String normalizedStatus = "Inactive".equalsIgnoreCase(status) || "INACTIVE".equalsIgnoreCase(status)
                ? "INACTIVE"
                : "ACTIVE";

        user.setStatus(normalizedStatus);
        userRepository.save(user);

        MemberAuditLog audit = MemberAuditLog.builder()
                .adminUsername(adminUsername != null ? adminUsername : "admin")
                .memberId(user.getUserId())
                .username(user.getUsername())
                .action("ACTIVE".equals(normalizedStatus) ? "ACTIVATE_ACCOUNT" : "DEACTIVATE_ACCOUNT")
                .details(String.format("Changed account status of '%s' to %s", user.getUsername(), normalizedStatus))
                .build();
        memberAuditLogRepository.save(audit);

        return getMemberById(user.getUserId());
    }

    @Override
    public Map<String, Object> resetPassword(Long id, ResetPasswordRequest request, String adminUsername) {
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            Flat flat = flatRepository.findById(id).orElse(null);
            if (flat != null) {
                user = FlatServiceImpl.getSafeOwner(flat);
            }
        }
        if (user == null) {
            throw new RuntimeException("Member not found with id: " + id);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        MemberAuditLog audit = MemberAuditLog.builder()
                .adminUsername(adminUsername != null ? adminUsername : "admin")
                .memberId(user.getUserId())
                .username(user.getUsername())
                .action("RESET_PASSWORD")
                .details(String.format("Admin reset password for user: %s", user.getUsername()))
                .build();
        memberAuditLogRepository.save(audit);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Password successfully updated for " + user.getUsername());
        return resp;
    }

    @Override
    public Map<String, Object> deleteOrArchiveMember(Long id, String adminUsername) {
        User user = userRepository.findById(id).orElse(null);
        Flat flat = null;
        if (user != null) {
            List<Flat> userFlats = flatRepository.findByOwnerUserId(user.getUserId());
            if (userFlats != null && !userFlats.isEmpty()) {
                flat = userFlats.get(0);
            }
        } else {
            flat = flatRepository.findById(id).orElse(null);
            if (flat != null) {
                user = FlatServiceImpl.getSafeOwner(flat);
            }
        }

        if (user == null && flat == null) {
            throw new RuntimeException("Member not found with id: " + id);
        }

        boolean hasFinancialHistory = checkFinancialHistory(flat, user);

        Map<String, Object> response = new HashMap<>();

        if (hasFinancialHistory) {
            // Soft-archive to preserve referential integrity and audit records
            if (flat != null) {
                flat.setIsArchived(true);
                flat.setOccupancyStatus("Vacated");
                flat.setBillingEligibility("Ineligible");
                flatRepository.save(flat);
            }
            if (user != null) {
                user.setStatus("INACTIVE");
                userRepository.save(user);
            }

            MemberAuditLog audit = MemberAuditLog.builder()
                    .adminUsername(adminUsername != null ? adminUsername : "admin")
                    .memberId(user != null ? user.getUserId() : null)
                    .username(user != null ? user.getUsername() : "")
                    .flatNumber(flat != null ? flat.getFlatNumber() : "")
                    .action("ARCHIVE_MEMBER")
                    .details("Account has financial history; safely archived and deactivated to preserve audit records")
                    .build();
            memberAuditLogRepository.save(audit);

            response.put("action", "ARCHIVED");
            response.put("message", String.format("Member '%s' (Flat %s) has existing financial records. The account has been safely Archived and Deactivated to preserve transaction history.",
                    user != null ? user.getName() : "Resident",
                    flat != null ? flat.getFlatNumber() : "N/A"));
        } else {
            // Permanent removal if safe
            String username = user != null ? user.getUsername() : "";
            String flatNum = flat != null ? flat.getFlatNumber() : "";

            if (flat != null) {
                flatRepository.delete(flat);
            }
            if (user != null) {
                userRepository.delete(user);
            }

            MemberAuditLog audit = MemberAuditLog.builder()
                    .adminUsername(adminUsername != null ? adminUsername : "admin")
                    .memberId(id)
                    .username(username)
                    .flatNumber(flatNum)
                    .action("DELETE_MEMBER")
                    .details("Permanently deleted member: " + username)
                    .build();
            memberAuditLogRepository.save(audit);

            response.put("action", "DELETED");
            response.put("message", String.format("Member record for '%s' was permanently removed.", username));
        }

        return response;
    }

    @Override
    public List<MemberAuditLog> getAuditLogs() {
        return memberAuditLogRepository.findAllByOrderByCreatedAtDesc();
    }

    private boolean checkFinancialHistory(Flat flat, User user) {
        if (flat != null && flat.getFlatId() != null) {
            List<ServiceFeePayment> payments = serviceFeePaymentRepository.findByFlat(flat);
            if (payments != null && !payments.isEmpty()) {
                return true;
            }
        }
        if (user != null && user.getPhoneNumber() != null) {
            List<PaymentVerificationAudit> audits = paymentVerificationAuditRepository.findByPhoneNumber(user.getPhoneNumber());
            if (audits != null && !audits.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}

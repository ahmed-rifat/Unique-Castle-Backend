package Unique_Castle;

import Unique_Castle.Controller.MemberController;
import Unique_Castle.Controller.UserController;
import Unique_Castle.Entity.*;
import Unique_Castle.Repository.*;
import Unique_Castle.RequestData.*;
import Unique_Castle.ResponseData.*;
import Unique_Castle.ServiceImpl.FlatServiceImpl;
import Unique_Castle.ServiceImpl.MemberServiceImpl;
import Unique_Castle.ServiceImpl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FlatMemberManagementTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LUserRepository lUserRepository;

    @Mock
    private FlatRepository flatRepository;

    @Mock
    private LFlatRepository lFlatRepository;

    @Mock
    private ServiceFeePaymentRepository serviceFeePaymentRepository;

    @Mock
    private PaymentVerificationAuditRepository paymentVerificationAuditRepository;

    @Mock
    private MemberAuditLogRepository memberAuditLogRepository;

    @Mock
    private Unique_Castle.Config.JwtUtil jwtUtil;

    @InjectMocks
    private MemberServiceImpl memberService;

    @InjectMocks
    private UserServiceImpl userService;

    @InjectMocks
    private UserController userController;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private User sampleAdmin;
    private User sampleOwner;
    private Flat sampleFlat;

    @BeforeEach
    void setUp() {
        sampleAdmin = User.builder()
                .userId(1L)
                .username("admin")
                .name("Super Admin")
                .phoneNumber("01711000001")
                .password(passwordEncoder.encode("password123"))
                .role(User.Role.ADMIN)
                .status("ACTIVE")
                .build();

        sampleOwner = User.builder()
                .userId(2L)
                .username("owner1")
                .name("Md. Rafiqul Islam")
                .phoneNumber("01712345678")
                .email("rafiqul@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(User.Role.OWNER)
                .status("ACTIVE")
                .build();

        sampleFlat = Flat.builder()
                .flatId(10L)
                .flatNumber("4B")
                .contact("01712345678")
                .residents("2")
                .buildingName("Unique Castle")
                .occupancyStatus("Moved In")
                .billingEligibility("Eligible")
                .moveInDate(LocalDate.of(2026, 1, 15))
                .isArchived(false)
                .owner(sampleOwner)
                .build();
    }

    @Test
    void testGetAllMembersReturnsCompleteFlatMemberDetails() {
        when(flatRepository.findAll()).thenReturn(List.of(sampleFlat));
        when(userRepository.findAll()).thenReturn(List.of(sampleAdmin, sampleOwner));
        when(serviceFeePaymentRepository.findByFlat(sampleFlat)).thenReturn(Collections.emptyList());

        List<FlatMemberResponse> members = memberService.getAllMembers();

        assertNotNull(members);
        assertEquals(2, members.size());

        FlatMemberResponse ownerResp = members.stream()
                .filter(m -> "4B".equals(m.getFlatNumber()))
                .findFirst().orElse(null);

        assertNotNull(ownerResp);
        assertEquals("Md. Rafiqul Islam", ownerResp.getName());
        assertEquals("owner1", ownerResp.getUsername());
        assertEquals("01712345678", ownerResp.getPhoneNumber());
        assertEquals("Moved In", ownerResp.getOccupancyStatus());
        assertEquals("Active", ownerResp.getAccountStatus());
        assertEquals("Eligible", ownerResp.getBillingEligibility());
    }

    @Test
    void testAdminCanRegisterNewFlatMember() {
        FlatMemberRequest request = FlatMemberRequest.builder()
                .name("Tariq Mahmud")
                .username("tariq_5a")
                .phoneNumber("01712000051")
                .email("tariq@example.com")
                .flatNumber("5A")
                .password("secret123")
                .occupancyStatus("Not Moved In")
                .accountStatus("Active")
                .billingEligibility("Ineligible")
                .moveInDate(LocalDate.of(2026, 11, 15))
                .notes("Moving in mid-November")
                .build();

        when(userRepository.existsByUsername("tariq_5a")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("01712000051")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setUserId(50L);
            return u;
        });
        when(flatRepository.findByFlatNumberIgnoreCase("5A")).thenReturn(Optional.empty());
        when(flatRepository.save(any(Flat.class))).thenAnswer(invocation -> {
            Flat f = invocation.getArgument(0);
            f.setFlatId(55L);
            return f;
        });

        // For getMemberById
        User createdUser = User.builder()
                .userId(50L)
                .username("tariq_5a")
                .name("Tariq Mahmud")
                .phoneNumber("01712000051")
                .email("tariq@example.com")
                .role(User.Role.OWNER)
                .status("ACTIVE")
                .build();
        Flat createdFlat = Flat.builder()
                .flatId(55L)
                .flatNumber("5A")
                .occupancyStatus("Not Moved In")
                .billingEligibility("Ineligible")
                .moveInDate(LocalDate.of(2026, 11, 15))
                .owner(createdUser)
                .build();

        when(userRepository.findById(50L)).thenReturn(Optional.of(createdUser));
        when(flatRepository.findByOwnerUserId(50L)).thenReturn(List.of(createdFlat));

        FlatMemberResponse resp = memberService.createMember(request, "admin");

        assertNotNull(resp);
        assertEquals("Tariq Mahmud", resp.getName());
        assertEquals("5A", resp.getFlatNumber());
        assertEquals("Not Moved In", resp.getOccupancyStatus());
        assertEquals("Ineligible", resp.getBillingEligibility());

        verify(memberAuditLogRepository, times(1)).save(any(MemberAuditLog.class));
    }

    @Test
    void testAdminCannotRegisterDuplicateUsernameOrPhone() {
        FlatMemberRequest req1 = FlatMemberRequest.builder()
                .name("Test User")
                .username("admin")
                .phoneNumber("01799999999")
                .flatNumber("6A")
                .build();

        when(userRepository.existsByUsername("admin")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> memberService.createMember(req1, "admin"));

        FlatMemberRequest req2 = FlatMemberRequest.builder()
                .name("Test User")
                .username("unique_user")
                .phoneNumber("01712345678")
                .flatNumber("6A")
                .build();

        when(userRepository.existsByUsername("unique_user")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("01712345678")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> memberService.createMember(req2, "admin"));
    }

    @Test
    void testAdminCanToggleAccountStatus() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleOwner));
        when(flatRepository.findByOwnerUserId(2L)).thenReturn(List.of(sampleFlat));

        // Toggle to Inactive
        FlatMemberResponse resp = memberService.toggleAccountStatus(2L, "Inactive", "admin");

        assertNotNull(resp);
        assertEquals("INACTIVE", sampleOwner.getStatus());
        assertEquals("Inactive", resp.getAccountStatus());
        verify(memberAuditLogRepository, times(1)).save(any(MemberAuditLog.class));

        // Toggle back to Active
        resp = memberService.toggleAccountStatus(2L, "Active", "admin");
        assertEquals("ACTIVE", sampleOwner.getStatus());
        assertEquals("Active", resp.getAccountStatus());
    }

    @Test
    void testInactiveMemberBlockedFromLogin() {
        sampleOwner.setStatus("INACTIVE");
        when(userRepository.findByUsernameIgnoreCase("owner1")).thenReturn(Optional.of(sampleOwner));

        UserRequest req = new UserRequest();
        req.setUsername("owner1");
        req.setPassword("password123");

        ApiGetResponseData loginResp = userService.Login(req);

        assertEquals("false", loginResp.getStatus());
        assertTrue(loginResp.getMessage().contains("deactivated"));
    }

    @Test
    void testAdminCanResetPasswordSecurely() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleOwner));

        ResetPasswordRequest resetReq = new ResetPasswordRequest("newSecurePass123");
        Map<String, Object> resp = memberService.resetPassword(2L, resetReq, "admin");

        assertTrue((Boolean) resp.get("success"));
        assertTrue(passwordEncoder.matches("newSecurePass123", sampleOwner.getPassword()));
        verify(memberAuditLogRepository, times(1)).save(any(MemberAuditLog.class));
    }

    @Test
    void testDeleteMemberWithFinancialHistoryIsSafelyArchivedNotDeleted() {
        ServiceFeePayment existingPayment = new ServiceFeePayment();
        existingPayment.setServiceFeePaymentId(99L);
        existingPayment.setFlat(sampleFlat);

        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleOwner));
        when(flatRepository.findByOwnerUserId(2L)).thenReturn(List.of(sampleFlat));
        when(serviceFeePaymentRepository.findByFlat(sampleFlat)).thenReturn(List.of(existingPayment));

        Map<String, Object> deleteResp = memberService.deleteOrArchiveMember(2L, "admin");

        assertEquals("ARCHIVED", deleteResp.get("action"));
        assertTrue(sampleFlat.getIsArchived());
        assertEquals("Vacated", sampleFlat.getOccupancyStatus());
        assertEquals("Ineligible", sampleFlat.getBillingEligibility());
        assertEquals("INACTIVE", sampleOwner.getStatus());

        // Verify neither Flat nor User was permanently deleted
        verify(flatRepository, never()).delete(any());
        verify(userRepository, never()).delete(any());
        verify(memberAuditLogRepository, times(1)).save(any(MemberAuditLog.class));
    }

    @Test
    void testDeleteMemberWithoutFinancialHistoryIsPermanentlyDeleted() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleOwner));
        when(flatRepository.findByOwnerUserId(2L)).thenReturn(List.of(sampleFlat));
        when(serviceFeePaymentRepository.findByFlat(sampleFlat)).thenReturn(Collections.emptyList());

        Map<String, Object> deleteResp = memberService.deleteOrArchiveMember(2L, "admin");

        assertEquals("DELETED", deleteResp.get("action"));
        verify(flatRepository, times(1)).delete(sampleFlat);
        verify(userRepository, times(1)).delete(sampleOwner);
        verify(memberAuditLogRepository, times(1)).save(any(MemberAuditLog.class));
    }

    @Test
    void testNotMovedInMemberExemptFromServiceFeesForOctober() {
        Flat flat5A = Flat.builder()
                .flatId(55L)
                .flatNumber("5A")
                .occupancyStatus("Not Moved In")
                .billingEligibility("Ineligible")
                .moveInDate(LocalDate.of(2026, 11, 15))
                .isArchived(false)
                .build();

        LocalDate october = LocalDate.of(2026, 10, 1);
        boolean eligibleOct = FlatServiceImpl.isFlatEligibleForMonth(flat5A, october);
        assertFalse(eligibleOct, "Flat 5A must not be eligible for October service fees");

        // When November starts (billing month Nov 1 to Nov 30), moveInDate Nov 15 is within November
        flat5A.setOccupancyStatus("Moved In");
        flat5A.setBillingEligibility("Eligible");
        LocalDate november = LocalDate.of(2026, 11, 1);
        boolean eligibleNov = FlatServiceImpl.isFlatEligibleForMonth(flat5A, november);
        assertTrue(eligibleNov, "Flat 5A must be eligible for November service fees after move-in");
    }

    @Test
    void testMemberCanChangePasswordWithValidCurrentPassword() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("owner1");
        when(userRepository.findByUsernameIgnoreCase("owner1")).thenReturn(Optional.of(sampleOwner));

        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.hasErrors()).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest("password123", "newPassword123", "newPassword123");
        ResponseEntity<?> response = userController.changePassword(req, bindingResult, auth);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(passwordEncoder.matches("newPassword123", sampleOwner.getPassword()));
    }

    @Test
    void testMemberCannotChangePasswordIfCurrentPasswordIncorrect() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("owner1");
        when(userRepository.findByUsernameIgnoreCase("owner1")).thenReturn(Optional.of(sampleOwner));

        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.hasErrors()).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest("wrongOldPassword", "newPassword123", "newPassword123");
        ResponseEntity<?> response = userController.changePassword(req, bindingResult, auth);

        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertTrue(body.get("message").toString().contains("Current password"));
    }
}

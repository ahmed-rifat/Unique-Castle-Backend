package Unique_Castle;

import Unique_Castle.Controller.DashboardController;
import Unique_Castle.Controller.PaymentController;
import Unique_Castle.Controller.ServiceFeeController;
import Unique_Castle.Entity.Flat;
import Unique_Castle.Entity.L_ServiceFee;
import Unique_Castle.Entity.ServiceFee;
import Unique_Castle.Entity.ServiceFeePayment;
import Unique_Castle.Entity.User;
import Unique_Castle.Repository.FlatRepository;
import Unique_Castle.Repository.LServiceFeeRepository;
import Unique_Castle.Repository.PaymentVerificationAuditRepository;
import Unique_Castle.Repository.ServiceFeePaymentRepository;
import Unique_Castle.Repository.ServiceFeeRepository;
import Unique_Castle.Repository.UserRepository;
import Unique_Castle.RequestData.ServiceFeeRequest;
import Unique_Castle.ResponseData.ApiResponseData;
import Unique_Castle.ResponseData.ServiceFeeMonthResponseData;
import Unique_Castle.ServiceImpl.FlatServiceImpl;
import Unique_Castle.ServiceImpl.ServiceFeeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MonthlyServiceFeeRateTest {

    @Mock
    private ServiceFeeRepository serviceFeeRepository;

    @Mock
    private ServiceFeePaymentRepository serviceFeePaymentRepository;

    @Mock
    private LServiceFeeRepository lServiceFeeRepository;

    @Mock
    private FlatRepository flatRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentVerificationAuditRepository paymentVerificationAuditRepository;

    @InjectMocks
    private ServiceFeeServiceImpl serviceFeeService;

    @InjectMocks
    private DashboardController dashboardController;

    @InjectMocks
    private ServiceFeeController serviceFeeController;

    @InjectMocks
    private PaymentController paymentController;

    @InjectMocks
    private FlatServiceImpl flatService;

    private ServiceFee octFee;
    private Flat sampleFlat;
    private User sampleOwner;

    @BeforeEach
    void setUp() {
        octFee = ServiceFee.builder()
                .serviceFeeId(1L)
                .month(LocalDate.of(2026, 10, 1))
                .DueDate(LocalDate.of(2026, 10, 10))
                .caretakerFee(BigDecimal.valueOf(800))
                .wasteFee(BigDecimal.valueOf(200))
                .liftFee(BigDecimal.valueOf(500))
                .waterFee(BigDecimal.ZERO)
                .electricityFee(BigDecimal.ZERO)
                .otherFee(BigDecimal.valueOf(1000))
                .totalFee(BigDecimal.valueOf(2500))
                .status("PUBLISHED")
                .publishedDate(LocalDate.of(2026, 10, 1))
                .build();

        sampleOwner = User.builder()
                .userId(10L)
                .username("owner1")
                .phoneNumber("01712345678")
                .role(User.Role.OWNER)
                .build();

        sampleFlat = Flat.builder()
                .flatId(100L)
                .flatNumber("4B")
                .contact("01712345678")
                .residents("3")
                .owner(sampleOwner)
                .build();
    }

    // 1. Admin Functionality: Setting November 2026 rate in advance with no existing records
    @Test
    void testAdminCanSetNovemberRateInAdvanceWhenNoRecordsExist() {
        LocalDate novMonth = LocalDate.of(2026, 11, 1);
        when(serviceFeeRepository.findByMonth(novMonth)).thenReturn(Optional.empty());
        when(serviceFeeRepository.findByCurrentMonth(novMonth, novMonth.plusMonths(1))).thenReturn(Optional.empty());
        when(serviceFeeRepository.save(any(ServiceFee.class))).thenAnswer(invocation -> {
            ServiceFee sf = invocation.getArgument(0);
            sf.setServiceFeeId(2L);
            return sf;
        });

        ServiceFeeRequest request = new ServiceFeeRequest();
        request.setMonth(novMonth);
        request.setCaretakerFee(BigDecimal.valueOf(900));
        request.setLiftFee(BigDecimal.valueOf(600));
        request.setWasteFee(BigDecimal.valueOf(250));
        request.setOtherFee(BigDecimal.valueOf(1050));
        request.setTotalFee(BigDecimal.valueOf(2800));

        ApiResponseData response = serviceFeeService.saveAndUpdateServiceFee(request);

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertTrue(response.getMessage().contains("created successfully"));

        ServiceFeeMonthResponseData data = (ServiceFeeMonthResponseData) response.getData();
        assertEquals(BigDecimal.valueOf(2800), data.getTotalFee());
        assertEquals(novMonth, data.getMonth());
        verify(serviceFeeRepository, times(1)).save(any(ServiceFee.class));
        verify(lServiceFeeRepository, times(1)).save(any(L_ServiceFee.class));
    }

    // 2. Admin Functionality: If November rate already exists, update it instead of duplicate error
    @Test
    void testAdminUpdateExistingNovemberRateDoesNotCauseDuplicateError() {
        LocalDate novMonth = LocalDate.of(2026, 11, 1);
        ServiceFee existingNov = ServiceFee.builder()
                .serviceFeeId(2L)
                .month(novMonth)
                .DueDate(LocalDate.of(2026, 11, 10))
                .caretakerFee(BigDecimal.valueOf(800))
                .wasteFee(BigDecimal.valueOf(200))
                .liftFee(BigDecimal.valueOf(500))
                .otherFee(BigDecimal.valueOf(1000))
                .totalFee(BigDecimal.valueOf(2500))
                .status("PUBLISHED")
                .build();

        when(serviceFeeRepository.findByMonth(novMonth)).thenReturn(Optional.of(existingNov));
        when(serviceFeePaymentRepository.findByServiceFee(existingNov)).thenReturn(new ArrayList<>());
        when(serviceFeeRepository.save(any(ServiceFee.class))).thenAnswer(inv -> inv.getArgument(0));

        // Admin saves updated rates without serviceFeeId
        ServiceFeeRequest updateRequest = new ServiceFeeRequest();
        updateRequest.setMonth(novMonth);
        updateRequest.setServiceFeeId(null); // id is null!
        updateRequest.setCaretakerFee(BigDecimal.valueOf(1000));
        updateRequest.setTotalFee(BigDecimal.valueOf(3000));

        ApiResponseData response = serviceFeeService.saveAndUpdateServiceFee(updateRequest);

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertTrue(response.getMessage().contains("updated successfully"));

        ServiceFeeMonthResponseData data = (ServiceFeeMonthResponseData) response.getData();
        assertEquals(BigDecimal.valueOf(3000), data.getTotalFee());
        assertEquals(BigDecimal.valueOf(1000), data.getCaretakerFee());
        // Verify it updated the existing record, did not try to insert a new one
        assertEquals(2L, data.getServiceFeeId());
    }

    // 3. Repeated saves do not cause duplicate entry errors
    @Test
    void testRepeatedSavesDoNotCreateDuplicates() {
        LocalDate novMonth = LocalDate.of(2026, 11, 1);
        ServiceFee novFee = ServiceFee.builder()
                .serviceFeeId(2L)
                .month(novMonth)
                .DueDate(LocalDate.of(2026, 11, 10))
                .totalFee(BigDecimal.valueOf(2800))
                .status("PUBLISHED")
                .build();

        when(serviceFeeRepository.findByMonth(novMonth)).thenReturn(Optional.of(novFee));
        when(serviceFeePaymentRepository.findByServiceFee(novFee)).thenReturn(new ArrayList<>());
        when(serviceFeeRepository.save(any(ServiceFee.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceFeeRequest req1 = new ServiceFeeRequest();
        req1.setMonth(novMonth);
        req1.setTotalFee(BigDecimal.valueOf(2800));
        ApiResponseData resp1 = serviceFeeService.saveAndUpdateServiceFee(req1);
        assertEquals("success", resp1.getStatus());

        ServiceFeeRequest req2 = new ServiceFeeRequest();
        req2.setMonth(novMonth);
        req2.setTotalFee(BigDecimal.valueOf(2900));
        ApiResponseData resp2 = serviceFeeService.saveAndUpdateServiceFee(req2);
        assertEquals("success", resp2.getStatus());
        assertEquals(BigDecimal.valueOf(2900), ((ServiceFeeMonthResponseData) resp2.getData()).getTotalFee());
    }

    // 4. Rate updates preserve payment rules: Cannot alter totalFee if active payments exist
    @Test
    void testCannotModifyTotalRateIfActivePaymentsExist() {
        LocalDate novMonth = LocalDate.of(2026, 11, 1);
        ServiceFee novFee = ServiceFee.builder()
                .serviceFeeId(2L)
                .month(novMonth)
                .totalFee(BigDecimal.valueOf(2800))
                .build();

        ServiceFeePayment activePayment = ServiceFeePayment.builder()
                .serviceFeePaymentId(101L)
                .flat(sampleFlat)
                .serviceFee(novFee)
                .paid("Y")
                .paidDate(LocalDate.of(2026, 11, 2))
                .transactionId("BK12345")
                .build();

        when(serviceFeeRepository.findByMonth(novMonth)).thenReturn(Optional.of(novFee));
        when(serviceFeePaymentRepository.findByServiceFee(novFee)).thenReturn(List.of(activePayment));

        ServiceFeeRequest req = new ServiceFeeRequest();
        req.setMonth(novMonth);
        req.setTotalFee(BigDecimal.valueOf(3500)); // Trying to change total

        ApiResponseData resp = serviceFeeService.saveAndUpdateServiceFee(req);
        assertEquals("error", resp.getStatus());
        assertTrue(resp.getMessage().contains("Cannot modify total service charge amount"));
    }

    // 5. User Dashboard in October: Users see only October charges and cannot see November charges before November begins
    @Test
    void testUserDashboardShowsOctoberAndClampsFutureMonthQueryBeforeNovemberBegins() {
        LocalDate octMonth = LocalDate.of(2026, 10, 1);
        when(serviceFeeRepository.findByMonth(octMonth)).thenReturn(Optional.of(octFee));
        when(flatRepository.findByFlatNumberIgnoreCase("4B")).thenReturn(Optional.of(sampleFlat));
        when(serviceFeePaymentRepository.findByFlatAndServiceFee(sampleFlat, octFee)).thenReturn(Optional.empty());
        when(serviceFeePaymentRepository.findPaymentsInMonth(sampleFlat.getFlatId(), octMonth, octMonth.plusMonths(1))).thenReturn(Optional.empty());

        // When user asks for November 2026 while in October 2026:
        ResponseEntity<?> resp = dashboardController.getOwnerDashboard("4B", "November", 2026, null);
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());

        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        // Since November is in the future relative to October 2026, it must clamp to October 2026
        assertEquals("October", body.get("billingMonth"));
        assertEquals(2026, body.get("billingYear"));
        assertEquals(2500.0, body.get("totalServiceCharge"));
    }

    // 6. User breakdown endpoint for future month returns Unpublished message before month begins
    @Test
    void testServiceFeeBreakdownHidesFutureMonthChargesBeforeMonthBegins() {
        ResponseEntity<?> resp = serviceFeeController.getServiceFeeBreakdown("4B", "November", 2026);
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());

        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertEquals(false, body.get("isPublished"));
        assertEquals("Unpublished", body.get("status"));
        assertTrue(String.valueOf(body.get("message")).contains("visible once the billing month begins"));
    }

    // 7. Apply Rates to All Users for November: Admin view shows November configured rates and not October rates
    @Test
    void testNovemberChargesAppliedToAllFlatsCorrectlyWithoutGeneratingAsOctober() {
        LocalDate novMonth = LocalDate.of(2026, 11, 1);
        ServiceFee novFee = ServiceFee.builder()
                .serviceFeeId(2L)
                .month(novMonth)
                .DueDate(LocalDate.of(2026, 11, 10))
                .caretakerFee(BigDecimal.valueOf(1000))
                .wasteFee(BigDecimal.valueOf(300))
                .liftFee(BigDecimal.valueOf(600))
                .waterFee(BigDecimal.ZERO)
                .electricityFee(BigDecimal.ZERO)
                .otherFee(BigDecimal.valueOf(1100))
                .totalFee(BigDecimal.valueOf(3000))
                .status("PUBLISHED")
                .build();

        when(serviceFeeRepository.findByMonth(novMonth)).thenReturn(Optional.of(novFee));
        when(flatRepository.findAll()).thenReturn(List.of(sampleFlat));
        when(serviceFeePaymentRepository.findByFlatAndServiceFee(sampleFlat, novFee)).thenReturn(Optional.empty());
        when(serviceFeePaymentRepository.findPaymentsInMonth(sampleFlat.getFlatId(), novMonth, novMonth.plusMonths(1))).thenReturn(Optional.empty());

        // Admin summary for November
        ResponseEntity<?> summaryResp = dashboardController.getAdminSummary("November", 2026);
        Map<String, Object> summary = (Map<String, Object>) summaryResp.getBody();
        assertEquals(BigDecimal.valueOf(3000), summary.get("totalBilled"));
        assertEquals(1, summary.get("notPaidFlats"));

        // Admin flats for November
        ResponseEntity<?> flatsResp = dashboardController.getAdminFlats("November", 2026, null, null);
        List<Map<String, Object>> flats = (List<Map<String, Object>>) flatsResp.getBody();
        assertEquals(1, flats.size());
        Map<String, Object> flatRow = flats.get(0);
        assertEquals(3000.0, flatRow.get("totalCharge")); // Configured November rate! Not 2500 October rate!
        assertEquals("Not Paid", flatRow.get("status"));
    }
}

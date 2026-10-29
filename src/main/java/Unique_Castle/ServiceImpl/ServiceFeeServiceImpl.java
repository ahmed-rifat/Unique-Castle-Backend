package Unique_Castle.ServiceImpl;

import Unique_Castle.Entity.L_ServiceFee;
import Unique_Castle.Entity.ServiceFee;
import Unique_Castle.Entity.ServiceFeePayment;
import Unique_Castle.Repository.LServiceFeeRepository;
import Unique_Castle.Repository.ServiceFeePaymentRepository;
import Unique_Castle.Repository.ServiceFeeRepository;
import Unique_Castle.RequestData.ServiceFeeRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.ApiResponseData;
import Unique_Castle.ResponseData.ServiceFeeMonthResponseData;
import Unique_Castle.Service.ServiceFeeService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ServiceFeeServiceImpl implements ServiceFeeService {

    @Autowired
    private ServiceFeeRepository serviceFeeRepository;

    @Autowired
    private ServiceFeePaymentRepository serviceFeePaymentRepository;

    @Autowired
    private LServiceFeeRepository lServiceFeeRepository;

    @Override
    @Transactional
    public ApiResponseData saveAndUpdateServiceFee(ServiceFeeRequest serviceFeeRequest) {
        LocalDate resolvedMonth = null;
        if (serviceFeeRequest.getMonth() != null) {
            resolvedMonth = serviceFeeRequest.getMonth().withDayOfMonth(1);
        } else if (serviceFeeRequest.getResolvedMonth() != null) {
            resolvedMonth = serviceFeeRequest.getResolvedMonth();
        } else {
            resolvedMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        }
        final LocalDate targetMonth = resolvedMonth;

        // 1. Locate existing ServiceFee either by provided ID or by target month
        ServiceFee serviceFeeExisting = null;
        if (serviceFeeRequest.getServiceFeeId() != null) {
            serviceFeeExisting = serviceFeeRepository.findById(serviceFeeRequest.getServiceFeeId()).orElse(null);
        }
        if (serviceFeeExisting == null) {
            serviceFeeExisting = serviceFeeRepository.findByMonth(targetMonth)
                    .or(() -> serviceFeeRepository.findByCurrentMonth(targetMonth, targetMonth.plusMonths(1)))
                    .orElse(null);
        }

        if (serviceFeeExisting != null) {
            // Updating existing ServiceFee rate configuration


            // Check if active verified/pending payments are linked to this service charge
            List<ServiceFeePayment> payments = serviceFeePaymentRepository.findByServiceFee(serviceFeeExisting);
            boolean hasActivePayments = payments != null && payments.stream().anyMatch(p ->
                    "Y".equalsIgnoreCase(p.getPaid()) ||
                    "PAID".equalsIgnoreCase(p.getPaid()) ||
                    "P".equalsIgnoreCase(p.getPaid()) ||
                    "PENDING".equalsIgnoreCase(p.getPaid()) ||
                    (p.getTransactionId() != null && !p.getTransactionId().isBlank() && !p.getTransactionId().startsWith("REJ-"))
            );

            // Do not silently overwrite historical charges linked to active existing payments
            if (hasActivePayments) {
                if (serviceFeeRequest.getTotalFee() != null &&
                    serviceFeeExisting.getTotalFee() != null &&
                    serviceFeeRequest.getTotalFee().compareTo(serviceFeeExisting.getTotalFee()) != 0) {
                    long activeCount = payments.stream().filter(p ->
                            "Y".equalsIgnoreCase(p.getPaid()) || "PAID".equalsIgnoreCase(p.getPaid()) ||
                            "P".equalsIgnoreCase(p.getPaid()) || "PENDING".equalsIgnoreCase(p.getPaid())
                    ).count();
                    return new ApiResponseData<>("error", "Cannot modify total service charge amount for a month with existing payment records (" + activeCount + " payments found).");
                }
            }

            if (serviceFeeRequest.getCaretakerFee() != null) serviceFeeExisting.setCaretakerFee(serviceFeeRequest.getCaretakerFee());
            if (serviceFeeRequest.getLiftFee() != null) serviceFeeExisting.setLiftFee(serviceFeeRequest.getLiftFee());
            if (serviceFeeRequest.getWasteFee() != null) serviceFeeExisting.setWasteFee(serviceFeeRequest.getWasteFee());
            if (serviceFeeRequest.getWaterFee() != null) serviceFeeExisting.setWaterFee(serviceFeeRequest.getWaterFee());
            if (serviceFeeRequest.getElectricityFee() != null) serviceFeeExisting.setElectricityFee(serviceFeeRequest.getElectricityFee());
            if (serviceFeeRequest.getOtherFee() != null) serviceFeeExisting.setOtherFee(serviceFeeRequest.getOtherFee());
            if (serviceFeeRequest.getDueDate() != null) {
                serviceFeeExisting.setDueDate(serviceFeeRequest.getDueDate());
            } else if (serviceFeeExisting.getDueDate() == null && targetMonth != null) {
                serviceFeeExisting.setDueDate(targetMonth.plusDays(9));
            }

            BigDecimal calculatedTotal = BigDecimal.ZERO
                    .add(serviceFeeExisting.getCaretakerFee() != null ? serviceFeeExisting.getCaretakerFee() : BigDecimal.ZERO)
                    .add(serviceFeeExisting.getLiftFee() != null ? serviceFeeExisting.getLiftFee() : BigDecimal.ZERO)
                    .add(serviceFeeExisting.getWasteFee() != null ? serviceFeeExisting.getWasteFee() : BigDecimal.ZERO)
                    .add(serviceFeeExisting.getWaterFee() != null ? serviceFeeExisting.getWaterFee() : BigDecimal.ZERO)
                    .add(serviceFeeExisting.getElectricityFee() != null ? serviceFeeExisting.getElectricityFee() : BigDecimal.ZERO)
                    .add(serviceFeeExisting.getOtherFee() != null ? serviceFeeExisting.getOtherFee() : BigDecimal.ZERO);

            if (!hasActivePayments) {
                if (serviceFeeRequest.getTotalFee() != null) {
                    serviceFeeExisting.setTotalFee(serviceFeeRequest.getTotalFee());
                } else if (calculatedTotal.compareTo(BigDecimal.ZERO) > 0) {
                    serviceFeeExisting.setTotalFee(calculatedTotal);
                }
            }

            String status = (serviceFeeRequest.getStatus() != null && !serviceFeeRequest.getStatus().isBlank())
                    ? serviceFeeRequest.getStatus().toUpperCase()
                    : (serviceFeeExisting.getStatus() != null ? serviceFeeExisting.getStatus() : "PUBLISHED");
            serviceFeeExisting.setStatus(status);
            if ("PUBLISHED".equalsIgnoreCase(status) && serviceFeeExisting.getPublishedDate() == null) {
                serviceFeeExisting.setPublishedDate(LocalDate.now());
            }

            serviceFeeExisting = serviceFeeRepository.save(serviceFeeExisting);
            syncLServiceFee(serviceFeeExisting);

            return new ApiResponseData<>("success", "Monthly Service Fee for " + (targetMonth != null ? targetMonth.getMonth() + " " + targetMonth.getYear() : "") + " updated successfully", mapToResponseData(serviceFeeExisting));
        } else {
            // Creating new ServiceFee record for target month
            ServiceFee newServiceFee = new ServiceFee();
            newServiceFee.setMonth(targetMonth);
            newServiceFee.setDueDate(serviceFeeRequest.getDueDate() != null ? serviceFeeRequest.getDueDate() : targetMonth.plusDays(9));
            newServiceFee.setCaretakerFee(serviceFeeRequest.getCaretakerFee() != null ? serviceFeeRequest.getCaretakerFee() : BigDecimal.valueOf(800));
            newServiceFee.setLiftFee(serviceFeeRequest.getLiftFee() != null ? serviceFeeRequest.getLiftFee() : BigDecimal.valueOf(500));
            newServiceFee.setWasteFee(serviceFeeRequest.getWasteFee() != null ? serviceFeeRequest.getWasteFee() : BigDecimal.valueOf(200));
            newServiceFee.setWaterFee(serviceFeeRequest.getWaterFee() != null ? serviceFeeRequest.getWaterFee() : BigDecimal.ZERO);
            newServiceFee.setElectricityFee(serviceFeeRequest.getElectricityFee() != null ? serviceFeeRequest.getElectricityFee() : BigDecimal.ZERO);
            newServiceFee.setOtherFee(serviceFeeRequest.getOtherFee() != null ? serviceFeeRequest.getOtherFee() : BigDecimal.valueOf(1000));

            BigDecimal calculatedTotal = newServiceFee.getCaretakerFee()
                    .add(newServiceFee.getLiftFee())
                    .add(newServiceFee.getWasteFee())
                    .add(newServiceFee.getWaterFee())
                    .add(newServiceFee.getElectricityFee())
                    .add(newServiceFee.getOtherFee());

            newServiceFee.setTotalFee(serviceFeeRequest.getTotalFee() != null ? serviceFeeRequest.getTotalFee() : calculatedTotal);

            String status = (serviceFeeRequest.getStatus() != null && !serviceFeeRequest.getStatus().isBlank())
                    ? serviceFeeRequest.getStatus().toUpperCase()
                    : "PUBLISHED";
            newServiceFee.setStatus(status);
            if ("PUBLISHED".equalsIgnoreCase(status)) {
                newServiceFee.setPublishedDate(LocalDate.now());
            }

            try {
                newServiceFee = serviceFeeRepository.save(newServiceFee);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                // Defensive recovery against concurrent insert or constraint race condition
                ServiceFee raceExisting = serviceFeeRepository.findByMonth(targetMonth)
                        .or(() -> serviceFeeRepository.findByCurrentMonth(targetMonth, targetMonth.plusMonths(1)))
                        .orElse(null);
                if (raceExisting != null) {
                    raceExisting.setCaretakerFee(newServiceFee.getCaretakerFee());
                    raceExisting.setLiftFee(newServiceFee.getLiftFee());
                    raceExisting.setWasteFee(newServiceFee.getWasteFee());
                    raceExisting.setWaterFee(newServiceFee.getWaterFee());
                    raceExisting.setElectricityFee(newServiceFee.getElectricityFee());
                    raceExisting.setOtherFee(newServiceFee.getOtherFee());
                    raceExisting.setTotalFee(newServiceFee.getTotalFee());
                    raceExisting.setDueDate(newServiceFee.getDueDate());
                    raceExisting.setStatus(newServiceFee.getStatus());
                    newServiceFee = serviceFeeRepository.save(raceExisting);
                } else {
                    throw ex;
                }
            }

            syncLServiceFee(newServiceFee);

            return new ApiResponseData<>("success", "Monthly Service Fee for " + targetMonth.getMonth() + " " + targetMonth.getYear() + " created successfully (" + status + ")", mapToResponseData(newServiceFee));
        }
    }

    private void syncLServiceFee(ServiceFee sf) {
        if (sf == null || sf.getServiceFeeId() == null) return;
        try {
            Optional<L_ServiceFee> lsfOpt = lServiceFeeRepository.findById(sf.getServiceFeeId())
                    .or(() -> lServiceFeeRepository.findByMonth(sf.getMonth()));
            L_ServiceFee lsf = lsfOpt.orElseGet(() -> {
                L_ServiceFee newLsf = new L_ServiceFee();
                newLsf.setServiceFeeId(sf.getServiceFeeId());
                return newLsf;
            });
            lsf.setServiceFeeId(sf.getServiceFeeId());
            lsf.setMonth(sf.getMonth());
            lServiceFeeRepository.save(lsf);
        } catch (Exception ignored) {
        }
    }

    private ServiceFeeMonthResponseData mapToResponseData(ServiceFee fee) {
        ServiceFeeMonthResponseData responseData = new ServiceFeeMonthResponseData();
        responseData.setServiceFeeId(fee.getServiceFeeId());
        responseData.setMonth(fee.getMonth());
        responseData.setDueDate(fee.getDueDate());
        responseData.setTotalFee(fee.getTotalFee());
        responseData.setCaretakerFee(fee.getCaretakerFee());
        responseData.setLiftFee(fee.getLiftFee());
        responseData.setWasteFee(fee.getWasteFee());
        responseData.setWaterFee(fee.getWaterFee() != null ? fee.getWaterFee() : BigDecimal.ZERO);
        responseData.setElectricityFee(fee.getElectricityFee() != null ? fee.getElectricityFee() : BigDecimal.ZERO);
        responseData.setOtherFee(fee.getOtherFee());
        responseData.setStatus(fee.getStatus() != null ? fee.getStatus() : "PUBLISHED");
        responseData.setPublishedDate(fee.getPublishedDate() != null ? fee.getPublishedDate() : fee.getMonth());

        List<ServiceFeePayment> payments = serviceFeePaymentRepository.findByServiceFee(fee);
        int count = 0;
        if (payments != null) {
            for (ServiceFeePayment p : payments) {
                if ("Y".equalsIgnoreCase(p.getPaid()) || "PAID".equalsIgnoreCase(p.getPaid()) ||
                    "P".equalsIgnoreCase(p.getPaid()) || "PENDING".equalsIgnoreCase(p.getPaid())) {
                    count++;
                }
            }
        }
        responseData.setPaymentCount(count);
        responseData.setHasPayments(count > 0);

        return responseData;
    }

    @Override
    public ApiGetResponseData getAllServiceFees() {
        List<ServiceFee> fees = serviceFeeRepository.findAllByOrderByMonthDesc();
        if (fees.isEmpty()) {
            fees = serviceFeeRepository.findAll();
        }

        if (fees.isEmpty()) {
            return new ApiGetResponseData<>("error", "No service fees configured yet", null);
        }

        List<ServiceFeeMonthResponseData> responseList = new ArrayList<>();

        for (ServiceFee fee : fees) {
            responseList.add(mapToResponseData(fee));
        }

        return new ApiGetResponseData<>("success", "Service Fees retrieved successfully", responseList);
    }
}


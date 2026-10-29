package Unique_Castle.Controller;

import Unique_Castle.Entity.Flat;
import Unique_Castle.Entity.ServiceFeePayment;
import Unique_Castle.RequestData.FlatRequest;
import Unique_Castle.RequestData.ServiceFeePaymentRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.ApiResponseData;
import Unique_Castle.Service.ServiceFeePaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
public class ServiceFeePaymentServiceController {

    @Autowired
    private ServiceFeePaymentService serviceFeePaymentService;


    @PostMapping("/service-fee-payment")
    public ApiResponseData saveAndUpdateServiceFeePayment(@Valid @RequestBody ServiceFeePaymentRequest serviceFeePaymentRequest, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errorMessages = bindingResult.getAllErrors()
                    .stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return new ApiResponseData("false", errorMessages);
        }
        return serviceFeePaymentService.saveAndUpdateServiceFeePayment(serviceFeePaymentRequest);
    }

    @GetMapping("/get-service-fee-payments")
    public ApiGetResponseData getAllServiceFeePayments() {
        return serviceFeePaymentService.getAllServiceFeePayments();

    }
    @GetMapping("/get-service-fee-payments-by-flatId")
    public ApiGetResponseData getServiceFeePaymentsByFlatId(Long flatId) {
        return serviceFeePaymentService.getServiceFeePaymentsByFlatId(flatId);
    }

    @PostMapping("/payment-verification")
    public ApiGetResponseData PaymentVerification(
            @RequestParam(value = "PhoneNumber", required = false) String PhoneNumber,
            @RequestParam(value = "phoneNumber", required = false) String altPhoneNumber,
            @RequestParam(value = "TransactionId", required = false) String TransactionId,
            @RequestParam(value = "transactionId", required = false) String altTransactionId) {
        String phone = PhoneNumber != null ? PhoneNumber : altPhoneNumber;
        String trx = TransactionId != null ? TransactionId : altTransactionId;
        return serviceFeePaymentService.PaymentVerification(phone, trx);
    }

}

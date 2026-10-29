package Unique_Castle.Service;


import Unique_Castle.RequestData.ServiceFeePaymentRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.ApiResponseData;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public interface ServiceFeePaymentService {
    ApiResponseData saveAndUpdateServiceFeePayment(ServiceFeePaymentRequest serviceFeePaymentRequest);

    ApiGetResponseData getAllServiceFeePayments();

    ApiGetResponseData getServiceFeePaymentsByFlatId(Long flatId);

    ApiGetResponseData PaymentVerification(String PhoneNumber, String TransactionId);
}

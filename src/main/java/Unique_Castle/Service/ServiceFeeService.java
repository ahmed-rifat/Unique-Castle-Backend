package Unique_Castle.Service;

import Unique_Castle.Entity.ServiceFee;
import Unique_Castle.RequestData.ServiceFeeRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.ApiResponseData;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;

@Service
public interface ServiceFeeService {

    ApiResponseData saveAndUpdateServiceFee(ServiceFeeRequest serviceFeeRequest);

    ApiGetResponseData getAllServiceFees();



}

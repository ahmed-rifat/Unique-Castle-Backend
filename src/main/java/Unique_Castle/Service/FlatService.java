package Unique_Castle.Service;


import Unique_Castle.Entity.Flat;
import Unique_Castle.RequestData.FlatRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.ApiResponseData;
import Unique_Castle.ResponseData.FlatResponseData;
import Unique_Castle.ResponseData.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.awt.print.Pageable;
import java.time.LocalDate;
import java.util.Map;

@Service
public interface FlatService {
    ApiResponseData saveAndUpdateFlat(FlatRequest flatRequest);

    ApiGetResponseData getAllFlats(Authentication authentication);

    ApiGetResponseData getAllFlats(Authentication authentication, Integer year, Integer month);

    ApiGetResponseData getAllFlatsByMonth(int year, int month);


    ApiGetResponseData getFlatsByOwnerId(Long ownerId);

    PageResponse<FlatResponseData> getFlatsWithPagination(int page, int size);


}

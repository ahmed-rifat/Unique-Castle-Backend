package Unique_Castle.Controller;


import Unique_Castle.Entity.Flat;
import Unique_Castle.RequestData.FlatRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.ApiResponseData;
import Unique_Castle.ResponseData.FlatResponseData;
import Unique_Castle.ResponseData.PageResponse;
import Unique_Castle.Service.FlatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.stream.Collectors;

@RestController
public class FlatController {

    @Autowired
    private FlatService flatService;

    @GetMapping("/flat")
    public String hello() {
        return "Hello, all flat member!";
    }

    @GetMapping("/get-flats")
    public ApiGetResponseData getAllFlats(
            Authentication authentication,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return flatService.getAllFlats(authentication, year, month);
    }

    @GetMapping("/get-flats-by-month")
    public ApiGetResponseData getAllFlatsByMonth( int year, int month) {
        return flatService.getAllFlatsByMonth(year, month);
    }

    @GetMapping("/get-flats-by-ownerId")
    public ApiGetResponseData getFlatsByOwner(Long ownerId) {
        return flatService.getFlatsByOwnerId(ownerId);
    }

    @PostMapping("/save-update-flat")
    public ApiResponseData<Flat> addFlat(@Valid @RequestBody FlatRequest flatRequest , BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errorMessages = bindingResult.getAllErrors()
                    .stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return new ApiResponseData<>("false", errorMessages);
        }
        return flatService.saveAndUpdateFlat(flatRequest);
    }

    @GetMapping("/get-flats-with-pagination")
    public ResponseEntity<PageResponse<FlatResponseData>> getFlatsWithPagination(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        PageResponse<FlatResponseData> response = flatService.getFlatsWithPagination(page, size);
        return ResponseEntity.ok(response);
    }






}

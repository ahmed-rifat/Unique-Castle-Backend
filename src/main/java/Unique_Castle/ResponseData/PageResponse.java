package Unique_Castle.ResponseData;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class PageResponse<T> {
        private List<T> data;       // actual list of flats
        private int currentPage;    // 1-based
        private int totalPages;
        private long totalItems;
    }



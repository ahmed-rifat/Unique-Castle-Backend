package Unique_Castle.ResponseData;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponseData<T> {

    private String status; // "success" / "error"
    private String message;
    private T data; // Can hold any type of object

    public ApiResponseData(String status, String message) {
        this.status = status;
        this.message = message;
    }
}

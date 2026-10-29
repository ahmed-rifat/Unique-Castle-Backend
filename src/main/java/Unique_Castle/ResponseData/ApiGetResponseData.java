package Unique_Castle.ResponseData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiGetResponseData<T> {

    private String status; // "success" / "error"
    private String message;
    private T data; // Can hold any type of object
}


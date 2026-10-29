package Unique_Castle.ResponseData;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlatResponse {
    private Long flatId;
    private String flatNumber;
    private String phoneNumber;
    private String residents;
}

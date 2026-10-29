package Unique_Castle.ResponseData;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long userId;
    private String username;
    private String phoneNumber;
    private String role;
    private List<FlatResponse> flatsList = new ArrayList<>();

}
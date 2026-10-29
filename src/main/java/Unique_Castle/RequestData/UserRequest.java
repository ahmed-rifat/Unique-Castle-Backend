package Unique_Castle.RequestData;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRequest {
    private String username;
    private String phoneNumber;

    @NotBlank(message = "Password cannot be empty")
    private String password;

    public String getIdentifier() {
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        return phoneNumber != null ? phoneNumber.trim() : "";
    }
}

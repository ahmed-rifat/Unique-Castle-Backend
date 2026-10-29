package Unique_Castle.Service;


import Unique_Castle.RequestData.UserRegisterRequest;
import Unique_Castle.RequestData.UserRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;


public interface UserService {
    ApiGetResponseData Login(UserRequest userRequest);

    ApiGetResponseData RegisterAndUpdate(UserRegisterRequest userRegisterRequest);

    ApiGetResponseData updateUserRole(Long userId, String newRole);

    ApiGetResponseData Logout(HttpSession session);


    ApiGetResponseData getAllUsers();
}

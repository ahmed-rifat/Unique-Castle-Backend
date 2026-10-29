package Unique_Castle.ServiceImpl;

import Unique_Castle.Config.JwtFilter;
import Unique_Castle.Config.JwtUtil;
import Unique_Castle.Entity.Flat;
import Unique_Castle.Entity.User;
import Unique_Castle.Repository.FlatRepository;
import Unique_Castle.Repository.UserRepository;
import Unique_Castle.RequestData.UserRegisterRequest;
import Unique_Castle.RequestData.UserRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.FlatResponse;
import Unique_Castle.ResponseData.LoginResponseData;
import Unique_Castle.ResponseData.UserResponse;
import Unique_Castle.Service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();



    @Override
    public ApiGetResponseData Login(UserRequest userRequest) {
        String identifier = userRequest.getIdentifier();
        if (identifier == null || identifier.isBlank()) {
            return new ApiGetResponseData<>("false", "Username or phone number cannot be empty", null);
        }

        Optional<User> userInfo = userRepository.findByUsernameIgnoreCase(identifier);
        if (userInfo.isEmpty()) {
            userInfo = userRepository.findByPhoneNumber(identifier);
        }
        if (userInfo.isEmpty()) {
            userInfo = userRepository.findByName(identifier);
        }

        if (userInfo.isEmpty()) {
            return new ApiGetResponseData<>("false", "User not found", null);
        }

        User user = userInfo.get();
        String reqPass = userRequest.getPassword();
        String dbPass = user.getPassword();

        boolean matches = (dbPass != null && passwordEncoder.matches(reqPass, dbPass))
                || reqPass.equals(dbPass);

        if (!matches) {
            return new ApiGetResponseData<>("false", "Invalid credentials", null);
        }

        if ("INACTIVE".equalsIgnoreCase(user.getStatus())) {
            return new ApiGetResponseData<>("false", "Your account has been deactivated. Please contact the building administrator.", null);
        }

        // Auto-upgrade plain text password to BCrypt hash
        if (reqPass.equals(dbPass)) {
            user.setPassword(passwordEncoder.encode(reqPass));
            userRepository.save(user);
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        String role = user.getRole().name();

        String flatNumber = "Management Office";
        if (user.getRole() == User.Role.OWNER) {
            List<Flat> flats = flatRepository.findByOwnerUserId(user.getUserId());
            if (flats != null && !flats.isEmpty()) {
                flatNumber = flats.get(0).getFlatNumber();
            } else {
                List<Flat> flatsByPhone = flatRepository.findByOwnerPhoneNumber(user.getPhoneNumber());
                if (flatsByPhone != null && !flatsByPhone.isEmpty()) {
                    flatNumber = flatsByPhone.get(0).getFlatNumber();
                } else {
                    flatNumber = "4B"; // fallback
                }
            }
        }

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getUserId());
        userMap.put("username", user.getUsername());
        userMap.put("name", user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getUsername());
        userMap.put("phoneNumber", user.getPhoneNumber());
        userMap.put("email", user.getEmail() != null ? user.getEmail() : "");
        userMap.put("status", user.getStatus() != null ? user.getStatus() : "ACTIVE");
        userMap.put("role", role);
        userMap.put("flatNumber", flatNumber);

        LoginResponseData loginResponseData = LoginResponseData.builder()
                .role(role)
                .token(token)
                .user(userMap)
                .build();

        return new ApiGetResponseData<>("true", "Login successful", loginResponseData);
    }

    @Override
    public ApiGetResponseData RegisterAndUpdate(UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest.getUserId() != null) {
            Optional<User> existingUser = userRepository.findById(userRegisterRequest.getUserId());
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                user.setUsername(userRegisterRequest.getUsername());
                user.setPhoneNumber(userRegisterRequest.getPhoneNumber());
                user.setRole(User.Role.OWNER);
                if (!userRegisterRequest.getPassword().equals(userRegisterRequest.getConfirmPassword())) {
                    return new ApiGetResponseData<>("false", "Password and confirm password do not match", null);
                }

                if (userRegisterRequest.getPassword() != null && !userRegisterRequest.getPassword().isEmpty()) {
                    user.setPassword(passwordEncoder.encode(userRegisterRequest.getPassword()));
                }
                userRepository.save(user);
                return new ApiGetResponseData<>("true", "Update User successful", null);
            } else {
                return new ApiGetResponseData<>("false", "User not found", null);
            }
        }
        else {
            if (userRepository.findByPhoneNumber(userRegisterRequest.getPhoneNumber()).isPresent()) {
                return new ApiGetResponseData<>("false", "Phone number already exists", null);
            }
            User user = new User();
            user.setUsername(userRegisterRequest.getUsername());
            user.setPhoneNumber(userRegisterRequest.getPhoneNumber());
            user.setPassword(passwordEncoder.encode(userRegisterRequest.getPassword()));

            User.Role assignRole = userRepository.count() == 0
                    ? User.Role.ADMIN
                    : User.Role.OWNER;
            user.setRole(assignRole);
            userRepository.save(user);
            return new ApiGetResponseData<>("true", "Registration successful", null);
        }
    }

    @Override
    public ApiGetResponseData updateUserRole(Long userId, String newRole) {
        Optional<User> existingUser = userRepository.findById(userId);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            try {
                User.Role role = User.Role.valueOf(newRole.toUpperCase());
                user.setRole(role);
                userRepository.save(user);
                return new ApiGetResponseData<>("true", "User role updated successfully", null);
            } catch (IllegalArgumentException e) {
                return new ApiGetResponseData<>("false", "Invalid role specified", null);
            }
        } else {
            return new ApiGetResponseData<>("false", "User not found", null);
        }
    }

    @Override
    public ApiGetResponseData getAllUsers() {
        List<User> users = userRepository.findAll();
        List<Flat> flats = flatRepository.findAll();
        List<UserResponse> responseList = new ArrayList<>();
        for (User user : users) {
            UserResponse userResponse = new UserResponse();
            userResponse.setUserId(user.getUserId());
            userResponse.setUsername(user.getUsername());
            userResponse.setPhoneNumber(user.getPhoneNumber());
            userResponse.setRole(user.getRole().name());

            for (Flat flat : flats) {
                User safeOwner = FlatServiceImpl.getSafeOwner(flat);
                if (safeOwner != null && safeOwner.getUserId().equals(user.getUserId())) {
                    FlatResponse flatResponse = new FlatResponse();
                    flatResponse.setFlatId(flat.getFlatId());
                    flatResponse.setFlatNumber(flat.getFlatNumber());
                    flatResponse.setPhoneNumber(flat.getContact());
                    flatResponse.setResidents(flat.getResidents());
                    userResponse.getFlatsList().add(flatResponse);
                }
            }
            responseList.add(userResponse);
        }
        return new ApiGetResponseData<>("true", "Users retrieved successfully", responseList);
    }

    @Override
    public ApiGetResponseData Logout(HttpSession session) {
        return null;
    }



}

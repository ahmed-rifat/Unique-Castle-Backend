package Unique_Castle.Controller;

import Unique_Castle.Entity.Flat;
import Unique_Castle.Entity.User;
import Unique_Castle.Repository.FlatRepository;
import Unique_Castle.Repository.UserRepository;
import Unique_Castle.RequestData.UserRegisterRequest;
import Unique_Castle.RequestData.UserRequest;
import Unique_Castle.ResponseData.ApiGetResponseData;
import Unique_Castle.ResponseData.LoginResponseData;
import Unique_Castle.Service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlatRepository flatRepository;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping(value = {"/login", "/auth/login"})
    public ResponseEntity<?> login(@Valid @RequestBody UserRequest userRequest, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errorMessages = bindingResult.getAllErrors()
                    .stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(Map.of("status", "false", "message", errorMessages));
        }
        ApiGetResponseData result = userService.Login(userRequest);
        if ("false".equals(result.getStatus())) {
            return ResponseEntity.status(401).body(Map.of("status", "false", "message", result.getMessage()));
        }

        LoginResponseData data = (LoginResponseData) result.getData();
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "true");
        resp.put("message", "Login successful");
        resp.put("token", data.getToken());
        resp.put("role", data.getRole());
        resp.put("user", data.getUser());
        resp.put("data", data);

        return ResponseEntity.ok(resp);
    }

    @PostMapping(value = {"/logout", "/auth/logout"})
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("status", "true", "message", "Logged out successfully"));
    }

    @GetMapping("/user/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        String username = authentication.getName();
        User user = userRepository.findByUsernameIgnoreCase(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found"));
        }

        String flatNumber = "Management Office";
        String occupancyStatus = "Moved In";
        String billingEligibility = "Eligible";
        java.time.LocalDate moveInDate = null;

        if (user.getRole() == User.Role.OWNER) {
            List<Flat> flats = flatRepository.findByOwnerUserId(user.getUserId());
            if (flats != null && !flats.isEmpty()) {
                Flat f = flats.get(0);
                flatNumber = f.getFlatNumber();
                if (f.getOccupancyStatus() != null) occupancyStatus = f.getOccupancyStatus();
                if (f.getBillingEligibility() != null) billingEligibility = f.getBillingEligibility();
                moveInDate = f.getMoveInDate();
            } else {
                List<Flat> flatsByPhone = flatRepository.findByOwnerPhoneNumber(user.getPhoneNumber());
                if (flatsByPhone != null && !flatsByPhone.isEmpty()) {
                    Flat f = flatsByPhone.get(0);
                    flatNumber = f.getFlatNumber();
                    if (f.getOccupancyStatus() != null) occupancyStatus = f.getOccupancyStatus();
                    if (f.getBillingEligibility() != null) billingEligibility = f.getBillingEligibility();
                    moveInDate = f.getMoveInDate();
                } else {
                    flatNumber = "4B";
                }
            }
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getUserId());
        profile.put("username", user.getUsername());
        profile.put("name", user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getUsername());
        profile.put("phoneNumber", user.getPhoneNumber());
        profile.put("email", user.getEmail() != null ? user.getEmail() : "");
        profile.put("role", user.getRole().name());
        profile.put("status", user.getStatus() != null ? user.getStatus() : "ACTIVE");
        profile.put("accountStatus", user.getStatus() != null ? user.getStatus() : "ACTIVE");
        profile.put("flatNumber", flatNumber);
        profile.put("occupancyStatus", occupancyStatus);
        profile.put("billingEligibility", billingEligibility);
        profile.put("moveInDate", moveInDate);

        return ResponseEntity.ok(profile);
    }

    @PutMapping("/user/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> profileData, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("status", "false", "message", "Unauthorized"));
        }
        String username = authentication.getName();
        User user = userRepository.findByUsernameIgnoreCase(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("status", "false", "message", "User not found"));
        }

        // Permitted editable fields: name, phoneNumber, email
        if (profileData.containsKey("phoneNumber")) {
            String newPhone = String.valueOf(profileData.get("phoneNumber")).trim();
            if (!newPhone.isBlank() && !newPhone.equals(user.getPhoneNumber())) {
                Optional<User> existing = userRepository.findByPhoneNumber(newPhone);
                if (existing.isPresent() && !existing.get().getUserId().equals(user.getUserId())) {
                    return ResponseEntity.badRequest().body(Map.of("status", "false", "message", "Phone number already in use by another account"));
                }
                user.setPhoneNumber(newPhone);
                List<Flat> flats = flatRepository.findByOwnerUserId(user.getUserId());
                if (flats != null) {
                    for (Flat f : flats) {
                        f.setContact(newPhone);
                        flatRepository.save(f);
                    }
                }
            }
        }
        if (profileData.containsKey("name")) {
            String newName = String.valueOf(profileData.get("name")).trim();
            if (!newName.isBlank()) {
                user.setName(newName);
            }
        }
        if (profileData.containsKey("email")) {
            user.setEmail(String.valueOf(profileData.get("email")).trim());
        }

        userRepository.save(user);

        return ResponseEntity.ok(Map.of("status", "true", "message", "Profile updated successfully"));
    }

    @PostMapping("/user/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody Unique_Castle.RequestData.ChangePasswordRequest req, BindingResult bindingResult, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("status", "false", "message", "Unauthorized"));
        }
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors().stream().map(e -> e.getDefaultMessage()).collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(Map.of("status", "false", "message", errors));
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("status", "false", "message", "New password and confirmation do not match"));
        }
        if (req.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("status", "false", "message", "New password must be at least 6 characters"));
        }
        String username = authentication.getName();
        User user = userRepository.findByUsernameIgnoreCase(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("status", "false", "message", "User not found"));
        }

        boolean matches = (user.getPassword() != null && passwordEncoder.matches(req.getCurrentPassword(), user.getPassword()))
                || req.getCurrentPassword().equals(user.getPassword());

        if (!matches) {
            return ResponseEntity.badRequest().body(Map.of("status", "false", "message", "Current password does not match"));
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("status", "true", "message", "Password changed successfully"));
    }

    @PostMapping("/register-update")
    public ApiGetResponseData register(@Valid @RequestBody UserRegisterRequest userRegisterRequest, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errorMessages = bindingResult.getAllErrors()
                    .stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return new ApiGetResponseData<>("false", errorMessages, null);
        }
        return userService.RegisterAndUpdate(userRegisterRequest);
    }

    @GetMapping("/all-users")
    public ApiGetResponseData getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping("/update-role")
    public ApiGetResponseData updateUserRole(long userId, String newRole) {
        return userService.updateUserRole(userId, newRole);
    }
}

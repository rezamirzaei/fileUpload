package com.example.demo.controller;

import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {

        // Validate passwords match
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword",
                    "Passwords do not match");
        }

        // Check if username exists
        if (userService.existsByUsername(form.getUsername())) {
            bindingResult.rejectValue("username", "error.username",
                    "Username already taken");
        }

        // Check if email exists
        if (userService.existsByEmail(form.getEmail())) {
            bindingResult.rejectValue("email", "error.email",
                    "Email already registered");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            userService.registerUser(form.getUsername(), form.getEmail(), form.getPassword());
            redirectAttributes.addFlashAttribute("success",
                    "Account created successfully! Please login.");
            return "redirect:/login";
        } catch (Exception e) {
            log.error("Registration failed", e);
            bindingResult.reject("error.global", "Registration failed: " + e.getMessage());
            return "register";
        }
    }

    @Data
    public static class RegisterForm {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
        private String username;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
        private String password;

        @NotBlank(message = "Confirm password is required")
        private String confirmPassword;
    }
}

package com.relive.project.service;

import com.relive.project.entity.User;
import com.relive.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import com.relive.project.security.JwtUtil;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public String register(String name, String email, String password) {

        if (userRepository.findByEmail(email).isPresent()) {
            return "Email already exists";
        }

        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        return "User registered successfully";
    }

    public String login(String email, String password) {

        var userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return "User not found";
        }

        var user = userOptional.get();

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return "Invalid password";
        }

        return jwtUtil.generateToken(email);
    }
}

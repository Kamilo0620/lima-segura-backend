package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.auth.RegisterRequestDTO;
import com.limasegura.limasegurabackend.dto.user.UserResponse;
import com.limasegura.limasegurabackend.dto.user.UserUpdateRequest;
import com.limasegura.limasegurabackend.exception.DuplicateResourceException;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.Role;
import com.limasegura.limasegurabackend.model.User;
import com.limasegura.limasegurabackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Solo un ADMIN crea usuarios por aquí (puede elegir el rol). La contraseña se guarda cifrada. */
    public UserResponse create(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Ya existe un usuario con ese email");
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : Role.USER);
        user.setCreatedAt(LocalDateTime.now());
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse getById(Long id) {
        return UserResponse.from(findUser(id));
    }

    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    /** Actualiza nombre, correo y (opcional) contraseña. El rol no se toca. */
    public UserResponse update(Long id, UserUpdateRequest request) {
        User existing = findUser(id);
        if (!existing.getEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Ya existe un usuario con ese email");
        }
        existing.setName(request.getName());
        existing.setEmail(request.getEmail());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return UserResponse.from(userRepository.save(existing));
    }

    public void delete(Long id) {
        userRepository.delete(findUser(id));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
    }
}

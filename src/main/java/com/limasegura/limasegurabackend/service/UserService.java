package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.request.UserCreateRequest;
import com.limasegura.limasegurabackend.dto.request.UserUpdateRequest;
import com.limasegura.limasegurabackend.dto.response.UserDetailResponse;
import com.limasegura.limasegurabackend.dto.response.UserResponse;
import com.limasegura.limasegurabackend.exception.DuplicateResourceException;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.User;
import com.limasegura.limasegurabackend.repository.ConfirmationRepository;
import com.limasegura.limasegurabackend.repository.ReportRepository;
import com.limasegura.limasegurabackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final ConfirmationRepository confirmationRepository;
    private final ModelMapper modelMapper;

    public UserResponse create(UserCreateRequest request) {
        User user = modelMapper.map(request, User.class);
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException("Ya existe un usuario con ese email");
        }
        user.setCreatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);
        return modelMapper.map(savedUser, UserResponse.class);
    }

    public UserDetailResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        UserDetailResponse response = modelMapper.map(user, UserDetailResponse.class);
        response.setConfirmationsCount((int) confirmationRepository.countByUserId(id));
        response.setReportsCount(reportRepository.findByUserId(id).size());
        return response;
    }

    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(user -> modelMapper.map(user, UserResponse.class)).toList();
    }

    public UserResponse update(Long id, UserUpdateRequest request) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        modelMapper.map(request, existing);
        User updatedUser = userRepository.save(existing);
        return modelMapper.map(updatedUser, UserResponse.class);
    }

    public void delete(Long id) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        userRepository.delete(existing);
    }
}
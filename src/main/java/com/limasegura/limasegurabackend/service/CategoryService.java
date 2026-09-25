package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.request.CategoryCreateRequest;
import com.limasegura.limasegurabackend.dto.request.CategoryUpdateRequest;
import com.limasegura.limasegurabackend.dto.response.CategoryDetailResponse;
import com.limasegura.limasegurabackend.dto.response.CategoryResponse;
import com.limasegura.limasegurabackend.exception.DuplicateResourceException;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.Category;
import com.limasegura.limasegurabackend.repository.CategoryRepository;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import com.limasegura.limasegurabackend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ReportRepository reportRepository;
    private final IncidentRepository incidentRepository;
    private final ModelMapper modelMapper;

    public CategoryResponse create(CategoryCreateRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Ya existe una categoria con ese nombre");
        }
        Category category = modelMapper.map(request, Category.class);
        Category savedCategory = categoryRepository.save(category);
        return modelMapper.map(savedCategory, CategoryResponse.class);
    }

    public CategoryDetailResponse getById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id: " + id));
        CategoryDetailResponse response = modelMapper.map(category, CategoryDetailResponse.class);
        response.setReportsCount((int) reportRepository.countByCategoryId(id));
        response.setIncidentsCount((int) incidentRepository.countByCategoryId(id));
        return response;
    }

    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream()
                .map(category -> modelMapper.map(category, CategoryResponse.class)).toList();
    }

    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id: " + id));
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        Category updatedCategory = categoryRepository.save(existing);
        return modelMapper.map(updatedCategory, CategoryResponse.class);
    }

    public void delete(Long id) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id: " + id));
        categoryRepository.delete(existing);
    }
}
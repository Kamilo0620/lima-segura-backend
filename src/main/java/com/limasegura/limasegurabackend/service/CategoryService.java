package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.exception.DuplicateResourceException;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.Category;
import com.limasegura.limasegurabackend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public Category create(Category category) {
        if (categoryRepository.existsByName(category.getName())) {
            throw new DuplicateResourceException("Ya existe una categoria con ese nombre");
        }
        return categoryRepository.save(category);
    }

    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con id: " + id));
    }

    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    public Category update(Long id, Category category) {
        Category existing = getById(id);
        existing.setName(category.getName());
        existing.setDescription(category.getDescription());
        return categoryRepository.save(existing);
    }

    public void delete(Long id) {
        Category existing = getById(id);
        categoryRepository.delete(existing);
    }
}
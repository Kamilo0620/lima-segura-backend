package com.limasegura.limasegurabackend.service;


import com.limasegura.limasegurabackend.model.Zona;
import com.limasegura.limasegurabackend.repository.ZonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ZonaService {
    private ZonaRepository zonaRepository;
    public Zona crear(Zona zona) {
        return zonaRepository.save(zona);
    }

    public Zona obtenerPorId(Long id) {
        return zonaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Zona no encontrada con id: " + id));
    }

    public List<Zona> obtenerTodos() {
        return zonaRepository.findAll();
    }

    public List<Zona> obtenerPorDistrito(String distrito) {
        return zonaRepository.findByDistrito(distrito);
    }

    public Zona actualizar(Long id, Zona zona) {
        Zona existente = obtenerPorId(id);
        existente.setNombre(zona.getNombre());
        existente.setDistrito(zona.getDistrito());
        existente.setLatitud(zona.getLatitud());
        existente.setLongitud(zona.getLongitud());
        return zonaRepository.save(existente);
    }

    public void eliminar(Long id) {
        Zona existente = obtenerPorId(id);
        zonaRepository.delete(existente);
    }



}

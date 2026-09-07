package com.limasegura.limasegurabackend.service;


import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service@RequiredArgsConstructor
public class ReporteService {
    private static final int CONFIRMACIONES_PARA_VALIDAR = 3;

    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ZonaRepository zonaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ConfirmacionRepository confirmacionRepository;

    public Reporte crear(Long usuarioId, Long zonaId, Long categoriaId, String descripcion, Double latitud, Double longitud) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + usuarioId));
        Zona zona = zonaRepository.findById(zonaId)
                .orElseThrow(() -> new NoSuchElementException("Zona no encontrada con id: " + zonaId));
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new NoSuchElementException("Categoria no encontrada con id: " + categoriaId));

        Reporte reporte = new Reporte();
        reporte.setUsuario(usuario);
        reporte.setZona(zona);
        reporte.setCategoria(categoria);
        reporte.setDescripcion(descripcion);
        reporte.setLatitud(latitud);
        reporte.setLongitud(longitud);

        return reporteRepository.save(reporte);
    }

    public Reporte obtenerPorId(Long id) {
        return reporteRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Reporte no encontrado con id: " + id));
    }

    public List<Reporte> obtenerTodos() {
        return reporteRepository.findAll();
    }

    public List<Reporte> obtenerPorZona(Long zonaId) {
        return reporteRepository.findByZonaId(zonaId);
    }

    public List<Reporte> obtenerPorUsuario(Long usuarioId) {
        return reporteRepository.findByUsuarioId(usuarioId);
    }

    public void confirmar(Long reporteId, Long usuarioId) {
        Reporte reporte = obtenerPorId(reporteId);
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + usuarioId));

        boolean yaConfirmo = confirmacionRepository.findByUsuarioIdAndReporteId(usuarioId, reporteId).isPresent();
        if (yaConfirmo) {
            throw new IllegalStateException("Este usuario ya confirmo este reporte");
        }

        Confirmacion confirmacion = new Confirmacion();
        confirmacion.setUsuario(usuario);
        confirmacion.setReporte(reporte);
        confirmacionRepository.save(confirmacion);

        long totalConfirmaciones = confirmacionRepository.countByReporteId(reporteId);
        if (totalConfirmaciones >= CONFIRMACIONES_PARA_VALIDAR) {
            reporte.setEstado(EstadoReporte.VALIDADO);
            reporteRepository.save(reporte);
        }
    }

    public void eliminar(Long id) {
        Reporte existente = obtenerPorId(id);
        reporteRepository.delete(existente);
    }

}

package com.limasegura.limasegurabackend.service;


import com.limasegura.limasegurabackend.model.Confirmacion;
import com.limasegura.limasegurabackend.repository.ConfirmacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfirmacionService {

    private final ConfirmacionRepository confirmacionRepository;

    public List<Confirmacion> obtenerPorReporte(Long reporteId) {
        return confirmacionRepository.findByReporteId(reporteId);
    }

    public long contarPorReporte(Long reporteId) {
        return confirmacionRepository.countByReporteId(reporteId);
    }


}

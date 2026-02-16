package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.domain.PsyCenter;
import com.wuhao.aiemotion.service.PsyCenterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/psy-centers")
public class PsyCenterController {

    private final PsyCenterService psyCenterService;

    public PsyCenterController(PsyCenterService psyCenterService) {
        this.psyCenterService = psyCenterService;
    }

    @GetMapping
    public List<PsyCenter> query(@RequestParam(required = false) String cityCode,
                                 @RequestParam(name = "city_code", required = false) String cityCodeSnake,
                                 @RequestParam(required = false) Double latitude,
                                 @RequestParam(required = false) Double longitude,
                                 @RequestParam(defaultValue = "20") int limit,
                                 @RequestParam(defaultValue = "10") double radiusKm) {
        if (latitude != null && longitude != null) {
            return psyCenterService.nearby(latitude, longitude, radiusKm, limit);
        }
        String resolvedCityCode = (cityCode != null && !cityCode.isBlank()) ? cityCode : cityCodeSnake;
        if (resolvedCityCode == null || resolvedCityCode.isBlank()) {
            return List.of();
        }
        return psyCenterService.findByCity(resolvedCityCode, limit);
    }
}

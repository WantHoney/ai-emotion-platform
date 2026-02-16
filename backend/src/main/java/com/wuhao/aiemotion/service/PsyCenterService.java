package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.PsyCenter;
import com.wuhao.aiemotion.repository.PsyCenterRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PsyCenterService {

    private final PsyCenterRepository psyCenterRepository;

    public PsyCenterService(PsyCenterRepository psyCenterRepository) {
        this.psyCenterRepository = psyCenterRepository;
    }

    public List<PsyCenter> findByCity(String cityCode, int limit) {
        if (cityCode == null || cityCode.isBlank()) {
            throw new IllegalArgumentException("cityCode 不能为空");
        }
        return psyCenterRepository.findByCityCode(cityCode, Math.max(limit, 1));
    }

    public List<PsyCenter> nearby(double latitude, double longitude, double radiusKm, int limit) {
        if (radiusKm <= 0 || radiusKm > 100) {
            throw new IllegalArgumentException("radiusKm 取值范围应为 (0,100]");
        }
        return psyCenterRepository.findNearby(latitude, longitude, radiusKm, Math.max(limit, 1));
    }

    public List<PsyCenter> adminList() {
        return psyCenterRepository.findAllForAdmin();
    }

    public void adminCreate(String name, String cityCode, String cityName, String district, String address, String phone,
                            BigDecimal latitude, BigDecimal longitude, boolean recommended, boolean enabled) {
        psyCenterRepository.create(new PsyCenter(null, name, cityCode, cityName, district, address, phone,
                latitude, longitude, recommended, enabled, null, null));
    }

    public void adminUpdate(long id, String name, String cityCode, String cityName, String district, String address, String phone,
                            BigDecimal latitude, BigDecimal longitude, boolean recommended, boolean enabled) {
        int updated = psyCenterRepository.update(id, new PsyCenter(id, name, cityCode, cityName, district, address,
                phone, latitude, longitude, recommended, enabled, null, null));
        if (updated == 0) {
            throw new IllegalArgumentException("心理中心资源不存在: " + id);
        }
    }

    public void adminDelete(long id) {
        int deleted = psyCenterRepository.delete(id);
        if (deleted == 0) {
            throw new IllegalArgumentException("心理中心资源不存在: " + id);
        }
    }
}

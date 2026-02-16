package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.PsyCenter;
import com.wuhao.aiemotion.repository.PsyCenterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PsyCenterService {

    private static final Logger log = LoggerFactory.getLogger(PsyCenterService.class);

    private final PsyCenterRepository psyCenterRepository;

    public PsyCenterService(PsyCenterRepository psyCenterRepository) {
        this.psyCenterRepository = psyCenterRepository;
    }

    public List<PsyCenter> findByCity(String cityCode, int limit) {
        if (cityCode == null || cityCode.isBlank()) {
            throw new IllegalArgumentException("cityCode cannot be empty");
        }
        try {
            List<PsyCenter> rows = psyCenterRepository.findByCityCode(cityCode, Math.max(limit, 1));
            if (rows == null || rows.isEmpty()) {
                return placeholderByCity(cityCode);
            }
            return rows;
        } catch (Exception e) {
            log.warn("psy center query by city failed, fallback to placeholder list", e);
            return placeholderByCity(cityCode);
        }
    }

    public List<PsyCenter> nearby(double latitude, double longitude, double radiusKm, int limit) {
        if (radiusKm <= 0 || radiusKm > 100) {
            throw new IllegalArgumentException("radiusKm must be in (0,100]");
        }
        try {
            List<PsyCenter> rows = psyCenterRepository.findNearby(latitude, longitude, radiusKm, Math.max(limit, 1));
            if (rows == null || rows.isEmpty()) {
                return placeholderNearby(latitude, longitude);
            }
            return rows;
        } catch (Exception e) {
            log.warn("psy center nearby query failed, fallback to placeholder list", e);
            return placeholderNearby(latitude, longitude);
        }
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
            throw new IllegalArgumentException("psy center not found: " + id);
        }
    }

    public void adminDelete(long id) {
        int deleted = psyCenterRepository.delete(id);
        if (deleted == 0) {
            throw new IllegalArgumentException("psy center not found: " + id);
        }
    }

    private List<PsyCenter> placeholderByCity(String cityCode) {
        return List.of(new PsyCenter(
                0L,
                "心理支持中心（示例）",
                cityCode,
                "示例城市",
                "示例区域",
                "示例地址 1 号",
                "400-000-0000",
                new BigDecimal("31.2304"),
                new BigDecimal("121.4737"),
                true,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
    }

    private List<PsyCenter> placeholderNearby(double latitude, double longitude) {
        return List.of(new PsyCenter(
                0L,
                "附近心理支持中心（示例）",
                "000000",
                "示例城市",
                "示例区域",
                "示例地址 1 号",
                "400-000-0000",
                BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude),
                true,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
    }
}

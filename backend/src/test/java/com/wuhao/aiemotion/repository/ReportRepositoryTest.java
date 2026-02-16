package com.wuhao.aiemotion.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReportRepositoryTest {

    @Test
    void normalizeFilterValueShouldTreatAllLikeValuesAsNoFilter() {
        assertNull(ReportRepository.normalizeFilterValue(null));
        assertNull(ReportRepository.normalizeFilterValue(""));
        assertNull(ReportRepository.normalizeFilterValue("   "));
        assertNull(ReportRepository.normalizeFilterValue("ALL"));
        assertNull(ReportRepository.normalizeFilterValue("all"));
        assertNull(ReportRepository.normalizeFilterValue("Any"));
        assertNull(ReportRepository.normalizeFilterValue("*"));
        assertNull(ReportRepository.normalizeFilterValue("全部"));
    }

    @Test
    void normalizeFilterValueShouldKeepNormalValues() {
        assertEquals("HIGH", ReportRepository.normalizeFilterValue("HIGH"));
        assertEquals("sad", ReportRepository.normalizeFilterValue(" sad "));
    }
}

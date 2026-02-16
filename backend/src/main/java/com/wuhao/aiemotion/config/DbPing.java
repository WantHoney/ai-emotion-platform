package com.wuhao.aiemotion.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DbPing {

    private final JdbcTemplate jdbcTemplate;

    public DbPing(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ping() {
        Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
        System.out.println("DB ping => " + one);
    }
}

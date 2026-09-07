package com.lichun.agsell;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.lichun.agsell.mapper")
public class AgsellApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgsellApplication.class, args);
    }

}

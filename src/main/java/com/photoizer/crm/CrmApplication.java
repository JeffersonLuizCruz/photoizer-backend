package com.photoizer.crm;

import com.photoizer.crm.shared.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Modulith
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
public class CrmApplication {

	public static void main(String[] args) {
		SpringApplication.run(CrmApplication.class, args);
	}

}

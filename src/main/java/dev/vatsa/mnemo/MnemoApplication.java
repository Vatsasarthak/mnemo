package dev.vatsa.mnemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MnemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MnemoApplication.class, args);
	}

}

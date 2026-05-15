package com.treasurehunter.socket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.TreasureHunter.CommonLib.service")
@SpringBootApplication
public class SocketApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocketApplication.class, args);
	}

}

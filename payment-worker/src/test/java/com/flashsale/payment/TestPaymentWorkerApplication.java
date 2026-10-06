package com.flashsale.payment;

import org.springframework.boot.SpringApplication;

public class TestPaymentWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaymentWorkerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

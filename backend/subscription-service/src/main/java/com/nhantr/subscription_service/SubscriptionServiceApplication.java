package com.nhantr.subscription_service;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.nhantr.subscription_service", "com.nhantr.common"})
public class SubscriptionServiceApplication {
    public static void main(String[] args) { SpringApplication.run(SubscriptionServiceApplication.class,args); }
}

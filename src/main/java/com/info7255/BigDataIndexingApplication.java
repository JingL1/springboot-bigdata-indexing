package com.info7255;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BigDataIndexingApplication {

    public static void main(String[] args) {

        SpringApplication.run(BigDataIndexingApplication.class, args);
    }

//    @Bean
//    public Filter filter(){
//        ShallowEtagHeaderFilter filter=new ShallowEtagHeaderFilter();
//        return filter;
//    }


}

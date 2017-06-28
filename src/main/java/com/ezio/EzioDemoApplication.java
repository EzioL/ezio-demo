package com.ezio;

import com.ezio.processor.NetEaseMusicPageProcessor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import us.codecraft.webmagic.Spider;

import static com.ezio.processor.NetEaseMusicPageProcessor.START_URL;

@RestController
@SpringBootApplication
public class EzioDemoApplication {
	@Autowired
	NetEaseMusicPageProcessor mProcessor;

	@GetMapping("/")
	public String index() {
		mProcessor.start();
		return "ing";
	}

	public static void main(String[] args) {
		SpringApplication.run(EzioDemoApplication.class, args);
	}


}

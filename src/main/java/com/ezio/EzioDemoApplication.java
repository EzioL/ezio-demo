package com.ezio;

import com.ezio.pipeline.NetEaseMusicPipeline;
import com.ezio.processor.NetEaseMusicPageProcessor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class EzioDemoApplication {
	@Autowired
	NetEaseMusicPageProcessor mProcessor;
	@Autowired
	NetEaseMusicPipeline mPipeline;

	@GetMapping("/")
	public String index() {
		mProcessor.start(mProcessor, mPipeline);
		return "ing";
	}

	public static void main(String[] args) {
		SpringApplication.run(EzioDemoApplication.class, args);

	}

}

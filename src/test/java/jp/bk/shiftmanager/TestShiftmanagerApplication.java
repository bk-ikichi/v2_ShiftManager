package jp.bk.shiftmanager;

import org.springframework.boot.SpringApplication;

public class TestShiftmanagerApplication {

	public static void main(String[] args) {
		SpringApplication.from(ShiftmanagerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

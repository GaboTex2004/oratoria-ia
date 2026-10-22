package com.oratoria.backend;

import io.github.cdimascio.dotenv.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {


		// Carga el archivo .env desde la raíz del proyecto backend
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()
                .load();

		// Pasa cada variable del .env a las propiedades del sistema
        dotenv.entries().forEach(entry -> 
            System.setProperty(entry.getKey(), entry.getValue())
        );
		SpringApplication.run(BackendApplication.class, args);
	}

}

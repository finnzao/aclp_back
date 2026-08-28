package br.jus.tjba.aclp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * application.properties aponta para um PostgreSQL local. Sem sobrescrever aqui,
 * este teste só passa em máquina com o banco de pé — e falha em CI.
 * driver-class-name precisa vir junto da URL: só trocar a URL para H2 deixa o
 * driver do PostgreSQL configurado e o Hibernate não resolve o dialect.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ctxtestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "aclp.email.enabled=false"
})
class AclpApplicationTests {

	@Test
	void contextLoads() {
	}

}

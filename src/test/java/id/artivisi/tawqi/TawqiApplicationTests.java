package id.artivisi.tawqi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TawqiTestConfig.class)
@ActiveProfiles("test")
class TawqiApplicationTests {

    @Test
    void contextLoads() {
    }
}

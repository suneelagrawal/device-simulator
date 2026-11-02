
package device.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:firmwaretest;DB_CLOSE_DELAY=-1"
})
class DeviceSimulatorApplicationTests {

    @Test
    void contextLoads() {
    }
}

package test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xq.jvmtestkit.junit.Xq;
import com.xq.jvmtestkit.junit.XqTest;
import com.xq.jvmtestkit.rest.RestRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@XqTest
@Tag("e2e")
class RoutineBusinessE2ETest {
  @Test
  void createsAndReadsRoutine() {
    var name = "Routine E2E " + UUID.randomUUID();
    var created =
        Xq.rest()
            .post("/api/v1/routines", RestRequest.builder().jsonBody(Map.of("name", name)).build());
    created.should().hasStatus(201);
    var id = created.bodyUtf8().replaceFirst(".*\"id\":\"([^\"]+)\".*", "$1");
    var read = Xq.rest().get("/api/v1/routines/" + id);
    read.should().hasStatus(200);
    assertTrue(read.bodyUtf8().contains(name));
  }
}

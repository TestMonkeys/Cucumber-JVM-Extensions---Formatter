import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;

public class Hooks {

    @Before
    public void before(Scenario scenario) {
        scenario.write("Before started");
        scenario.write("something happened");
        scenario.write("Before finished");
    }

    @After
    public void after(Scenario scenario) {
        scenario.write("After started");
        scenario.write("something happened");
        scenario.write("After finished");
    }
}

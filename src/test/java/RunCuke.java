import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
        features = {"src/test/resources"},
        plugin = {"org.testmonkeys.cucumber2.ext.formatters.PerFeatureFormatter:target/json-report"})
public class RunCuke {
}

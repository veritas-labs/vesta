package uk.veritaslabs.vesta.devicepairing

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Runs shared/src/jvmTest/resources/features/device-pairing.feature.
 *
 * Deliberately a JUnit Platform Suite (@Suite + @IncludeEngines("cucumber")),
 * not bare `cucumber.glue`/`cucumber.plugin` auto-discovery via
 * junit-platform.properties alone: the Kotlin Multiplatform Gradle plugin's
 * jvmTest task only surfaces class-shaped tests it recognises from its own
 * discovery pass (kotlin-test-annotated classes, in this project's case) —
 * Cucumber's dynamically-generated Feature/Scenario nodes never appeared in
 * `./gradlew :shared:jvmTest` output even with cucumber-junit-platform-engine
 * on the classpath and useJUnitPlatform() set. Wrapping Cucumber in a Suite
 * gives KGP one ordinary test class to discover; the Suite Engine then runs
 * Cucumber underneath it, unaffected by that limitation.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/device-pairing.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "uk.veritaslabs.vesta.devicepairing")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
class RunDevicePairingCucumberTest

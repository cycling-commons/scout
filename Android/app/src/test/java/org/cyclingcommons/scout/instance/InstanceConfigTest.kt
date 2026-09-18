package org.cyclingcommons.scout.instance

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class InstanceConfigTest {
    @Test
    fun parsesBundledShape() {
        val config = InstanceConfigLoader.parse(
            JSONObject(
                """
                {
                  "instance_url": "https://example.com",
                  "instance_name": "Your Atlas"
                }
                """.trimIndent(),
            ),
        )
        assertEquals("https://example.com", config.baseUrl)
        assertEquals("https://example.com/scout/review", config.reviewUrl)
        assertEquals("Your Atlas", config.instanceName)
    }

    @Test
    fun trimsTrailingSlashFromBaseUrl() {
        val config = InstanceConfigLoader.parse(
            JSONObject("""{"instance_url":"https://example.com/","instance_name":"x"}"""),
        )
        assertEquals("https://example.com", config.baseUrl)
    }

    @Test
    fun exampleEnvDefinesRequiredKeys() {
        val env = Files.readString(Paths.get(".env.example"))
        assert(env.contains("SCOUT_INSTANCE_URL="))
        assert(env.contains("SCOUT_INSTANCE_NAME="))
    }
}

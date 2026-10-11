package de.familyplanner.api

import de.familyplanner.api.model.Member
import de.familyplanner.api.model.Role
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratedModelSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `generated member model round trips with enum role`() {
        // Arrange
        val member = Member(id = "id-1", name = "Lea", role = Role.CHILD, createdAt = "2026-10-12T10:00:00Z")

        // Act
        val encoded = json.encodeToString(member)
        val decoded = json.decodeFromString<Member>(encoded)

        // Assert
        assertEquals(member, decoded)
        assertEquals("""{"id":"id-1","name":"Lea","role":"CHILD","createdAt":"2026-10-12T10:00:00Z"}""", encoded)
    }
}

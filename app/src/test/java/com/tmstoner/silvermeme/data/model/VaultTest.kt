package com.tmstoner.silvermeme.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultTest {

    // ── Migration from single-vault settings ──────────────────────────────────

    @Test
    fun `no saved list turns the legacy settings into the Default vault`() {
        val vaults = VaultRegistry.resolve(
            serialized      = null,
            legacyRemoteUrl = "https://example.com/v.git",
            legacyUsername  = "me",
            legacyPath      = "/data/vault"
        )
        assertEquals(
            listOf(VaultConfig(VaultRegistry.DEFAULT_ID, "Default", "/data/vault", "https://example.com/v.git", "me")),
            vaults
        )
    }

    @Test
    fun `fresh install gets one local Default vault with a blank path`() {
        val vaults = VaultRegistry.resolve("", "", "", "")
        assertEquals(1, vaults.size)
        assertEquals(VaultRegistry.DEFAULT_ID, vaults.single().id)
        assertEquals("", vaults.single().path)
    }

    @Test
    fun `saved list wins over legacy settings`() {
        val saved = VaultCodec.encode(listOf(VaultConfig("a1", "Work", "/w")))
        val vaults = VaultRegistry.resolve(saved, "https://old", "old", "/old")
        assertEquals(listOf(VaultConfig("a1", "Work", "/w")), vaults)
    }

    @Test
    fun `default vault keeps the pre-G2 token key`() {
        assertEquals("git_token", VaultRegistry.tokenKey(VaultRegistry.DEFAULT_ID))
        assertEquals("git_token_a1", VaultRegistry.tokenKey("a1"))
    }

    // ── Active vault and edits ────────────────────────────────────────────────

    private val two = listOf(VaultConfig("default", "Default"), VaultConfig("a1", "Work"))

    @Test
    fun `activeOf falls back to the first vault for unknown or missing ids`() {
        assertEquals("a1", VaultRegistry.activeOf(two, "a1").id)
        assertEquals("default", VaultRegistry.activeOf(two, "gone").id)
        assertEquals("default", VaultRegistry.activeOf(two, null).id)
    }

    @Test
    fun `update changes only the given vault`() {
        val updated = VaultRegistry.update(two, "a1") { it.copy(remoteUrl = "https://w") }
        assertEquals("", updated[0].remoteUrl)
        assertEquals("https://w", updated[1].remoteUrl)
    }

    @Test
    fun `the last vault cannot be removed`() {
        assertNull(VaultRegistry.remove(listOf(VaultConfig("default", "Default")), "default"))
        assertNull(VaultRegistry.remove(two, "missing"))
        assertEquals(listOf("default"), VaultRegistry.remove(two, "a1")!!.map { it.id })
    }

    @Test
    fun `nextName skips names already used`() {
        assertEquals("Vault 2", VaultRegistry.nextName(two))
        assertEquals("Vault 3", VaultRegistry.nextName(two + VaultConfig("b", "Vault 2")))
    }

    // ── Codec ─────────────────────────────────────────────────────────────────

    @Test
    fun `codec round-trips awkward characters`() {
        val vaults = listOf(
            VaultConfig("a1", "Tabs\tand\nnewlines", "C:\\vaults\\one", "https://h/x.git?a=b", "user\\name"),
            VaultConfig("b2", "", "", "", "")
        )
        assertEquals(vaults, VaultCodec.decode(VaultCodec.encode(vaults)))
    }

    @Test
    fun `codec skips malformed lines and duplicate ids`() {
        val text = "a1\tWork\t/w\t\t\n" +   // ok
            "broken line\n" +              // wrong field count
            "\tNo id\t\t\t\n" +            // blank id
            "a1\tDuplicate\t\t\t"          // duplicate id
        val decoded = VaultCodec.decode(text)
        assertEquals(listOf(VaultConfig("a1", "Work", "/w")), decoded)
        assertTrue(VaultCodec.decode("").isEmpty())
    }
}

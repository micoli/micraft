package org.micoli.micraft.game.world.claim

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.auth.Permission
import org.micoli.micraft.game.FactionsSection
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.social.FactionManager
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.actionblock.ActionBlockPermissions
import org.micoli.micraft.game.world.actionblock.canEditActionBlock
import org.micoli.micraft.social.FactionDefinition
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class AuthorizerTest {
    private val registry = ClaimRegistry(null)
    private val authorizer = Authorizer(registry)
    private val inside = BlockPos(5, 5, 5)
    private val outside = BlockPos(500, 5, 500)

    private val claim = registry.create(setOf(ChunkPos(0, 0)), 0, 10, "owner-id", "Owner")

    private fun character(id: String, vararg permissions: String): PlayerSession =
        testSession(id = id, name = id).also {
            it.permissions = permissions.map(::Permission).toSet()
        }

    @Test
    fun buildInClaim_stranger_isRefused() {
        assertFalse(authorizer.canBuildAt(character("stranger"), inside))
    }

    @Test
    fun buildInClaim_unclaimedLandIsOpen() {
        assertTrue(authorizer.canBuildAt(character("stranger"), outside))
    }

    @Test
    fun buildInClaim_ownerAndTrusted_areAllowed() {
        registry.setTrusted(claim.id, "friend", "friend", true)
        assertTrue(authorizer.canBuildAt(character("owner-id"), inside))
        assertTrue(authorizer.canBuildAt(character("friend"), inside))
    }

    @Test
    fun buildInClaim_buildPermissionWithoutWildcard_isAllowed() {
        assertTrue(authorizer.canBuildAt(character("mod", ClaimPermissions.BUILD.id), inside))
    }

    @Test
    fun buildInClaim_administerPermission_doesNotBuild() {
        assertFalse(authorizer.canBuildAt(character("mod", ClaimPermissions.ADMINISTER.id), inside))
    }

    @Test
    fun buildInClaim_wildcard_isAllowed() {
        assertTrue(authorizer.canBuildAt(character("admin", Permission.WILDCARD.id), inside))
    }

    @Test
    fun administer_ownerAdministerPermissionAndWildcard_areAllowed() {
        assertTrue(authorizer.canAdminister(character("owner-id"), claim))
        assertTrue(
            authorizer.canAdminister(character("mod", ClaimPermissions.ADMINISTER.id), claim))
        assertTrue(authorizer.canAdminister(character("admin", Permission.WILDCARD.id), claim))
    }

    @Test
    fun administer_buildPermissionTrustedAndStranger_areRefused() {
        registry.setTrusted(claim.id, "friend", "friend", true)
        assertFalse(authorizer.canAdminister(character("mod", ClaimPermissions.BUILD.id), claim))
        assertFalse(authorizer.canAdminister(character("friend"), claim))
        assertFalse(authorizer.canAdminister(character("stranger"), claim))
    }

    @Test
    fun factionAlly_canBuildButNotAdminister() {
        val sessions =
            listOf(
                testSession(id = "owner-id", name = "Owner").also {
                    it.state = it.state.copy(factionId = "red")
                },
                testSession(id = "ally", name = "ally").also {
                    it.state = it.state.copy(factionId = "red")
                })
        val channels = ChatChannelManager()
        val factions =
            FactionManager(
                { sessions }, {}, ChatService(channels, {}, { sessions }), channels, testI18n())
        factions.applyConfig(
            FactionsSection(enabled = true, list = listOf(FactionDefinition("red", "Red"))))
        registry.bindFactions(factions)

        val ally = sessions.last()
        assertTrue(authorizer.canBuildAt(ally, inside))
        assertFalse(authorizer.canAdminister(ally, claim))
    }

    @Test
    fun isAllowed_namedPermissionThenOwnerThenClaim() {
        val permission = Permission("thing:edit")
        val stranger = character("stranger")

        assertFalse(authorizer.isAllowed(stranger, permission, EditTarget.ByOwner("Owner")))
        assertTrue(
            authorizer.isAllowed(
                character("x", permission.id), permission, EditTarget.ByOwner("Owner")))
        assertTrue(
            authorizer.isAllowed(
                testSession(id = "o", name = "Owner"), permission, EditTarget.ByOwner("Owner")))
        assertFalse(authorizer.isAllowed(stranger, permission, EditTarget.InWorld(inside)))
        assertTrue(
            authorizer.isAllowed(
                character("mod", ClaimPermissions.BUILD.id),
                permission,
                EditTarget.InWorld(inside)))
        assertFalse(
            authorizer.isAllowed(stranger, permission, EditTarget.OwnedInWorld("Owner", inside)))
        assertTrue(
            authorizer.isAllowed(
                testSession(id = "o", name = "Owner"),
                permission,
                EditTarget.OwnedInWorld("Owner", outside)))
    }

    @Test
    fun actionBlockEdit_refusedInClaimUnlessOwnerPermissionOrBuildRights() {
        assertFalse(authorizer.canEditActionBlock(character("stranger"), inside, "Owner"))
        assertTrue(
            authorizer.canEditActionBlock(
                character("editor", ActionBlockPermissions.EDIT.id), inside, "Owner"))
        assertTrue(
            authorizer.canEditActionBlock(
                character("mod", ClaimPermissions.BUILD.id), inside, "Owner"))
        assertTrue(authorizer.canEditActionBlock(testSession(name = "Owner"), inside, "Owner"))
    }
}

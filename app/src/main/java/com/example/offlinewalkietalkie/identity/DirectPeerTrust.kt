package com.example.offlinewalkietalkie.identity

import com.example.offlinewalkietalkie.data.TrustedContact
import com.example.offlinewalkietalkie.transport.ConnectionRequest
import java.util.Locale

/** Discovery names are only a conservative change alarm; only a verified key grants trust. */
object DirectPeerTrust {
    enum class Decision { TRUSTED, IDENTITY_CHANGED, REQUIRE_SAS }

    fun evaluate(request: ConnectionRequest, contacts: List<TrustedContact>): Decision {
        val identity = request.identityKeyHex
        val verified = contacts.filter { it.isVerified }
        if (identity != null && identity.matches(Regex("[0-9a-f]{64}")) &&
            verified.any { it.publicKeyHex == identity }) return Decision.TRUSTED
        val name = request.peerName.trim().lowercase(Locale.ROOT)
        if (name.isNotEmpty() && verified.any {
                it.displayName.trim().lowercase(Locale.ROOT) == name && it.publicKeyHex != identity
            }) return Decision.IDENTITY_CHANGED
        return Decision.REQUIRE_SAS
    }
}

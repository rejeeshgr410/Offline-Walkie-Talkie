package com.example.offlinewalkietalkie.transport

import org.junit.Assert.*
import org.junit.Test

class TransportSelectorTest {

    @Test
    fun testAllTransportTypesHaveDescriptiveNames() {
        for (type in TransportType.values()) {
            val name = TransportSelector.getDisplayName(type)
            assertNotNull(name)
            assertTrue("Display name must not be blank for $type", name.isNotBlank())
            // Section 0 Rule: Never claim fixed radio distance
            assertFalse(name.contains("2 km"))
            assertFalse(name.contains("kilometer"))
        }
    }

    @Test
    fun testTransportEnumCompleteness() {
        val types = TransportType.values().toList()
        assertTrue("Must include NEARBY_CONNECTIONS", types.contains(TransportType.NEARBY_CONNECTIONS))
        assertTrue("Must include BLE_L2CAP", types.contains(TransportType.BLE_L2CAP))
        assertTrue("Must include WIFI_AWARE", types.contains(TransportType.WIFI_AWARE))
        assertTrue("Must include WIFI_DIRECT", types.contains(TransportType.WIFI_DIRECT))
        assertTrue("Must include BLUETOOTH_CLASSIC", types.contains(TransportType.BLUETOOTH_CLASSIC))
    }
}

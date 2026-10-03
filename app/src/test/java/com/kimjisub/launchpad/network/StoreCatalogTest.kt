package com.kimjisub.launchpad.network

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import com.kimjisub.launchpad.network.fb.StoreVO
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/** Preserve the original Firebase keys, events and lifecycle while allowing an offline UI feed. */
class StoreCatalogTest {
    @Test
    fun forwardsAddedChangedAndCountAndDetachesBothSubscriptions() {
        val store = mockk<FirebaseManager>(relaxed = true)
        val count = mockk<FirebaseManager>(relaxed = true)
        val child = slot<ChildEventListener>()
        val value = slot<ValueEventListener>()
        every { store.setEventListener(capture(child)) } returns store
        every { count.setEventListener(capture(value)) } returns count
        val added = mutableListOf<Pair<StoreVO, String>>()
        val changed = mutableListOf<StoreVO>()
        val counts = mutableListOf<Long>()
        val catalog = FirebaseStoreCatalog({ store }, { count })
        catalog.attach(object : StoreCatalog.Listener {
            override fun onAdded(pack: StoreVO, key: String) { added += pack to key }
            override fun onChanged(pack: StoreVO) { changed += pack }
            override fun onCount(count: Long) { counts += count }
        })

        fun snapshot(pack: StoreVO?, key: String?) = mockk<DataSnapshot>().also { node ->
            every { node.getValue(StoreVO::class.java) } returns pack
            every { node.key } returns key
        }
        val pack = StoreVO(code = "code", title = "first")
        child.captured.onChildAdded(snapshot(pack, "firebase-child-key"), null)
        child.captured.onChildAdded(snapshot(pack, null), null)
        child.captured.onChildAdded(snapshot(null, "missing"), null)
        child.captured.onChildAdded(snapshot(StoreVO(), null), null)
        val update = pack.copy(title = "changed")
        child.captured.onChildChanged(snapshot(update, "firebase-child-key"), null)
        val countSnapshot = mockk<DataSnapshot> { every { getValue(Long::class.java) } returns 12L }
        value.captured.onDataChange(countSnapshot)

        assertEquals(listOf(pack to "firebase-child-key", pack to "code"), added)
        assertEquals(listOf(update), changed)
        assertEquals(listOf(12L), counts)
        verify(exactly = 1) { store.attachEventListener(true); count.attachEventListener(true) }
        catalog.detach()
        verify(exactly = 1) { store.attachEventListener(false); count.attachEventListener(false) }
    }
}

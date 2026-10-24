package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.basefeatures.FeatureScreen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PackMetadataCompatibilityDeviceTest {
	private val label get() = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "after"

	@Test fun missingDimensions_refusesInsteadOfOpeningAnEmptyGrid() = checkRefusal(
		"missing-dimensions", "title=Metadata check\nproducerName=UniPad test\nchain=2\n",
	)

	@Test fun lastInvalidChain_refusesInsteadOfKeepingTheEarlierValue() = checkRefusal(
		"last-invalid-chain", "title=Metadata check\nproducerName=UniPad test\nbuttonX=8\nbuttonY=8\nchain=2\nchain=abc\n",
	)

	private fun checkRefusal(name: String, info: String) {
		val screen = FeatureScreen()
		val root = TestUniPack.install(screen.context)
		try {
			File(root, "info").writeText(info)
			screen.launch(PlayActivity::class.java) { putExtra("path", root.path) }
			screen.await("Pack metadata did not finish loading") { screen.onMain { !screen.vm().unipackLoading } }
			val refused = screen.onMain { screen.vm().unipack.criticalError }
			if (refused) screen.node(By.text(screen.text(R.string.quit)))
			else if (screen.onMain { screen.vm().unipack.errorDetail != null }) screen.node(By.text(screen.text(R.string.accept)))
			else screen.node(By.desc(screen.text(R.string.menu)))
			screen.capture("pack-metadata-$label-$name")
			assertTrue("Invalid metadata must refuse the pack", refused)
		} finally {
			screen.close()
			root.deleteRecursively()
		}
	}

	@Test fun emptyWebsite_doesNotOfferAnInactiveButton() {
		val screen = FeatureScreen()
		try {
			screen.assertDedicatedWorkspace()
			screen.cleanPacks()
			val root = screen.install()
			File(root, "info").appendText("website=\n")
			screen.launch(MainActivity::class.java)
			screen.click(By.text(FeatureScreen.TITLE))
			screen.node(By.desc(screen.text(R.string.cd_delete)))
			screen.capture("pack-metadata-$label-empty-website")
			assertFalse("An empty website must not offer a link", screen.has(By.desc(screen.text(R.string.cd_website))))
		} finally {
			screen.close()
		}
	}
}

package com.kimjisub.launchpad.basefeatures

import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Release preflight subset. No production network, skipped tests, or legacy helper edits. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    LibraryAndFileTest::class,
    ShareImportTest::class,
    TouchPlaybackTest::class,
    MultiTouchPadModeTest::class,
    MultiTouchSlideModeTest::class,
    MouseInputTest::class,
    TransportAndLifecycleTest::class,
    VirtualMidiTest::class,
    StoreDownloadTest::class,
    AppearanceAndInsetsTest::class,
)
class BaseFeaturesSuite

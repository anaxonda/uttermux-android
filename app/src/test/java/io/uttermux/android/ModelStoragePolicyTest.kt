package io.uttermux.android

import io.uttermux.android.provider.ModelManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelStoragePolicyTest {
    @Test fun installHeadroomIncludesArchiveExtractionAndSystemReserve(){assertEquals(2074,ModelManager.storageRequirementMb(350));assertEquals(3553,ModelManager.storageRequirementMb(843))}
}

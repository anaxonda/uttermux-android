package io.uttermux.android.config

import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareAdvisorTest {
    private val phone=DeviceHardware("arm64-v8a",8,4096,1800)

    @Test fun cloudDoesNotClaimToUseLocalGpu(){
        assertEquals("Cloud",HardwareAdvisor.recommend(phone,4000,"heavy",true).label)
    }

    @Test fun rejectsModelThatDominatesTotalMemory(){
        assertEquals("Not recommended",HardwareAdvisor.recommend(phone,3000,"balanced",false).label)
    }

    @Test fun warnsWhenAvailableMemoryIsTight(){
        assertEquals("Memory pressure",HardwareAdvisor.recommend(phone,1500,"fast",false).label)
    }

    @Test fun recommendsSmallCpuModel(){
        assertEquals("Recommended",HardwareAdvisor.recommend(phone,400,"fast",false).label)
    }
}

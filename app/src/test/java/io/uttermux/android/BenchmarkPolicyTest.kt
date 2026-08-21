package io.uttermux.android

import io.uttermux.android.benchmark.BenchmarkPolicy
import io.uttermux.android.benchmark.CandidateSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkPolicyTest {
    @Test fun candidatesNeverExceedAvailableCores(){assertEquals(listOf(1),BenchmarkPolicy.threadCandidates(1));assertEquals(listOf(1,2,3,4,6,8),BenchmarkPolicy.threadCandidates(12))}
    @Test fun smallestCandidateWithinFivePercentWins(){val winner=BenchmarkPolicy.winner(listOf(CandidateSummary(2,.51,500.0,200,0,true),CandidateSummary(4,.50,450.0,230,0,true),CandidateSummary(1,.70,700.0,180,0,true)));assertEquals(2,winner.threads)}
    @Test fun unstableAndUnderrunningCandidatesAreClassified(){assertEquals("too-slow",BenchmarkPolicy.classification(CandidateSummary(4,1.1,500.0,200,0,true)));assertEquals("marginal",BenchmarkPolicy.classification(CandidateSummary(2,.5,500.0,200,1,true)))}
}

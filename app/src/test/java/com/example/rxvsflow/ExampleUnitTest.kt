package com.example.rxvsflow

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    @Test
    fun test() = runBlocking {
        val flow = flow { emit(1) }
        val value = flow.toList().first()
        assert(value == 1)
    }
}
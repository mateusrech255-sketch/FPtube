package com.example.fptube

import org.junit.Assert.assertTrue
import org.junit.Test

class SaveLogoRobolectricTest {
    @Test
    fun logoResourcesExist() {
        assertTrue(br.com.fpagency.fptube.R.mipmap.ic_launcher != 0)
    }
}

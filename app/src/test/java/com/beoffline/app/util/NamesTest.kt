package com.beoffline.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {

    @Test
    fun firstName_takesTheFirstWord() {
        assertEquals("Priya", "Priya Sharma".firstName())
        assertEquals("Sam", "Sam Okonkwo".firstName())
        assertEquals("Aarav", "Aarav".firstName())
    }

    @Test
    fun firstName_toleratesMessyInput() {
        assertEquals("Priya", "  Priya   Sharma  ".firstName())
        assertEquals("Your partner", (null as String?).firstName())
        assertEquals("Your partner", "".firstName())
        assertEquals("Your partner", "   ".firstName())
        assertEquals("Someone", "".firstName(fallback = "Someone"))
    }

    @Test
    fun disambiguation_keepsAnInitial_onlyWhenTwoPeopleShareAFirstName() {
        val names = listOf("Priya Sharma", "Priya Rao", "Sam Okonkwo")
        val short = disambiguatedFirstNames(names)

        assertEquals("Priya S.", short["Priya Sharma"])
        assertEquals("Priya R.", short["Priya Rao"])
        // Sam is unique — no reason to make anyone read a surname.
        assertEquals("Sam", short["Sam Okonkwo"])
    }

    @Test
    fun disambiguation_fallsBackToTheFirstName_whenThereIsNoSurnameToUse() {
        val short = disambiguatedFirstNames(listOf("Priya", "Priya Rao"))

        assertEquals("Priya", short["Priya"])
        assertEquals("Priya R.", short["Priya Rao"])
    }

    @Test
    fun disambiguation_handlesTheEmptyCase() {
        assertEquals(emptyMap<String, String>(), disambiguatedFirstNames(emptyList()))
    }
}

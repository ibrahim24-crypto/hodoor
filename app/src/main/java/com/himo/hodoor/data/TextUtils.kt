package com.himo.hodoor.data

import java.text.Normalizer
import java.util.Locale

/** Lowercase, strip accents, trim and collapse whitespace. Used by import and search. */
fun normalize(input: String): String =
    Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
        .trim()
        .replace(Regex("\\s+"), " ")

fun normalizedStudentName(firstName: String, familyName: String): String =
    normalize("$firstName $familyName")

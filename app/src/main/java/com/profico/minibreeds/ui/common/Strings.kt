package com.profico.minibreeds.ui.common

/**
 * Upper-cases the first character, turning lowercase API breed names
 * ("bulldog") into display titles ("Bulldog").
 */
fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

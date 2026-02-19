package no.nav.syfo.utils

/**
 * Returns an enum entry with the specified name or `null` if no such entry was found.
 */
inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? = enumValues<T>().find { it.name == name }

fun String.capitalizeFirstLetter(): String =
    this.lowercase().replaceFirstChar { it.uppercaseChar() }

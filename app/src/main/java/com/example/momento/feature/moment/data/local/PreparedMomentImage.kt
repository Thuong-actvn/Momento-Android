package com.example.momento.feature.moment.data.local

import java.io.File
import java.io.IOException

data class PreparedMomentImage(val file: File, val mimeType: String)

class InvalidMomentImageException(val tooLarge: Boolean = false) : IOException()

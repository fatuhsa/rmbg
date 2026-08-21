package com.rmbg.app.domain

import android.graphics.Bitmap

interface BackgroundRemover {
    suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap>
}

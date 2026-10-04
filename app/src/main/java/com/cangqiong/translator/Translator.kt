package com.cangqiong.translator

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class Translator {

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.JAPANESE)
        .setTargetLanguage(TranslateLanguage.INDONESIAN)
        .build()

    private val client = Translation.getClient(options)

    suspend fun prepare() = suspendCancellableCoroutine<Unit> { cont ->
        client.downloadModelIfNeeded()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    suspend fun translate(text: String): String {
        if (text.isBlank()) return ""
        return suspendCancellableCoroutine { cont ->
            client.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(text) }
        }
    }

    fun close() = client.close()
}

package com.cangqiong.translator

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MyTranslator {

    private var currentTarget: String = TranslateLanguage.INDONESIAN
    private var currentSource: String = TranslateLanguage.ENGLISH
    private var client: Translator = build(currentSource, currentTarget)

    private fun build(source: String, target: String): Translator =
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )

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

    fun setTarget(newTarget: String) {
        if (newTarget == currentTarget) return
        runCatching { client.close() }
        currentTarget = newTarget
        client = build(currentSource, currentTarget)
    }

    fun setSource(code: String) {
        if (code == currentSource) return
        runCatching { client.close() }
        currentSource = code
        client = build(currentSource, currentTarget)
    }

    fun close() = runCatching { client.close() }
}

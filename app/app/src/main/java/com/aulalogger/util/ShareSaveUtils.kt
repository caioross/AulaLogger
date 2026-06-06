package com.aulalogger.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aulalogger.data.Session
import java.io.File
import java.io.FileInputStream

object ShareSaveUtils {

    private const val AUTHORITY = "com.aulalogger.fileprovider"

    /** Compartilha apenas o texto da transcrição (WhatsApp, email, etc). */
    fun shareTranscriptText(context: Context, session: Session) {
        if (session.transcript.isBlank()) {
            Toast.makeText(context, "Sem transcrição para compartilhar", Toast.LENGTH_SHORT).show()
            return
        }
        val body = buildString {
            appendLine(session.name)
            appendLine()
            append(session.transcript)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_SUBJECT, session.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar transcrição").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** Compartilha o arquivo .wav via FileProvider. */
    fun shareAudio(context: Context, session: Session) {
        val file = File(session.audioFile)
        if (!file.exists()) {
            Toast.makeText(context, "Áudio não encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, session.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar áudio").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** Salva o texto em Downloads/AulaLogger via MediaStore. */
    fun saveTranscriptToDownloads(context: Context, session: Session): Boolean {
        if (session.transcript.isBlank()) {
            Toast.makeText(context, "Sem transcrição para salvar", Toast.LENGTH_SHORT).show()
            return false
        }
        val safeName = session.name.replace(Regex("[^A-Za-z0-9 _\\-]"), "").take(80).ifBlank { "AulaLogger" }
        val displayName = "$safeName.txt"
        val content = buildString {
            appendLine(session.name)
            appendLine()
            append(session.transcript)
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AulaLogger")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "Salvo em Downloads/AulaLogger/$displayName", Toast.LENGTH_LONG).show()
                true
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AulaLogger")
                dir.mkdirs()
                val out = File(dir, displayName)
                out.writeText(content)
                Toast.makeText(context, "Salvo em ${out.absolutePath}", Toast.LENGTH_LONG).show()
                true
            }
        } catch (t: Throwable) {
            Toast.makeText(context, "Erro ao salvar: ${t.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    /** Salva o áudio .wav em Downloads/AulaLogger via MediaStore. */
    fun saveAudioToDownloads(context: Context, session: Session): Boolean {
        val src = File(session.audioFile)
        if (!src.exists()) {
            Toast.makeText(context, "Áudio não encontrado", Toast.LENGTH_SHORT).show()
            return false
        }
        val safeName = session.name.replace(Regex("[^A-Za-z0-9 _\\-]"), "").take(80).ifBlank { "AulaLogger" }
        val displayName = "$safeName.wav"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AulaLogger")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(src).use { input -> input.copyTo(out) }
                }
                Toast.makeText(context, "Salvo em Downloads/AulaLogger/$displayName", Toast.LENGTH_LONG).show()
                true
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AulaLogger")
                dir.mkdirs()
                val out = File(dir, displayName)
                src.copyTo(out, overwrite = true)
                Toast.makeText(context, "Salvo em ${out.absolutePath}", Toast.LENGTH_LONG).show()
                true
            }
        } catch (t: Throwable) {
            Toast.makeText(context, "Erro ao salvar: ${t.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
}

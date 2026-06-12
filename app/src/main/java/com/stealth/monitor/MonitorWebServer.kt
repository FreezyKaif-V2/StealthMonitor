package com.stealth.monitor

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MonitorWebServer(port: Int, private val monitorDir: File) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        
        return when (uri) {
            "/zip" -> serveZip()
            "/latest" -> serveLatestImage()
            "/img" -> serveSpecificImage(session.parms["f"])
            else -> serveDashboard()
        }
    }

    private fun serveDashboard(): Response {
        val files = monitorDir.listFiles()?.sortedByDescending { it.name } ?: emptyArray()
        val latestPath = files.firstOrNull()?.name ?: ""
        
        val html = """
            <html><head><title>Monitor</title>
            <meta http-equiv="refresh" content="3">
            <style>body{background:#111;color:#fff;font-family:sans-serif;text-align:center;} img{max-width:90%;border:2px solid #444;}</style></head>
            <body>
                <h2>Live View (Auto-Refreshes)</h2>
                <img src="/img?f=$latestPath" />
                <br><br>
                <a href="/zip" style="color:#0af;">Download All as ZIP</a>
            </body></html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun serveLatestImage(): Response {
        val files = monitorDir.listFiles()?.sortedByDescending { it.name }
        val latest = files?.firstOrNull()
        return if (latest != null && latest.exists()) {
            newFixedLengthResponse(Response.Status.OK, "image/png", FileInputStream(latest), latest.length())
        } else {
            newFixedLengthResponse("No images yet")
        }
    }

    private fun serveSpecificImage(filename: String?): Response {
        if (filename.isNullOrEmpty()) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing file name")
        val file = File(monitorDir, filename)
        return if (file.exists()) {
            newFixedLengthResponse(Response.Status.OK, "image/png", FileInputStream(file), file.length())
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun serveZip(): Response {
        val files = monitorDir.listFiles() ?: emptyArray()
        if (files.isEmpty()) return newFixedLengthResponse("No files to zip")

        val tempZip = File.createTempFile("monitor_cache", ".zip")
        ZipOutputStream(tempZip.outputStream().buffered()).use { zos ->
            for (file in files) {
                zos.putNextEntry(ZipEntry(file.name))
                FileInputStream(file).use { fis -> fis.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "application/zip", FileInputStream(tempZip), tempZip.length())
    }
}

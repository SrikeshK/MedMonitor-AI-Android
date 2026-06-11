package com.medmonitor.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.WeeklySummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeeklyReportPdfGenerator(private val context: Context) {

    fun generateWeeklyReport(summary: WeeklySummary): File? {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint()

        // Page info: A4 size is roughly 595 x 842 points
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val user = FirebaseAuth.getInstance().currentUser
        val userName = user?.displayName ?: "Patient"
        val currentDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        var y = 40f

        // Title
        titlePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        titlePaint.textSize = 20f
        titlePaint.color = Color.BLACK
        canvas.drawText("MedMonitor AI - Weekly Health Report", 40f, y, titlePaint)
        y += 30f

        // Header Info
        paint.textSize = 12f
        paint.color = Color.DKGRAY
        canvas.drawText("Patient: $userName", 40f, y, paint)
        y += 20f
        canvas.drawText("Generated on: $currentDate", 40f, y, paint)
        y += 40f

        // Summary Section
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        paint.color = Color.BLACK
        canvas.drawText("Weekly Summary", 40f, y, paint)
        y += 25f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        canvas.drawText("Adherence: ${summary.adherencePercent}%", 40f, y, paint)
        y += 20f
        canvas.drawText("Total Taken: ${summary.totalTaken}", 40f, y, paint)
        y += 20f
        canvas.drawText("Total Missed: ${summary.totalMissed}", 40f, y, paint)
        y += 20f
        canvas.drawText("Total Delayed: ${summary.totalDelayed}", 40f, y, paint)
        y += 40f

        // History Table Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Date", 40f, y, paint)
        canvas.drawText("Medicine", 140f, y, paint)
        canvas.drawText("Slot", 300f, y, paint)
        canvas.drawText("Status", 450f, y, paint)
        y += 10f
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 20f

        // History Rows
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        
        // Ensure logs are newest first
        val sortedLogs = summary.logs.sortedByDescending { it.timestamp }

        for (log in sortedLogs) {
            if (y > 800) { 
                break 
            }
            
            val dateStr = dateFormat.format(log.timestamp.toDate())
            canvas.drawText(dateStr, 40f, y, paint)
            canvas.drawText(log.medicineName, 140f, y, paint)
            canvas.drawText(log.slotName, 300f, y, paint)
            canvas.drawText(log.status.name, 450f, y, paint)
            y += 20f
        }

        pdfDocument.finishPage(page)

        // 🧩 PDF EXPORT FIX: Store inside Documents/MedMonitorReports/
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val directory = File(baseDir, "MedMonitorReports")
        if (!directory.exists()) directory.mkdirs()
        
        // 🧩 Filename: Weekly_Report_yyyyMMdd_HHmm.pdf
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val file = File(directory, "Weekly_Report_$timeStamp.pdf")

        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }
}

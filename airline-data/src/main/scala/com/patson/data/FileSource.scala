package com.patson.data

import scala.collection.mutable.ListBuffer

import java.io.{ByteArrayInputStream, File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}
import javax.imageio.ImageIO

/**
 * Todo one reset: move other logos to file in / out
 */
object FileSource {
  val LOGO_DIRECTORY = "data/logos"

  private def ensureLogoDirectory(): Unit = {
    val logoDir = new File(LOGO_DIRECTORY)
    if (!logoDir.exists()) {
      logoDir.mkdirs()
      // Set restrictive permissions: owner read/write/execute only
      logoDir.setReadable(true, true)
      logoDir.setWritable(true, true)
      logoDir.setExecutable(true, true)
    }
  }

  private def getLogoPath(dir: String, id: Int): String = {
    s"$LOGO_DIRECTORY/$dir/$id.png"
  }

  def saveLogo(id: Int, dir:String, logo: Array[Byte]) = {
    try {
      val maxLogoSize = 200 * 1024 // 200KB
      if (logo.length > maxLogoSize) {
        throw new IllegalArgumentException(s"Logo file too large: ${logo.length} bytes (maximum is $maxLogoSize bytes)")
      }

      // Validate image format and dimensions
      val image = ImageIO.read(new ByteArrayInputStream(logo))
      if (image == null) {
        throw new IllegalArgumentException("Invalid or corrupted image file")
      }

      val width = image.getWidth
      val height = image.getHeight
      if (width != 48 || height != 24) {
        throw new IllegalArgumentException(s"Logo must be exactly 24x48 pixels. Provided dimensions: ${width}x${height}")
      }

      ensureLogoDirectory()
      val logoPath = getLogoPath(dir, id)

      // Write logo to file
      val fos = new FileOutputStream(logoPath)
      try {
        fos.write(logo)
        fos.flush()
      } finally {
        fos.close()
      }

      // Set restrictive file permissions: owner read/write only
      val logoFile = new File(logoPath)
      logoFile.setReadable(false, false)
      logoFile.setWritable(false, false)
      logoFile.setExecutable(false, false)
      logoFile.setReadable(true, true)
      logoFile.setWritable(true, true)

    } catch {
      case e: Exception =>
        System.err.println(s"Failed to save logo for $dir $id: ${e.getMessage}")
        e.printStackTrace()
        throw e
    }
  }

  def loadLogos(dir: String): scala.collection.immutable.Map[Int, Array[Byte]] = {
    val logos = new ListBuffer[(Int, Array[Byte])]()

    try {
      val logoDir = new File(s"$LOGO_DIRECTORY/$dir")
      if (logoDir.exists() && logoDir.isDirectory) {
        // List all .png files in the logos directory
        val logoFiles = logoDir.listFiles((f: File) => f.isFile && f.getName.endsWith(".png"))

        if (logoFiles != null) {
          for (file <- logoFiles) {
            try {
              val id = file.getName.dropRight(4).toInt // Remove ".png" extension

              val maxLogoSize = 200 * 1024 // 200KB
              if (file.length() <= maxLogoSize) {
                val bytes = Files.readAllBytes(file.toPath)
                logos += Tuple2(id, bytes)
              } else {
                System.err.println(s"Logo file too large for $dir $id: ${file.length()} bytes (max: $maxLogoSize bytes)")
              }
            } catch {
              case e: NumberFormatException =>
                System.err.println(s"Invalid logo filename (not numeric): ${file.getName}")
              case e: Exception =>
                System.err.println(s"Failed to load logo from ${file.getName}: ${e.getMessage}")
            }
          }
        }
      }
    } catch {
      case e: Exception =>
        System.err.println(s"Failed to load logos directory: ${e.getMessage}")
        e.printStackTrace()
    }

    logos.toMap
  }

  def deleteLogo(dir: String, id: Int): Unit = {
    try {
      val logoPath = getLogoPath(dir, id)
      val logoFile = new File(logoPath)
      if (logoFile.exists()) {
        if (!logoFile.delete()) {
          System.err.println(s"Failed to delete logo file: $logoPath")
        }
      }
    } catch {
      case e: Exception =>
        System.err.println(s"Error deleting logo for $dir $id: ${e.getMessage}")
    }
  }
}
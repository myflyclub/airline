package com.patson.data

import java.io.{ByteArrayInputStream, File, FileOutputStream}
import java.nio.file.Files
import javax.imageio.ImageIO

object FileSource {
  val LOGO_DIRECTORY = "data/logos"

  val CONTENT_TYPE_PNG = "image/png"
  val CONTENT_TYPE_JPEG = "image/jpeg"
  val CONTENT_TYPE_WEBP = "image/webp"

  def detectContentType(bytes: Array[Byte]): String = {
    if (bytes.length >= 8 &&
      bytes(0) == 0x89.toByte && bytes(1) == 0x50 && bytes(2) == 0x4E && bytes(3) == 0x47) {
      CONTENT_TYPE_PNG
    } else if (bytes.length >= 3 &&
      bytes(0) == 0xFF.toByte && bytes(1) == 0xD8.toByte && bytes(2) == 0xFF.toByte) {
      CONTENT_TYPE_JPEG
    } else if (bytes.length >= 12 &&
      bytes(0) == 0x52 && bytes(1) == 0x49 && bytes(2) == 0x46 && bytes(3) == 0x46 &&
      bytes(8) == 0x57 && bytes(9) == 0x45 && bytes(10) == 0x42 && bytes(11) == 0x50) {
      CONTENT_TYPE_WEBP
    } else {
      "application/octet-stream"
    }
  }

  private def ensureDirectory(path: String): Unit = {
    val dir = new File(path)
    if (!dir.exists()) {
      dir.mkdirs()
      dir.setReadable(true, true)
      dir.setWritable(true, true)
      dir.setExecutable(true, true)
    }
  }

  private def getLogoPath(dir: String, id: Int): String = {
    s"$LOGO_DIRECTORY/$dir/$id.png"
  }

  def saveImage(id: Int, dir: String, data: Array[Byte], maxBytes: Int = 2048 * 1024,
                allowedContentTypes: Set[String] = Set(CONTENT_TYPE_PNG),
                exactWidth: Int = 0, exactHeight: Int = 0,
                maxWidth: Int = 0, maxHeight: Int = 0): Unit = {
    if (data.length > maxBytes) {
      throw new IllegalArgumentException(s"File too large: ${data.length} bytes (maximum is $maxBytes bytes)")
    }

    val contentType = detectContentType(data)
    if (!allowedContentTypes.contains(contentType)) {
      throw new IllegalArgumentException(s"Invalid image format: $contentType. Allowed: ${allowedContentTypes.mkString(", ")}")
    }

    val image = ImageIO.read(new ByteArrayInputStream(data))
    if (image == null) {
      throw new IllegalArgumentException("Invalid or corrupted image file")
    }

    val width = image.getWidth
    val height = image.getHeight

    if (exactWidth > 0 && exactHeight > 0 && (width != exactWidth || height != exactHeight)) {
      throw new IllegalArgumentException(s"Image must be exactly ${exactWidth}x${exactHeight}. Provided: ${width}x${height}")
    }

    if (maxWidth > 0 && width > maxWidth) {
      throw new IllegalArgumentException(s"Image width ${width} exceeds maximum ${maxWidth}")
    }
    if (maxHeight > 0 && height > maxHeight) {
      throw new IllegalArgumentException(s"Image height ${height} exceeds maximum ${maxHeight}")
    }

    writeFile(dir, id, data)
  }

  def saveLogo(id: Int, dir: String, logo: Array[Byte], minWidth: Int = 0, minHeight: Int = 0, checkRatio: Boolean = true): Unit = {
    val maxLogoSize = 2048 * 1024
    if (logo.length > maxLogoSize) {
      throw new IllegalArgumentException(s"File too large: ${logo.length} bytes (maximum is $maxLogoSize bytes)")
    }

    val image = ImageIO.read(new ByteArrayInputStream(logo))
    if (image == null) {
      throw new IllegalArgumentException("Invalid or corrupted image file")
    }

    val width = image.getWidth
    val height = image.getHeight

    if (width < minWidth || height < minHeight) {
      throw new IllegalArgumentException(s"Image too small. Minimum dimensions: ${minWidth}x${minHeight}. Provided: ${width}x${height}")
    }

    if (checkRatio && width * 10 / height != 20) {
      throw new IllegalArgumentException(s"Logo must have 2:1 aspect ratio. Provided dimensions: ${width}x${height}")
    }

    writeFile(dir, id, logo)
  }

  private def writeFile(dir: String, id: Int, data: Array[Byte]): Unit = {
    try {
      ensureDirectory(LOGO_DIRECTORY)
      val logoPath = getLogoPath(dir, id)
      val parentDir = new File(logoPath).getParentFile
      if (!parentDir.exists()) {
        parentDir.mkdirs()
      }

      val fos = new FileOutputStream(logoPath)
      try {
        fos.write(data)
        fos.flush()
      } finally {
        fos.close()
      }

      val file = new File(logoPath)
      file.setReadable(false, false)
      file.setWritable(false, false)
      file.setExecutable(false, false)
      file.setReadable(true, true)
      file.setWritable(true, true)
    } catch {
      case e: Exception =>
        System.err.println(s"Failed to save file for $dir $id: ${e.getMessage}")
        throw e
    }
  }

  def loadLogo(dir: String, id: Int): Option[Array[Byte]] = {
    val logoPath = getLogoPath(dir, id)
    val file = new File(logoPath)
    if (file.exists() && file.isFile) {
      try {
        Some(Files.readAllBytes(file.toPath))
      } catch {
        case e: Exception =>
          System.err.println(s"Failed to load logo from $logoPath: ${e.getMessage}")
          None
      }
    } else {
      None
    }
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
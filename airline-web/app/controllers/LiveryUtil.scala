package controllers

import com.patson.data.FileSource

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO

object LiveryUtil {
  val blank: Array[Byte] = getBlankImage(1, 1)

  val MAX_SIZE = 2 * 1024 * 1024
  val MAX_WIDTH = 600
  val MAX_HEIGHT = 300
  val ALLOWED_TYPES: Set[String] = Set(FileSource.CONTENT_TYPE_JPEG, FileSource.CONTENT_TYPE_WEBP)

  def getLivery(airlineId: Int): Array[Byte] = {
    FileSource.loadLogo("livery", airlineId).getOrElse(blank)
  }

  def getLiveryContentType(airlineId: Int): String = {
    FileSource.loadLogo("livery", airlineId).map(FileSource.detectContentType).getOrElse(FileSource.CONTENT_TYPE_PNG)
  }

  def getBlankImage(width: Int, height: Int): Array[Byte] = {
    import java.awt.Color
    import java.awt.image.BufferedImage
    val singlePixelImage = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)
    val transparent = new Color(0, 0, 0, 0)
    singlePixelImage.setRGB(0, 0, transparent.getRGB)

    val outputStream = new ByteArrayOutputStream()
    ImageIO.write(singlePixelImage, "png", outputStream)
    outputStream.toByteArray
  }

  def saveLivery(airlineId: Int, livery: Array[Byte]): Unit = {
    FileSource.saveImage(airlineId, "livery", livery,
      maxBytes = MAX_SIZE,
      allowedContentTypes = ALLOWED_TYPES,
      maxWidth = MAX_WIDTH, maxHeight = MAX_HEIGHT)
  }

  def deleteLivery(airlineId: Int): Unit = {
    FileSource.deleteLogo("livery", airlineId)
  }

  def validateUpload(liveryFile: Path): Option[String] = {
    try {
      val imageFile = liveryFile.toFile
      if (imageFile.length() > MAX_SIZE) {
        return Some(s"Image must be under ${MAX_SIZE / 1024 / 1024}MB")
      }

      val bytes = java.nio.file.Files.readAllBytes(liveryFile)
      val contentType = FileSource.detectContentType(bytes)
      if (!ALLOWED_TYPES.contains(contentType)) {
        return Some(s"Livery must be JPG or WebP format (got $contentType)")
      }

      val image = ImageIO.read(imageFile)
      if (image == null) {
        return Some("Cannot read image file")
      }
      if (image.getWidth > MAX_WIDTH || image.getHeight > MAX_HEIGHT) {
        return Some(s"Livery must be at most ${MAX_WIDTH}x${MAX_HEIGHT} pixels (got ${image.getWidth}x${image.getHeight})")
      }

      None
    } catch {
      case e: Exception => Some(s"Error validating livery: ${e.getMessage}")
    }
  }
}

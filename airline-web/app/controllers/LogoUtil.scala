package controllers

import java.nio.file.Path
import com.patson.data.FileSource
import javax.imageio.ImageIO

object LogoUtil {
  private val logoCache = com.google.common.cache.CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, java.util.concurrent.TimeUnit.MINUTES)
    .build[java.lang.Integer, Array[Byte]]()

  private val allianceLogoCache = com.google.common.cache.CacheBuilder.newBuilder()
    .maximumSize(200)
    .expireAfterWrite(10, java.util.concurrent.TimeUnit.MINUTES)
    .build[java.lang.Integer, Array[Byte]]()

  lazy val blank: Array[Byte] = loadResourceSafely("/logo/blank.png").getOrElse(Array.emptyByteArray)
  lazy val rat: Array[Byte] = loadResourceSafely("/logo/bot-rat-logo.png").getOrElse(blank)
  lazy val local: Array[Byte] = loadResourceSafely("/logo/bot-localConnection-logo.png").getOrElse(blank)

  val imageHeight = 64
  val imageWidth = 128

  def getLogo(airlineId: Int): Array[Byte] = {
    if (airlineId == 0) {
      local
    } else if (airlineId > 0 && airlineId < 30) {
      rat
    } else {
      val cached = logoCache.getIfPresent(airlineId)
      if (cached != null) {
        return cached
      }

      FileSource.loadLogo("airline", airlineId) match {
        case Some(logo) =>
          logoCache.put(airlineId, logo)
          logo
        case None => blank
      }
    }
  }

  def saveLogo(airlineId: Int, logo: Array[Byte]): Unit = {
    FileSource.saveImage(airlineId, "airline", logo,
      allowedContentTypes = Set(FileSource.CONTENT_TYPE_PNG),
      exactWidth = imageWidth, exactHeight = imageHeight)
    logoCache.put(airlineId, logo)
  }

  def getAllianceLogo(allianceId: Int): Array[Byte] = {
    val cached = allianceLogoCache.getIfPresent(allianceId)
    if (cached != null) {
      return cached
    }

    FileSource.loadLogo("alliance", allianceId) match {
      case Some(logo) =>
        allianceLogoCache.put(allianceId, logo)
        logo
      case None => blank
    }
  }

  def saveAllianceLogo(allianceId: Int, logo: Array[Byte]): Unit = {
    FileSource.saveImage(allianceId, "alliance", logo,
      allowedContentTypes = Set(FileSource.CONTENT_TYPE_PNG),
      exactWidth = imageWidth, exactHeight = imageHeight)
    allianceLogoCache.put(allianceId, logo)
  }

  def validateUpload(logoFile: Path): Option[String] = {
    try {
      val bytes = java.nio.file.Files.readAllBytes(logoFile)
      val contentType = FileSource.detectContentType(bytes)
      if (contentType != FileSource.CONTENT_TYPE_PNG) {
        return Some(s"Logo must be PNG format (got $contentType)")
      }

      val image = ImageIO.read(logoFile.toFile)
      if (image == null) {
        return Some("Cannot read image file")
      }
      if (image.getWidth != imageWidth || image.getHeight != imageHeight) {
        return Some(s"Logo must be exactly ${imageWidth}x${imageHeight} pixels (got ${image.getWidth}x${image.getHeight})")
      }

      None
    } catch {
      case e: Exception => Some(s"Error validating logo: ${e.getMessage}")
    }
  }

  private def loadResourceSafely(path: String): Option[Array[Byte]] = {
    val isOpt = Option(play.Environment.simple().resourceAsStream(path))
    isOpt.flatMap { is =>
      try {
        val baos = new java.io.ByteArrayOutputStream()
        val buffer = new Array[Byte](8192)
        var read = is.read(buffer)
        while (read != -1) {
          baos.write(buffer, 0, read)
          read = is.read(buffer)
        }
        Some(baos.toByteArray)
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          None
      } finally {
        try is.close() catch { case _: Throwable => }
      }
    }
  }
}

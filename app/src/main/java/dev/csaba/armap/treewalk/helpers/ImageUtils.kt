package dev.csaba.armap.treewalk.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import io.github.crow_misia.libyuv.*

// https://stackoverflow.com/questions/54017087/how-to-convert-yuv-420-888-image-to-bitmap
fun imageToBitmap(image: Image): Bitmap? {
    assert(image.format == ImageFormat.NV21)

    // NV21 is a plane of 8 bit Y values followed by interleaved  Cb Cr
    val ib: ByteBuffer = ByteBuffer.allocate(image.height * image.width * 2)
    val y: ByteBuffer = image.planes[0].buffer
    val cr: ByteBuffer = image.planes[1].buffer
    val cb: ByteBuffer = image.planes[2].buffer
    ib.put(y)
    ib.put(cb)
    ib.put(cr)
    val yuvImage = YuvImage(
        ib.array(),
        ImageFormat.NV21, image.width, image.height, null
    )
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(
        Rect(
            0, 0,
            image.width, image.height
        ), 50, out
    )
    val imageBytes: ByteArray = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

/*
https://blog.minhazav.dev/how-to-convert-yuv-420-sp-android.media.Image-to-Bitmap-or-jpeg/
https://blog.minhazav.dev/how-to-use-renderscript-to-convert-YUV_420_888-yuv-image-to-bitmap/
fun yuv420ToBitmap(image: Image, context: Context?): Bitmap? {
    val rs = RenderScript.create(context)
    val script = ScriptIntrinsicYuvToRGB.create(
        rs, Element.U8_4(rs)
    )

    // Refer the logic in a section below on how to convert a YUV_420_888 image
    // to single channel flat 1D array. For sake of this example I'll abstract it
    // as a method.
    val yuvByteArray: ByteArray = yuv420ToByteArray(image)
    val yuvType: Type.Builder = Builder(rs, Element.U8(rs))
        .setX(yuvByteArray.size)
    val `in` = Allocation.createTyped(
        rs, yuvType.create(), Allocation.USAGE_SCRIPT
    )
    val rgbaType: Type.Builder = Builder(rs, Element.RGBA_8888(rs))
        .setX(image.getWidth())
        .setY(image.getHeight())
    val out = Allocation.createTyped(
        rs, rgbaType.create(), Allocation.USAGE_SCRIPT
    )

    // The allocations above "should" be cached if you are going to perform
    // repeated conversion of YUV_420_888 to Bitmap.
    `in`.copyFrom(yuvByteArray)
    script.setInput(`in`)
    script.forEach(out)
    val bitmap = Bitmap.createBitmap(
        image.getWidth(), image.getHeight(), Config.ARGB_8888
    )
    out.copyTo(bitmap)
    return bitmap
}
*/
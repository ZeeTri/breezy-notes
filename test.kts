import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

val file = File("C:\\Users\\zisha\\Documents\\5_OtherApps\\BreezyNotes\\app\\src\\main\\res\\mipmap-xxxhdpi\\ic_launcher_img.jpg")
val image: BufferedImage = ImageIO.read(file)
val colorInt = image.getRGB(0, 0)
val hex = String.format("#%06X", (0xFFFFFF and colorInt))
println("BACKGROUND_COLOR: $hex")

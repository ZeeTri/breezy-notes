Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile('C:\Users\zisha\Documents\5_OtherApps\BreezyNotes\app\src\main\res\mipmap-xxxhdpi\ic_launcher_img.jpg')
$color = $bmp.GetPixel(0,0)
$hex = '{0:X2}{1:X2}{2:X2}' -f $color.R, $color.G, $color.B
Write-Host "BACKGROUND_COLOR: #$hex"
$bmp.Dispose()

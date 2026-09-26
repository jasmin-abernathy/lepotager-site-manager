import AppKit
import CoreGraphics
import Foundation

private let canvasSize = 1024
private let background = CGColor(red: 252.0 / 255.0, green: 251.0 / 255.0, blue: 247.0 / 255.0, alpha: 1.0)
private let primary = CGColor(red: 24.0 / 255.0, green: 62.0 / 255.0, blue: 45.0 / 255.0, alpha: 1.0)
private let secondary = CGColor(red: 120.0 / 255.0, green: 148.0 / 255.0, blue: 123.0 / 255.0, alpha: 1.0)
private let accent = CGColor(red: 203.0 / 255.0, green: 96.0 / 255.0, blue: 62.0 / 255.0, alpha: 1.0)

guard CommandLine.arguments.count == 2 else {
    fputs("Usage: swift generate-app-icon.swift <output.png>\n", stderr)
    exit(64)
}

guard
    let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
    let context = CGContext(
        data: nil,
        width: canvasSize,
        height: canvasSize,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    )
else {
    fputs("Unable to create icon bitmap context.\n", stderr)
    exit(1)
}

// Match the source SVG coordinate system: origin at top-left.
context.translateBy(x: 0, y: CGFloat(canvasSize))
context.scaleBy(x: 1, y: -1)
context.setAllowsAntialiasing(true)
context.setShouldAntialias(true)

context.setFillColor(background)
context.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024))

context.setFillColor(primary)
context.addPath(CGPath(roundedRect: CGRect(x: 190, y: 175, width: 275, height: 275), cornerWidth: 55, cornerHeight: 55, transform: nil))
context.fillPath()

context.setFillColor(secondary)
context.addPath(CGPath(roundedRect: CGRect(x: 190, y: 522, width: 275, height: 275), cornerWidth: 55, cornerHeight: 55, transform: nil))
context.fillPath()

context.setFillColor(accent)
context.fillEllipse(in: CGRect(x: 637, y: 207, width: 156, height: 156))

context.setStrokeColor(primary)
context.setLineWidth(44)
context.addPath(CGPath(roundedRect: CGRect(x: 537, y: 522, width: 275, height: 275), cornerWidth: 55, cornerHeight: 55, transform: nil))
context.strokePath()

guard let cgImage = context.makeImage() else {
    fputs("Unable to render app icon.\n", stderr)
    exit(1)
}

let output = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(
    at: output.deletingLastPathComponent(),
    withIntermediateDirectories: true,
    attributes: nil
)
let bitmap = NSBitmapImageRep(cgImage: cgImage)
guard let png = bitmap.representation(using: .png, properties: [:]) else {
    fputs("Unable to encode app icon as PNG.\n", stderr)
    exit(1)
}
try png.write(to: output, options: .atomic)

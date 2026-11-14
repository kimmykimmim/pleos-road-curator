import Foundation
import Vision
import CoreImage
import AppKit

// Lifts the photograph's subject out of its background using the system's own
// segmentation. Nothing is generated: the pixels that come out are the pixels
// that went in, minus the ones the mask says are background.
let args = CommandLine.arguments
guard args.count >= 3 else { print("usage: lift <in.jpg> <out.png>"); exit(2) }
let inURL = URL(fileURLWithPath: args[1])
let outURL = URL(fileURLWithPath: args[2])
guard let src = CIImage(contentsOf: inURL) else { print("cannot read"); exit(3) }

let handler = VNImageRequestHandler(url: inURL, options: [:])
let request = VNGenerateForegroundInstanceMaskRequest()
do { try handler.perform([request]) } catch { print("vision failed: \(error)"); exit(4) }
guard let result = request.results?.first else { print("no subject found"); exit(5) }
let mask = try result.generateMaskedImage(ofInstances: result.allInstances,
                                          from: handler, croppedToInstancesExtent: true)
let ci = CIImage(cvPixelBuffer: mask)
let ctx = CIContext()
guard let cg = ctx.createCGImage(ci, from: ci.extent) else { print("render failed"); exit(6) }
let rep = NSBitmapImageRep(cgImage: cg)
guard let data = rep.representation(using: .png, properties: [:]) else { print("encode failed"); exit(7) }
try data.write(to: outURL)
print("ok \(Int(ci.extent.width))x\(Int(ci.extent.height)) instances=\(result.allInstances.count)")

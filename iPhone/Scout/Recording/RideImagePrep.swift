// SPDX-License-Identifier: MIT
import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Decode any gallery/camera image into a stripped JPEG suitable for the ride sidecar.
enum RideImagePrep {
    static let maxEdgePx = 2048
    static let jpegQuality: CGFloat = 0.85

    static func compress(_ data: Data) throws -> Data {
        #if canImport(UIKit)
        guard let image = UIImage(data: data) else {
            throw NSError(domain: "Scout", code: 2, userInfo: [NSLocalizedDescriptionKey: "Could not decode image"])
        }
        return try compress(image)
        #else
        return data
        #endif
    }

    #if canImport(UIKit)
    static func compress(_ image: UIImage) throws -> Data {
        let scaled = scaleToMax(image, maxEdge: maxEdgePx)
        guard let jpeg = scaled.jpegData(compressionQuality: jpegQuality) else {
            throw NSError(domain: "Scout", code: 2, userInfo: [NSLocalizedDescriptionKey: "Could not compress image"])
        }
        return jpeg
    }

    private static func scaleToMax(_ image: UIImage, maxEdge: Int) -> UIImage {
        let size = image.size
        let longest = max(size.width, size.height)
        if longest <= CGFloat(maxEdge) { return image }
        let scale = CGFloat(maxEdge) / longest
        let w = max((size.width * scale).rounded(), 1)
        let h = max((size.height * scale).rounded(), 1)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(x: 0, y: 0, width: w, height: h))
        }
    }
    #endif

    static func sampleSize(width: Int, height: Int, maxEdge: Int) -> Int {
        var sample = 1
        var longest = max(width, height)
        while longest / 2 >= maxEdge {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}

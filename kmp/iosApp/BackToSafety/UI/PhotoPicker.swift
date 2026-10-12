import ImageIO
import Shared
import SwiftUI
import UIKit

/// expo-image-picker with allowsEditing, aspect [1, 1], quality 0.8: UIImagePickerController
/// with its square editing step, then the image (at most `maxPhotoSide` px on a side, so a
/// camera original doesn't stay a 50 MP file) saved as <Documents>/profile_photo_<epochMs>.jpg.
/// `onPhoto` gets its file:// URI, the form the RN app stores (spec/storage.md).
struct ImagePicker: UIViewControllerRepresentable {
    let source: UIImagePickerController.SourceType
    let onPhoto: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(source) ? source : .photoLibrary
        picker.mediaTypes = ["public.image"]
        picker.allowsEditing = true
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_: UIImagePickerController, context _: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: ImagePicker
        init(_ parent: ImagePicker) { self.parent = parent }

        func imagePickerController(_: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let image = (info[.editedImage] ?? info[.originalImage]) as? UIImage
            if let data = image?.fitting(maxSide: maxPhotoSide).jpegData(compressionQuality: 0.8) {
                let file = documentsDirectory.appendingPathComponent("\(photoFilePrefix)\(Int64(Date().timeIntervalSince1970 * 1000)).jpg")
                if (try? data.write(to: file)) != nil {
                    excludeFromBackup(file)
                    parent.onPhoto(file.absoluteString)
                }
            }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_: UIImagePickerController) { parent.dismiss() }
    }
}

/// Which picker a screen is showing.
enum PickerSource: Identifiable {
    case camera, library
    var id: Self { self }
    var uiSource: UIImagePickerController.SourceType { self == .camera ? .camera : .photoLibrary }
}

/// The longest side, in pixels, of a saved photo: more than any screen shows.
private let maxPhotoSide: CGFloat = 1024

private let photoFilePrefix = "profile_photo_"

private var documentsDirectory: URL {
    FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
}

private extension UIImage {
    /// This image scaled down (never up) to at most `maxSide` pixels on its longer side.
    func fitting(maxSide: CGFloat) -> UIImage {
        let pixels = CGSize(width: size.width * scale, height: size.height * scale)
        let longSide = max(pixels.width, pixels.height)
        guard longSide > maxSide else { return self }
        let target = CGSize(width: (pixels.width * maxSide / longSide).rounded(), height: (pixels.height * maxSide / longSide).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}

private let photoCache = NSCache<NSString, UIImage>()

/// A saved photo (file:// URI), decoded no larger than `maxPixel` on its longer side (a huge
/// file in a header or list would otherwise be decoded in full each time it draws), and kept for
/// the next draw. The container path can change across installs, so it's re-resolved under
/// Documents by file name (the shared `photoFileName`).
func loadPhoto(_ uri: String?, maxPixel: Int = 600) -> UIImage? {
    guard let uri, let url = URL(string: uri) else { return nil }
    return downsampledImage(atPath: url.path, maxPixel: maxPixel)
        ?? PhotoPathKt.photoFileName(uri: uri).flatMap {
            downsampledImage(atPath: documentsDirectory.appendingPathComponent($0).path, maxPixel: maxPixel)
        }
}

private func downsampledImage(atPath path: String, maxPixel: Int) -> UIImage? {
    let key = "\(path)#\(maxPixel)" as NSString
    if let cached = photoCache.object(forKey: key) { return cached }
    guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary) else { return nil }
    let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
    let width = properties?[kCGImagePropertyPixelWidth] as? Int ?? maxPixel
    let height = properties?[kCGImagePropertyPixelHeight] as? Int ?? maxPixel
    let options: [CFString: Any] = [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceShouldCacheImmediately: true,
        kCGImageSourceThumbnailMaxPixelSize: min(maxPixel, max(width, height)),
    ]
    guard let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
    let image = UIImage(cgImage: thumbnail)
    photoCache.setObject(image, forKey: key)
    return image
}

/// Keeps `url` out of iCloud and computer backups, as Android's backup rules leave the photos
/// out. Best effort.
private func excludeFromBackup(_ url: URL) {
    var url = url
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    try? url.setResourceValues(values)
}

/// At launch: excludes the profile photos already in Documents (the RN app's were backed up),
/// so they leave the backups on upgrade. Best effort.
func excludePhotosFromBackup() {
    let names = (try? FileManager.default.contentsOfDirectory(atPath: documentsDirectory.path)) ?? []
    for name in names where name.hasPrefix(photoFilePrefix) {
        excludeFromBackup(documentsDirectory.appendingPathComponent(name))
    }
}

/// Store.photoCleaner: deletes every saved profile photo from Documents except the one at
/// `keeping` (none, if nil). Delete Account passes nil. Best effort.
func cleanPhotos(keeping: String?) {
    photoCache.removeAllObjects()
    let keep = keeping.flatMap { PhotoPathKt.photoFileName(uri: $0) }
    let names = (try? FileManager.default.contentsOfDirectory(atPath: documentsDirectory.path)) ?? []
    for name in names where name.hasPrefix(photoFilePrefix) && name != keep {
        try? FileManager.default.removeItem(at: documentsDirectory.appendingPathComponent(name))
    }
}

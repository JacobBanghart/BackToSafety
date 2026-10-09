import SwiftUI
import UIKit

/// expo-image-picker with allowsEditing, aspect [1, 1], quality 0.8: UIImagePickerController
/// with its square editing step, then the image saved as <Documents>/profile_photo_<epochMs>.jpg.
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
            if let data = image?.jpegData(compressionQuality: 0.8) {
                let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                let file = documents.appendingPathComponent("profile_photo_\(Int64(Date().timeIntervalSince1970 * 1000)).jpg")
                if (try? data.write(to: file)) != nil { parent.onPhoto(file.absoluteString) }
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

/// A saved photo (file:// URI); the container path can change across installs, so it's
/// re-resolved under Documents by file name, as utils/photoPath.ts does.
func loadPhoto(_ uri: String?) -> UIImage? {
    guard let uri, let url = URL(string: uri) else { return nil }
    if let image = UIImage(contentsOfFile: url.path) { return image }
    let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    return UIImage(contentsOfFile: documents.appendingPathComponent(url.lastPathComponent).path)
}

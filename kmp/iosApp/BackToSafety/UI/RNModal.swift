import SwiftUI

/// RN's transparent <Modal>: its own full-screen presentation over the screen (so the screen
/// behind leaves the accessibility tree, as RN's modal window takes it), laid out against the
/// whole screen rather than the safe area. `fade` presents without the slide and fades the
/// content in (animationType="fade"); otherwise it slides up (animationType="slide").
private struct RNModal<Item: Identifiable, Body: View>: ViewModifier {
    @Binding var item: Item?
    let fade: Bool
    let body: (Item) -> Body
    @State private var shown: Item?

    func body(content: Content) -> some View {
        content
            .onChange(of: item?.id, initial: true) {
                var transaction = Transaction()
                transaction.disablesAnimations = fade
                withTransaction(transaction) { shown = item }
            }
            .fullScreenCover(item: Binding(get: { shown }, set: { if $0 == nil { item = nil } })) { presented in
                FadeIn(enabled: fade) { body(presented) }
                    .ignoresSafeArea()
                    .presentationBackground(.clear)
            }
    }
}

private struct FadeIn<Content: View>: View {
    let enabled: Bool
    @ViewBuilder let content: () -> Content
    @State private var visible = false

    var body: some View {
        content()
            .opacity(!enabled || visible ? 1 : 0)
            .onAppear { withAnimation(.easeOut(duration: 0.2)) { visible = true } }
    }
}

extension View {
    func rnModal<Item: Identifiable, Body: View>(item: Binding<Item?>, fade: Bool = true,
                                                @ViewBuilder content: @escaping (Item) -> Body) -> some View {
        modifier(RNModal(item: item, fade: fade, body: content))
    }
}

import SwiftUI
import UIKit
import MonManagerShared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosManagerRuntimeKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all, edges: .bottom)
            .onOpenURL { url in
                IosManagerRuntimeKt.handleIncomingPairingLink(raw: url.absoluteString)
            }
    }
}

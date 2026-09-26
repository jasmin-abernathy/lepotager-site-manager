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
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all, edges: .bottom)
            .onAppear {
                IosManagerRuntimeKt.notifyIosSceneActive(active: scenePhase == .active)
            }
            .onChange(of: scenePhase) { phase in
                IosManagerRuntimeKt.notifyIosSceneActive(active: phase == .active)
            }
            .onOpenURL { url in
                IosManagerRuntimeKt.handleIncomingPairingUrl(raw: url.absoluteString)
            }
    }
}

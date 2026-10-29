import SwiftUI
import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Push notifications: Firebase Cloud Messaging on top of APNs.
/// Tokens and taps are forwarded to shared Kotlin code through `PushEvents`.
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self

        // Without GoogleService-Info.plist the app still runs, just without push.
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            print("Notiz: GoogleService-Info.plist not found — push notifications are disabled.")
            return true
        }
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        // Gets an APNs token silently; the permission prompt is shown later from Kotlin.
        application.registerForRemoteNotifications()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Notiz: APNs registration failed: \(error.localizedDescription)")
    }

    // MARK: MessagingDelegate

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        PushEvents.shared.onNewToken(token: token)
    }

    // MARK: UNUserNotificationCenterDelegate

    /// Show banners even while the app is open, and let open screens refresh.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let type = notification.request.content.userInfo["type"] as? String
        PushEvents.shared.onNotificationReceived(type: type)
        completionHandler([.banner, .list, .sound])
    }

    /// The user tapped a notification: open the matching tab.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let type = response.notification.request.content.userInfo["type"] as? String
        PushEvents.shared.onNotificationOpened(type: type)
        completionHandler()
    }
}

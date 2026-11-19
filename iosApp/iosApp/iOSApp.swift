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
        registerGateCategory()

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

    /// Allow / Deny buttons on visitor alerts (category sent by the push function).
    /// Both need the phone unlocked, so nobody can answer the gate from a locked screen.
    private func registerGateCategory() {
        let labels = GateAlerts.shared.actionLabels()
        let allow = UNNotificationAction(identifier: "GATE_ALLOW", title: labels[0], options: [.authenticationRequired])
        let deny = UNNotificationAction(identifier: "GATE_DENY", title: labels[1], options: [.destructive, .authenticationRequired])
        let category = UNNotificationCategory(identifier: "GATE_VISITOR", actions: [allow, deny], intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    /// Silent pushes: "gate_cancel" removes a visitor alert someone in the flat already answered.
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        if let type = userInfo["type"] as? String, type == "gate_cancel", let id = userInfo["id"] as? String {
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [id])
            PushEvents.shared.onNotificationReceived(type: "gate")
            completionHandler(.newData)
        } else {
            completionHandler(.noData)
        }
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
        let userInfo = response.notification.request.content.userInfo
        let type = userInfo["type"] as? String
        let action = response.actionIdentifier
        if (action == "GATE_ALLOW" || action == "GATE_DENY"), let id = userInfo["id"] as? String {
            // Answer the gate without opening the app; iOS waits for completionHandler.
            GateAlerts.shared.respond(entryId: id, allow: action == "GATE_ALLOW") { _ in
                completionHandler()
            }
            return
        }
        PushEvents.shared.onNotificationOpened(type: type)
        completionHandler()
    }
}

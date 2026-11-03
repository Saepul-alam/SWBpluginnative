import Foundation
import Capacitor
import UIKit
import UserNotifications

@objc(SwbNotifPlugin)
public class SwbNotifPlugin: CAPPlugin, NotificationHandlerProtocol {

    private let center = UNUserNotificationCenter.current()
    private var defaults: UserDefaults {
        return UserDefaults.standard
    }

    override public func load() {
        bridge?.notificationRouter.localNotificationHandler = self
        bridge?.notificationRouter.pushNotificationHandler = self
        rebuildCategories()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handlePushToken(_:)),
            name: SwbNotif.pushTokenNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Permission

    @objc func checkPermission(_ call: CAPPluginCall) {
        center.getNotificationSettings { settings in
            let granted = self.isGranted(settings.authorizationStatus)
            call.resolve([
                "granted": granted,
                "display": self.displayStatus(settings.authorizationStatus)
            ])
        }
    }

    @objc func requestPermission(_ call: CAPPluginCall) {
        center.requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in
            self.center.getNotificationSettings { settings in
                call.resolve([
                    "granted": self.isGranted(settings.authorizationStatus),
                    "display": self.displayStatus(settings.authorizationStatus)
                ])
            }
        }
    }

    @objc func checkExactAlarmPermission(_ call: CAPPluginCall) {
        call.resolve(["granted": true])
    }

    @objc func requestExactAlarmPermission(_ call: CAPPluginCall) {
        call.resolve(["granted": true])
    }

    // MARK: - Channels (Android only)

    @objc func createChannel(_ call: CAPPluginCall) {
        call.resolve()
    }

    @objc func deleteChannel(_ call: CAPPluginCall) {
        call.resolve()
    }

    @objc func listChannels(_ call: CAPPluginCall) {
        call.resolve(["channels": []])
    }

    // MARK: - Scheduling

    @objc func schedule(_ call: CAPPluginCall) {
        let options: [String: Any] = SwbNotif.sanitize(SwbNotif.stringDictionary(call.options))
        let validation = SwbNotif.validate(options: options)
        guard validation.isValid else {
            call.reject(validation.error ?? "Invalid notification options", "INVALID_ARGUMENT")
            return
        }

        let id = (options["id"] as? NSNumber)?.intValue ?? SwbNotif.generateId()
        var mutableOptions = options
        mutableOptions["id"] = id

        let content = makeContent(options: mutableOptions)
        let trigger = makeTrigger(options: mutableOptions)

        let request = UNNotificationRequest(
            identifier: SwbNotif.identifier(for: id),
            content: content,
            trigger: trigger
        )

        center.add(request) { error in
            if let error = error {
                call.reject(error.localizedDescription, "UNKNOWN")
                return
            }
            call.resolve(["id": id])
        }
    }

    @objc func scheduleMultiple(_ call: CAPPluginCall) {
        let options: [String: Any] = SwbNotif.sanitize(SwbNotif.stringDictionary(call.options))
        guard let notifications = options["notifications"] as? [[String: Any]] else {
            call.reject("notifications array is required", "INVALID_ARGUMENT")
            return
        }
        if notifications.count > SwbNotif.maxScheduled {
            call.reject("Cannot schedule more than \(SwbNotif.maxScheduled) notifications at once", "STORAGE_LIMIT_EXCEEDED")
            return
        }

        var seenIds = Set<Int>()
        for raw in notifications {
            let item = SwbNotif.sanitize(raw)
            let validation = SwbNotif.validate(options: item)
            guard validation.isValid else {
                call.reject(validation.error ?? "Invalid notification options", "INVALID_ARGUMENT")
                return
            }
            if let idNum = item["id"] as? NSNumber {
                let idVal = idNum.intValue
                if seenIds.contains(idVal) {
                    call.reject("Duplicate notification id \(idVal) in schedule list", "INVALID_ARGUMENT")
                    return
                }
                seenIds.insert(idVal)
            }
        }

        var ids: [Int] = []
        let group = DispatchGroup()
        var failure: Error?

        for raw in notifications {
            let item = SwbNotif.sanitize(raw)
            let id = (item["id"] as? NSNumber)?.intValue ?? SwbNotif.generateId()
            var mutableItem = item
            mutableItem["id"] = id
            ids.append(id)

            let request = UNNotificationRequest(
                identifier: SwbNotif.identifier(for: id),
                content: makeContent(options: mutableItem),
                trigger: makeTrigger(options: mutableItem)
            )
            group.enter()
            center.add(request) { error in
                if let error = error {
                    failure = error
                }
                group.leave()
            }
        }

        group.notify(queue: .main) {
            if let failure = failure {
                call.reject(failure.localizedDescription, "UNKNOWN")
                return
            }
            call.resolve(["ids": ids])
        }
    }

    @objc func getPending(_ call: CAPPluginCall) {
        center.getPendingNotificationRequests { requests in
            let notifications = requests.map { SwbNotif.payload(from: $0, received: false) }
            call.resolve(["notifications": notifications])
        }
    }

    @objc func cancel(_ call: CAPPluginCall) {
        guard let id = call.getInt("id") else {
            call.reject("A notification id is required", "INVALID_ARGUMENT")
            return
        }
        let identifier = SwbNotif.identifier(for: id)
        center.removePendingNotificationRequests(withIdentifiers: [identifier])
        center.removeDeliveredNotifications(withIdentifiers: [identifier])
        call.resolve()
    }

    @objc func cancelAll(_ call: CAPPluginCall) {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
        call.resolve()
    }

    // MARK: - Delivered

    @objc func getDelivered(_ call: CAPPluginCall) {
        center.getDeliveredNotifications { notifications in
            let payloads = notifications.map { notification -> [String: Any] in
                var payload = SwbNotif.payload(from: notification.request, received: true)
                payload["receivedAt"] = Int(notification.date.timeIntervalSince1970 * 1000)
                return payload
            }
            call.resolve(["notifications": payloads])
        }
    }

    @objc func removeDelivered(_ call: CAPPluginCall) {
        let options: [String: Any] = SwbNotif.sanitize(SwbNotif.stringDictionary(call.options))
        guard let ids = options["ids"] as? [NSNumber] else {
            call.reject("ids array is required", "INVALID_ARGUMENT")
            return
        }
        let identifiers = ids.map { SwbNotif.identifier(for: $0.intValue) }
        center.removeDeliveredNotifications(withIdentifiers: identifiers)
        call.resolve()
    }

    @objc func removeAllDelivered(_ call: CAPPluginCall) {
        center.removeAllDeliveredNotifications()
        call.resolve()
    }

    // MARK: - Badge

    @objc func setBadgeCount(_ call: CAPPluginCall) {
        let count = call.getInt("count") ?? 0
        if count < 0 {
            call.reject("count must be a non-negative number", "INVALID_ARGUMENT")
            return
        }
        defaults.set(count, forKey: SwbNotif.badgeKey)
        DispatchQueue.main.async {
            UIApplication.shared.applicationIconBadgeNumber = count
        }
        call.resolve()
    }

    @objc func getBadgeCount(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            call.resolve(["count": UIApplication.shared.applicationIconBadgeNumber])
        }
    }

    // MARK: - Action groups

    @objc func createActionGroup(_ call: CAPPluginCall) {
        let options: [String: Any] = SwbNotif.sanitize(SwbNotif.stringDictionary(call.options))
        guard let id = options["id"] as? String, !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let actions = options["actions"] as? [[String: Any]] else {
            call.reject("An id and actions are required", "INVALID_ARGUMENT")
            return
        }
        if actions.count > SwbNotif.maxActionsPerGroup {
            call.reject("An action group cannot have more than \(SwbNotif.maxActionsPerGroup) actions", "INVALID_ARGUMENT")
            return
        }
        var groups = storedActionGroups()
        groups[id] = actions
        defaults.set(groups, forKey: SwbNotif.actionGroupsKey)
        rebuildCategories()
        call.resolve()
    }

    @objc func deleteActionGroup(_ call: CAPPluginCall) {
        guard let id = call.getString("id"), !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            call.reject("An action group id is required", "INVALID_ARGUMENT")
            return
        }
        var groups = storedActionGroups()
        groups.removeValue(forKey: id)
        defaults.set(groups, forKey: SwbNotif.actionGroupsKey)
        rebuildCategories()
        call.resolve()
    }

    // MARK: - Push

    @objc func registerPush(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            UIApplication.shared.registerForRemoteNotifications()
        }
        let token = defaults.string(forKey: SwbNotif.pushTokenKey)
        call.resolve(["token": token ?? NSNull()])
    }

    @objc func getPushToken(_ call: CAPPluginCall) {
        let token = defaults.string(forKey: SwbNotif.pushTokenKey)
        call.resolve(["token": token ?? NSNull()])
    }

    @objc func unregisterPush(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            UIApplication.shared.unregisterForRemoteNotifications()
        }
        defaults.removeObject(forKey: SwbNotif.pushTokenKey)
        call.resolve()
    }

    @objc func getLastAction(_ call: CAPPluginCall) {
        guard let action = defaults.dictionary(forKey: SwbNotif.lastActionKey) else {
            call.resolve()
            return
        }
        defaults.removeObject(forKey: SwbNotif.lastActionKey)
        call.resolve(action)
    }

    @objc func clearLastAction(_ call: CAPPluginCall) {
        defaults.removeObject(forKey: SwbNotif.lastActionKey)
        call.resolve()
    }

    // MARK: - CAPNotificationHandlerProtocol

    public func willPresent(notification: UNNotification) -> UNNotificationPresentationOptions {
        let payload = SwbNotif.payload(from: notification.request, received: true)
        notifyListeners("notificationReceived", data: payload)
        return [.alert, .badge, .sound]
    }

    public func didReceive(response: UNNotificationResponse) {
        if response.actionIdentifier == UNNotificationDismissActionIdentifier {
            let id = SwbNotif.id(from: response.notification.request.identifier)
            notifyListeners("notificationDismissed", data: ["id": id])
            return
        }

        let payload = SwbNotif.payload(from: response.notification.request, received: true)
        var actionId = response.actionIdentifier
        if actionId == UNNotificationDefaultActionIdentifier {
            actionId = "tap"
        }
        var event: [String: Any] = [
            "actionId": actionId,
            "notification": payload
        ]
        if let textResponse = response as? UNTextInputNotificationResponse {
            event["inputValue"] = textResponse.userText
        }
        defaults.set(event, forKey: SwbNotif.lastActionKey)
        notifyListeners("notificationActionPerformed", data: event)
    }

    // MARK: - Helpers

    private func isGranted(_ status: UNAuthorizationStatus) -> Bool {
        if status == .authorized || status == .provisional {
            return true
        }
        if #available(iOS 14.0, *), status == .ephemeral {
            return true
        }
        return false
    }

    private func displayStatus(_ status: UNAuthorizationStatus) -> String {
        switch status {
        case .authorized, .provisional:
            return "granted"
        case .denied:
            return "denied"
        case .notDetermined:
            return "prompt"
        default:
            if #available(iOS 14.0, *), status == .ephemeral {
                return "granted"
            }
            return "denied"
        }
    }

    private func makeContent(options: [String: Any]) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = options["title"] as? String ?? ""
        content.body = options["body"] as? String ?? ""
        if let summary = options["summaryText"] as? String {
            content.subtitle = summary
        }
        if let badge = options["badge"] as? NSNumber {
            content.badge = badge
        }
        if let groupId = options["actionGroupId"] as? String {
            content.categoryIdentifier = groupId
        }
        if let sound = options["sound"] as? String {
            if sound == "none" {
                content.sound = nil
            } else {
                content.sound = UNNotificationSound(named: UNNotificationSoundName(sound))
            }
        } else {
            content.sound = .default
        }
        content.userInfo = [SwbNotif.userInfoKey: options]
        return content
    }

    private func makeTrigger(options: [String: Any]) -> UNNotificationTrigger? {
        if let every = options["every"] as? String, let interval = SwbNotif.interval(for: every) {
            return UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: true)
        }
        guard let date = SwbNotif.date(from: options["scheduleAt"]) else {
            return nil
        }
        if date <= Date() {
            return nil
        }
        let components = Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute, .second],
            from: date
        )
        return UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
    }

    private func storedActionGroups() -> [String: [[String: Any]]] {
        return defaults.dictionary(forKey: SwbNotif.actionGroupsKey) as? [String: [[String: Any]]] ?? [:]
    }

    private func rebuildCategories() {
        let groups = storedActionGroups()
        var categories: [UNNotificationCategory] = []
        for (identifier, actions) in groups {
            categories.append(makeCategory(identifier: identifier, actions: actions))
        }
        center.setNotificationCategories(Set(categories))
    }

    private func makeCategory(identifier: String, actions: [[String: Any]]) -> UNNotificationCategory {
        var notificationActions: [UNNotificationAction] = []
        let maxCount = min(actions.count, SwbNotif.maxActionsPerGroup)
        for i in 0..<maxCount {
            let action = actions[i]
            guard let actionId = action["id"] as? String else {
                continue
            }
            let title = action["title"] as? String ?? actionId
            var options: UNNotificationActionOptions = [.foreground]
            if action["destructive"] as? Bool == true {
                options.insert(.destructive)
            }
            if action["input"] as? Bool == true {
                notificationActions.append(
                    UNTextInputNotificationAction(
                        identifier: actionId,
                        title: title,
                        options: options,
                        textInputButtonTitle: "Send",
                        textInputPlaceholder: action["inputPlaceholder"] as? String ?? "Reply"
                    )
                )
            } else {
                notificationActions.append(
                    UNNotificationAction(identifier: actionId, title: title, options: options)
                )
            }
        }
        return UNNotificationCategory(
            identifier: identifier,
            actions: notificationActions,
            intentIdentifiers: [],
            options: []
        )
    }

    @objc private func handlePushToken(_ notification: Notification) {
        let token = (notification.object as? String) ?? defaults.string(forKey: SwbNotif.pushTokenKey)
        guard let token = token else {
            return
        }
        defaults.set(token, forKey: SwbNotif.pushTokenKey)
        notifyListeners("pushTokenReceived", data: ["token": token])
    }
}

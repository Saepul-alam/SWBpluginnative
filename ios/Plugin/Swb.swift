import Foundation
import UserNotifications

@objc public class SwbNotif: NSObject {

    static let identifierPrefix = "swbnotif-"
    static let userInfoKey = "swbnotif"
    static let lastActionKey = "swbnotif.last_action"
    static let badgeKey = "swbnotif.badge"
    static let actionGroupsKey = "swbnotif.action_groups"

    public static let maxTitleLength = 256
    public static let maxBodyLength = 4096
    public static let maxExtraBytes = 32768
    public static let maxScheduled = 100
    public static let maxDelivered = 100
    public static let maxActionsPerGroup = 4

    @objc public static func generateId() -> Int {
        return Int(Date().timeIntervalSince1970 * 1000)
    }

    static func identifier(for id: Int) -> String {
        return "\(identifierPrefix)\(id)"
    }

    static func id(from identifier: String) -> Int {
        if identifier.hasPrefix(identifierPrefix) {
            return Int(identifier.dropFirst(identifierPrefix.count)) ?? 0
        }
        return Int(identifier) ?? 0
    }

    static func sanitize(_ value: Any) -> Any? {
        if value is NSNull {
            return nil
        }
        if let dictionary = value as? [String: Any] {
            return sanitize(dictionary)
        }
        if let array = value as? [Any] {
            return array.compactMap { sanitize($0) }
        }
        if value is String || value is NSNumber || value is Date {
            return value
        }
        return String(describing: value)
    }

    static func sanitize(_ dictionary: [String: Any]) -> [String: Any] {
        var result: [String: Any] = [:]
        for (key, value) in dictionary {
            if let sanitized = sanitize(value) {
                result[key] = sanitized
            }
        }
        return result
    }

    static func stringDictionary(_ dictionary: [AnyHashable: Any]?) -> [String: Any] {
        guard let dictionary = dictionary else {
            return [:]
        }
        var result: [String: Any] = [:]
        for (key, value) in dictionary {
            if let key = key as? String {
                result[key] = value
            }
        }
        return result
    }

    static func validate(options: [String: Any]) -> (isValid: Bool, error: String?) {
        guard let title = options["title"] as? String, !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return (false, "title is required and cannot be empty")
        }
        if title.count > maxTitleLength {
            return (false, "title exceeds maximum length of \(maxTitleLength) characters")
        }
        if let body = options["body"] as? String, body.count > maxBodyLength {
            return (false, "body exceeds maximum length of \(maxBodyLength) characters")
        }
        if let extra = options["extra"] as? [String: Any] {
            if let data = try? JSONSerialization.data(withJSONObject: extra, options: []) {
                if data.count > maxExtraBytes {
                    return (false, "extra payload exceeds maximum size of \(maxExtraBytes) bytes")
                }
            }
        }
        if let count = options["count"] as? NSNumber, count.intValue < 1 {
            return (false, "count must be a positive integer")
        }
        return (true, nil)
    }

    static func date(from value: Any?) -> Date? {
        if let date = value as? Date {
            return date
        }
        if let number = value as? NSNumber {
            return Date(timeIntervalSince1970: number.doubleValue / 1000.0)
        }
        if let string = value as? String {
            if let milliseconds = Double(string) {
                return Date(timeIntervalSince1970: milliseconds / 1000.0)
            }
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = formatter.date(from: string) {
                return date
            }
            formatter.formatOptions = [.withInternetDateTime]
            return formatter.date(from: string)
        }
        return nil
    }

    static func interval(for every: String) -> TimeInterval? {
        switch every {
        case "minute":
            return 60
        case "hour":
            return 60 * 60
        case "day":
            return 24 * 60 * 60
        case "week":
            return 7 * 24 * 60 * 60
        default:
            return nil
        }
    }

    static func value(from options: [String: Any], key: String) -> Any? {
        guard let value = options[key], !(value is NSNull) else {
            return nil
        }
        return value
    }

    static func string(from options: [String: Any], key: String) -> String? {
        return value(from: options, key: key) as? String
    }

    static func payload(from request: UNNotificationRequest, received: Bool) -> [String: Any] {
        let content = request.content
        var payload: [String: Any] = [:]
        if let stored = content.userInfo[userInfoKey] as? [String: Any] {
            payload = stored
        }
        payload["id"] = id(from: request.identifier)
        payload["title"] = content.title
        payload["body"] = content.body
        if !content.subtitle.isEmpty {
            payload["summaryText"] = content.subtitle
        }
        if let badge = content.badge {
            payload["badge"] = badge.intValue
        }
        if let trigger = request.trigger as? UNTimeIntervalNotificationTrigger, let date = trigger.nextTriggerDate() {
            payload["scheduleAt"] = Int(date.timeIntervalSince1970 * 1000)
        } else if let trigger = request.trigger as? UNCalendarNotificationTrigger, let date = trigger.nextTriggerDate() {
            payload["scheduleAt"] = Int(date.timeIntervalSince1970 * 1000)
        }
        if received {
            payload["receivedAt"] = Int(Date().timeIntervalSince1970 * 1000)
        }
        return payload
    }
}

// SPDX-License-Identifier: MIT
import Foundation

struct InstanceConfig: Equatable, Sendable {
    var instanceUrl: String
    var instanceName: String

    var baseUrl: String { instanceUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) }

    var reviewUrl: String { "\(baseUrl)/scout/review" }
}

enum InstanceConfigLoader {
    static func load() -> InstanceConfig? {
        guard let url = Bundle.main.url(forResource: "instance", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let instanceUrl = root["instance_url"] as? String,
              let instanceName = root["instance_name"] as? String
        else { return nil }
        return InstanceConfig(instanceUrl: instanceUrl, instanceName: instanceName)
    }
}

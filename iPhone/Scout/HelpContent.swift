// SPDX-License-Identifier: MIT
import Foundation

struct HelpPage: Equatable, Sendable {
    var title: String
    var sections: [HelpSection]
}

struct HelpSection: Equatable, Sendable {
    var heading: String
    var body: [String]
    var links: [HelpLink] = []
    var image: String?
}

struct HelpLink: Equatable, Sendable {
    var label: String
    var url: String
}

enum HelpContent {
    private static let sourceCodeHeading = "Source code"

    static func load() -> HelpPage? {
        guard var help = loadAsset("help").flatMap(parse) else { return nil }
        let legal = loadLegalSections()
        if !legal.isEmpty {
            help = mergeLegal(help, legal)
        }
        return help
    }

    static func loadLegalSections() -> [HelpSection] {
        loadAsset("legal").flatMap(parseLegalSections) ?? []
    }

    private static func loadAsset(_ name: String) -> [String: Any]? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }
        return root
    }

    static func parse(_ root: [String: Any]) -> HelpPage? {
        guard let title = root["title"] as? String,
              let sections = root["sections"] as? [[String: Any]]
        else { return nil }
        return HelpPage(title: title, sections: parseSections(sections))
    }

    static func parseLegalSections(_ root: [String: Any]) -> [HelpSection] {
        guard let sections = root["sections"] as? [[String: Any]] else { return [] }
        return parseSections(sections)
    }

    static func mergeLegal(_ help: HelpPage, _ legal: [HelpSection]) -> HelpPage {
        var out = help.sections
        if let idx = out.firstIndex(where: { $0.heading == sourceCodeHeading }) {
            out.insert(contentsOf: legal, at: idx)
        } else {
            out.append(contentsOf: legal)
        }
        return HelpPage(title: help.title, sections: out)
    }

    private static func parseSections(_ array: [[String: Any]]) -> [HelpSection] {
        array.compactMap { section in
            guard let heading = section["heading"] as? String,
                  let body = section["body"] as? [String]
            else { return nil }
            let links = (section["links"] as? [[String: Any]])?.compactMap { link -> HelpLink? in
                guard let label = link["label"] as? String, let url = link["url"] as? String else { return nil }
                return HelpLink(label: label, url: url)
            } ?? []
            return HelpSection(
                heading: heading,
                body: body,
                links: links,
                image: section["image"] as? String
            )
        }
    }
}
